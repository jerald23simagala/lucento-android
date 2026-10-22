#!/usr/bin/env python3
"""Offline cross-check of Room migrations against the exported schemas (a complement to, not a replacement for,
the instrumented MigrationTest).

For each step N -> N+1 it builds database N from N.json, runs the SQL in Migrations.kt for that step, builds a
fresh database from (N+1).json, and compares every table's columns (name, type, not-null, pk), indices
(name, columns, uniqueness) and foreign keys (table, columns, on-delete/on-update) — the same properties Room's
TableInfo validation checks. Seed inserts in the migration are executed too (they must not fail).

usage: python3 tools/check_migrations.py
"""
import json, re, sqlite3, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/com.lucento.app.data.local.LucentoDatabase"
MIGRATIONS = ROOT / "app/src/main/java/com/lucento/app/data/local/Migrations.kt"
CATEGORIES = ROOT / "app/src/main/java/com/lucento/app/domain/Category.kt"


def build(version):
    d = json.loads((SCHEMAS / f"{version}.json").read_text())["database"]
    db = sqlite3.connect(":memory:")
    for e in d["entities"]:
        db.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for i in e.get("indices", []):
            db.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    return db, d


def describe(db):
    out = {}
    tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table'")]
    for t in sorted(tables):
        cols = sorted((r[1], r[2].upper(), r[3], r[5]) for r in db.execute(f"PRAGMA table_info(`{t}`)"))
        idx = []
        for r in db.execute(f"PRAGMA index_list(`{t}`)"):
            if r[1].startswith("sqlite_autoindex"):
                continue
            idx.append((r[1], bool(r[2]), tuple(c[2] for c in db.execute(f"PRAGMA index_info(`{r[1]}`)"))))
        fks = sorted((r[2], r[3], r[4], r[5], r[6]) for r in db.execute(f"PRAGMA foreign_key_list(`{t}`)"))
        out[t] = {"columns": cols, "indices": sorted(idx), "foreign_keys": fks}
    return out


def migration_sql(src, frm, to):
    block = re.search(r"Migration\(%d, %d\)\s*\{(.*?)\n    \}\n" % (frm, to), src, re.S).group(1)
    stmts = []
    for call in re.finditer(r"db\.execSQL\((.*?)\)\n", block, re.S):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', call.group(1))
        stmts.append("".join(parts).replace('\\"', '"'))
    seeds = "CategorySeed.insertDefaults" in block
    return stmts, seeds


def seed(db):
    seeds = re.findall(r'Seed\("([^"]+)", CategoryKind\.(\w+), "([^"]+)"\)', CATEGORIES.read_text())
    order = {}
    for name, kind, icon in seeds:
        n = order.get(kind, 0); order[kind] = n + 1
        db.execute("INSERT INTO categories (name, kind, icon_key, sort_order, archived, created_at) VALUES (?,?,?,?,0,0)", (name, kind, icon, n))
    return len(seeds)


def main():
    src = MIGRATIONS.read_text()
    versions = sorted(int(p.stem) for p in SCHEMAS.glob("*.json"))
    ok = True
    for v in versions[:-1]:
        db, _ = build(v)
        stmts, seeds = migration_sql(src, v, v + 1)
        for s in stmts:
            db.execute(s)
        if seeds:
            print(f"  {v}->{v+1}: seeded {seed(db)} default categories")
        expected, _ = build(v + 1)
        a, b = describe(db), describe(expected)
        if a == b:
            print(f"OK  {v} -> {v+1}: {len(b)} tables match {v+1}.json exactly")
        else:
            ok = False
            print(f"FAIL {v} -> {v+1}")
            for t in sorted(set(a) | set(b)):
                if a.get(t) != b.get(t):
                    print("  table", t, "\n    migrated:", a.get(t), "\n    expected:", b.get(t))
    sys.exit(0 if ok else 1)


main()
