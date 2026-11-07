"""
Copies the in-app Terms of Use and Privacy Policy (app/.../feature/legal/LegalDocuments.kt) to the website, so the
two never say different things (docs/LEGAL.md "Keeping them true").

    python tools/legal/export_legal.py ../lucento-website/src/data/legal.ts

Writes a TypeScript module with VERSION, UPDATED, CONTACT_EMAIL, DELETE_ACCOUNT_URL, terms and privacy. Only the
Kotlin shapes LegalDocuments.kt uses are understood: string constants, Document(title, intro, sections) and
Section("title", listOf("...", ...)) with "..." + "..." concatenation and $CONSTANT templates.
"""
import io
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "..", "..", "app", "src", "main", "java", "com", "lucento", "app", "feature", "legal",
                      "LegalDocuments.kt")


def main(out_path):
    src = io.open(SOURCE, encoding="utf-8").read()
    consts = dict(re.findall(r'const val (\w+) = "([^"]*)"', src))
    # Join "a" + "b" across lines into one literal, then expand $CONSTANT templates.
    joined = re.sub(r'"\s*\+\s*\n\s*"', "", src)

    def expand(text):
        return re.sub(r"\$(\w+)", lambda m: consts[m.group(1)], text)

    def strings(block):
        return [expand(s) for s in re.findall(r'"((?:[^"\\]|\\.)*)"', block)]

    def document(name):
        start = joined.index("val %s = Document(" % name)
        end = joined.find("\n    val ", start + 1)
        body = joined[start:end if end != -1 else len(joined)]
        title = re.search(r'title = "([^"]*)"', body).group(1)
        intro = expand(re.search(r'intro = "((?:[^"\\]|\\.)*)"', body).group(1))
        sections = []
        for m in re.finditer(r'Section\(\s*"([^"]*)",\s*listOf\((.*?)\n\s*\),\s*\n\s*\)', body, re.S):
            sections.append({"title": m.group(1), "items": strings(m.group(2))})
        if not sections:
            raise SystemExit("no sections found in %s" % name)
        return {"title": title, "intro": intro, "sections": sections}

    terms = document("terms")
    privacy = document("privacy")
    out = [
        "// Generated from lucento-android app/src/main/java/com/lucento/app/feature/legal/LegalDocuments.kt by",
        "// tools/legal/export_legal.py. Edit the Kotlin file and regenerate; don't edit this one by hand.",
        "",
        "export interface LegalSection {",
        "  title: string;",
        "  items: string[];",
        "}",
        "",
        "export interface LegalDocument {",
        "  title: string;",
        "  intro: string;",
        "  sections: LegalSection[];",
        "}",
        "",
        "export const VERSION = %s;" % json.dumps(consts["VERSION"]),
        "export const UPDATED = %s;" % json.dumps(consts["UPDATED"]),
        "export const CONTACT_EMAIL = %s;" % json.dumps(consts["CONTACT_EMAIL"]),
        "export const DELETE_ACCOUNT_URL = %s;" % json.dumps(consts["DELETE_ACCOUNT_URL"]),
        "",
        "export const terms: LegalDocument = %s;" % json.dumps(terms, ensure_ascii=False, indent=2),
        "",
        "export const privacy: LegalDocument = %s;" % json.dumps(privacy, ensure_ascii=False, indent=2),
        "",
    ]
    io.open(out_path, "w", encoding="utf-8", newline="\n").write("\n".join(out))
    print("wrote %s: terms %d sections, privacy %d sections" % (out_path, len(terms["sections"]), len(privacy["sections"])))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(sys.argv[1])
