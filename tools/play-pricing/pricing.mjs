#!/usr/bin/env node
// Lucento's regional prices on Google Play: plan, apply, verify, snapshot (docs/monetization/REGIONAL_PRICING.md).
//
//   LUCENTO_PLAY_KEY=<path to a service-account JSON key> node tools/play-pricing/pricing.mjs <command>
//
//   snapshot                 Save the prices Play has now to out/snapshot-<time>.json (the format apply reads).
//   plan                     Read-only. Snapshot, convert each tier's base price with Play's own conversion and write
//                            out/plan.json + out/plan.md (now -> new for every region). Changes nothing.
//   apply --plan <file> --yes  Write the plan's prices to Play (a snapshot is saved first, so a snapshot file is
//                            also a rollback: apply it the same way).
//   verify --plan <file>     Read Play again and list every price that differs from the plan.
//
// The key needs Play Console's "Manage store presence" permission for convert and apply; read-only listing works with
// "View app information". The key is read from disk and never printed. Prices are exact decimals (units + nanos),
// never floating point. Existing subscribers keep the price they joined at: Play applies base-plan prices to new
// purchases only, and this tool never migrates price cohorts.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(here, 'out');
const config = JSON.parse(fs.readFileSync(path.join(here, 'tiers.json'), 'utf8'));
const SLOTS = Object.keys(config.slots);
const API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${config.packageName}`;

// ---------- money (exact) ----------
function moneyFromDecimal(currencyCode, decimal) {
  if (!/^\d+(\.\d{1,9})?$/.test(decimal)) throw new Error(`Not a plain decimal price: ${decimal}`);
  const [u, f = ''] = decimal.split('.');
  return { currencyCode, units: String(BigInt(u)), nanos: Number(f.padEnd(9, '0')) };
}
const nanosOf = m => BigInt(m.units ?? '0') * 1_000_000_000n + BigInt(m.nanos ?? 0);
const sameMoney = (a, b) => a.currencyCode === b.currencyCode && nanosOf(a) === nanosOf(b);
function fmt(m) {
  if (!m) return '—';
  const n = nanosOf(m); const whole = n / 1_000_000_000n; let frac = String(n % 1_000_000_000n).padStart(9, '0').replace(/0+$/, '');
  if (frac.length === 1) frac += '0';
  const grouped = whole.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${grouped}${frac ? '.' + frac : ''}`;
}
const clean = m => ({ currencyCode: m.currencyCode, units: String(m.units ?? '0'), ...(m.nanos ? { nanos: m.nanos } : {}) });

// ---------- auth + http ----------
async function accessToken() {
  const file = process.env.LUCENTO_PLAY_KEY;
  if (!file) throw new Error('Set LUCENTO_PLAY_KEY to the service-account key file.');
  const key = JSON.parse(fs.readFileSync(file, 'utf8'));
  const b64 = o => Buffer.from(JSON.stringify(o)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const unsigned = `${b64({ alg: 'RS256', typ: 'JWT' })}.${b64({ iss: key.client_email, scope: 'https://www.googleapis.com/auth/androidpublisher', aud: key.token_uri, iat: now, exp: now + 3600 })}`;
  const sig = crypto.sign('RSA-SHA256', Buffer.from(unsigned), key.private_key).toString('base64url');
  const r = await fetch(key.token_uri, { method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${sig}` }) });
  const j = await r.json();
  if (!j.access_token) throw new Error(`Google sign-in failed (${r.status} ${j.error ?? ''})`);
  return j.access_token;
}
let token;
async function api(method, p, body) {
  token ??= await accessToken();
  const r = await fetch(API + p, { method, headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const text = await r.text();
  if (!r.ok) {
    let msg = text; try { msg = JSON.parse(text).error?.message ?? text; } catch { /* not JSON */ }
    throw new Error(`${method} ${p} -> ${r.status}: ${msg.slice(0, 400)}`);
  }
  return text ? JSON.parse(text) : {};
}

// ---------- reading Play ----------
async function readProducts() {
  const subs = {}; const otps = {};
  for (const s of (await api('GET', '/subscriptions')).subscriptions ?? []) subs[s.productId] = s;
  for (const o of (await api('GET', '/oneTimeProducts')).oneTimeProducts ?? []) otps[o.productId] = o;
  return { subs, otps };
}
function regionalOf(products, slot) {
  const s = config.slots[slot];
  if (s.subscription) {
    const bp = products.subs[s.subscription]?.basePlans?.find(b => b.basePlanId === s.basePlan);
    if (!bp) throw new Error(`Play has no base plan ${s.subscription}/${s.basePlan}`);
    return bp.regionalConfigs;
  }
  const po = products.otps[s.oneTimeProduct]?.purchaseOptions?.find(o => o.purchaseOptionId === s.purchaseOption);
  if (!po) throw new Error(`Play has no purchase option ${s.oneTimeProduct}/${s.purchaseOption}`);
  return po.regionalPricingAndAvailabilityConfigs;
}
function pricesOf(products) {
  const out = {};
  for (const slot of SLOTS) out[slot] = Object.fromEntries(regionalOf(products, slot).filter(r => r.price).map(r => [r.regionCode, clean(r.price)]));
  return out;
}
function stamp() { return new Date().toISOString().replace(/[:.]/g, '-'); }
function save(name, data) { fs.mkdirSync(outDir, { recursive: true }); const f = path.join(outDir, name); fs.writeFileSync(f, typeof data === 'string' ? data : JSON.stringify(data, null, 2) + '\n'); return f; }
async function snapshot(products) {
  products ??= await readProducts();
  const f = save(`snapshot-${stamp()}.json`, { kind: 'snapshot', takenAt: new Date().toISOString(), regionsVersion: config.regionsVersion, prices: pricesOf(products) });
  console.log(`Saved the current prices to ${path.relative(process.cwd(), f)}`);
  return { products, file: f };
}

// ---------- planning ----------
async function plan() {
  const { products } = await snapshot();
  const now = pricesOf(products);
  const regions = Object.keys(now[SLOTS[0]]).sort();
  const missing = regions.filter(r => !(r in config.regions));
  if (missing.length) throw new Error(`Play sells in regions without a tier in tiers.json: ${missing.join(', ')}`);
  const extra = Object.keys(config.regions).filter(r => !regions.includes(r));
  if (extra.length) console.log(`Note: tiers.json lists regions Play doesn't offer: ${extra.join(', ')}`);

  const conversions = {}; // `${currency} ${decimal}` -> convertedRegionPrices
  const convert = async (currency, decimal) => {
    const k = `${currency} ${decimal}`;
    if (!conversions[k]) {
      const r = await api('POST', '/pricing:convertRegionPrices', { price: moneyFromDecimal(currency, decimal) });
      conversions[k] = r.convertedRegionPrices ?? {};
    }
    return conversions[k];
  };
  const next = {};
  for (const slot of SLOTS) {
    next[slot] = {};
    for (const region of regions) {
      const pin = config.pinned?.[region];
      if (pin) { next[slot][region] = moneyFromDecimal(pin.currency, pin.prices[slot]); continue; }
      const tier = config.tiers[String(config.regions[region])];
      const conv = (await convert(tier.currency, tier.prices[slot]))[region];
      if (!conv?.price) throw new Error(`Play gave no ${slot} price for ${region} from ${tier.currency} ${tier.prices[slot]}`);
      next[slot][region] = clean(conv.price);
    }
  }
  // One price per tier in a currency shared by several countries (owner decision 2026-11-21: euro countries pay the
  // same euro price in a tier instead of Play's VAT-adjusted €3.59–€4.59).
  for (const region of regions) {
    if (config.pinned?.[region]) continue;
    const fixed = config.currencyPrices?.[now[SLOTS[0]][region].currencyCode]?.[String(config.regions[region])];
    if (!fixed) continue;
    for (const slot of SLOTS) next[slot][region] = clean(moneyFromDecimal(now[slot][region].currencyCode, fixed[slot]));
  }
  // Hand-set prices where Play's rounding gives a poor result (e.g. a year that costs more than twelve months).
  // Written in the region's own currency, exactly as Play should store it.
  for (const [region, prices] of Object.entries(config.overrides ?? {})) {
    if (region.startsWith('_')) continue;
    for (const [slot, decimal] of Object.entries(prices)) {
      if (slot.startsWith('_')) continue;
      if (!next[slot]?.[region]) throw new Error(`overrides: no ${slot} price for ${region}`);
      next[slot][region] = clean(moneyFromDecimal(now[slot][region].currencyCode, decimal));
    }
  }
  // Every new price must be in the currency Play uses for that region now.
  for (const slot of SLOTS) for (const region of regions) {
    if (next[slot][region].currencyCode !== now[slot][region].currencyCode) {
      throw new Error(`${region} ${slot}: currency would change ${now[slot][region].currencyCode} -> ${next[slot][region].currencyCode}`);
    }
  }
  const planFile = save('plan.json', { kind: 'plan', madeAt: new Date().toISOString(), regionsVersion: config.regionsVersion, prices: next });
  save('plan.md', planTable(regions, now, next));
  console.log(`Wrote ${path.relative(process.cwd(), planFile)} and plan.md (${regions.length} regions, ${Object.keys(conversions).length} Play conversions). Nothing was changed in Play.`);
}

function savingPercent(monthly, yearly) { // as ProCopy.yearlySavingPercent: rounded down
  if (monthly.currencyCode !== yearly.currencyCode) return null;
  const twelve = nanosOf(monthly) * 12n; const y = nanosOf(yearly);
  return twelve > y ? Number(((twelve - y) * 100n) / twelve) : 0;
}
function planTable(regions, now, next) {
  const tierOf = r => config.regions[r];
  const cell = (slot, r) => {
    const a = now[slot][r], b = next[slot][r];
    if (sameMoney(a, b)) return fmt(b);
    const pct = Number((nanosOf(b) - nanosOf(a)) * 1000n / nanosOf(a)) / 10;
    return `${fmt(a)} → **${fmt(b)}** (${pct > 0 ? '+' : ''}${pct.toFixed(0)} %)`;
  };
  const lines = [
    '| Region | Tier | Currency | Pro monthly | Pro yearly | Lifetime | Cloud monthly | Cloud yearly | Yearly saving |',
    '|---|---|---|---|---|---|---|---|---|',
  ];
  const order = regions.slice().sort((a, b) => tierOf(a) - tierOf(b) || a.localeCompare(b));
  for (const r of order) {
    const s = savingPercent(next['pro.monthly'][r], next['pro.annual'][r]);
    lines.push(`| ${r} | ${config.pinned?.[r] ? `${tierOf(r)} (pinned)` : config.overrides?.[r] ? `${tierOf(r)} (adjusted)` : config.currencyPrices?.[next['pro.monthly'][r].currencyCode]?.[String(tierOf(r))] ? `${tierOf(r)} (one ${next['pro.monthly'][r].currencyCode} price)` : tierOf(r)} | ${next['pro.monthly'][r].currencyCode} | ${SLOTS.map(sl => cell(sl, r)).join(' | ')} | ${s ?? '—'} % |`);
  }
  const changed = SLOTS.reduce((n, sl) => n + regions.filter(r => !sameMoney(now[sl][r], next[sl][r])).length, 0);
  return `# Planned Google Play prices\n\nMade ${new Date().toISOString()} by tools/play-pricing/pricing.mjs from tiers.json. ` +
    `${changed} of ${regions.length * SLOTS.length} prices change. Prices are what a buyer pays: Play adds local tax where prices include it ` +
    `(most of Europe, the UK, Australia, Japan, India, the Philippines…), and US prices exclude sales tax. "Yearly saving" is what the app's ` +
    `badge would show (yearly vs 12 × monthly, rounded down).\n\n${lines.join('\n')}\n`;
}

// ---------- applying ----------
function readPlan(file) {
  const p = JSON.parse(fs.readFileSync(file, 'utf8'));
  if (!p.prices || !SLOTS.every(s => p.prices[s])) throw new Error(`${file} is not a plan or snapshot from this tool`);
  return p;
}
async function apply(file) {
  const p = readPlan(file);
  const { products } = await snapshot(); // rollback point
  const q = `regionsVersion.version=${encodeURIComponent(p.regionsVersion ?? config.regionsVersion)}`;
  const bySub = {};
  for (const slot of SLOTS) { const s = config.slots[slot]; if (s.subscription) (bySub[s.subscription] ??= []).push(slot); }
  let changed = 0;
  for (const [productId, slots] of Object.entries(bySub)) {
    const sub = structuredClone(products.subs[productId]);
    let dirty = false;
    for (const slot of slots) {
      const bp = sub.basePlans.find(b => b.basePlanId === config.slots[slot].basePlan);
      for (const rc of bp.regionalConfigs) {
        const want = p.prices[slot][rc.regionCode];
        if (want && !sameMoney(rc.price, want)) { rc.price = want; dirty = true; changed++; }
      }
    }
    if (dirty) { await api('PATCH', `/subscriptions/${productId}?updateMask=basePlans&${q}`, sub); console.log(`Updated ${productId}`); }
  }
  for (const slot of SLOTS) {
    const s = config.slots[slot]; if (!s.oneTimeProduct) continue;
    const otp = structuredClone(products.otps[s.oneTimeProduct]);
    const po = otp.purchaseOptions.find(o => o.purchaseOptionId === s.purchaseOption);
    let dirty = false;
    for (const rc of po.regionalPricingAndAvailabilityConfigs) {
      const want = p.prices[slot][rc.regionCode];
      if (want && rc.price && !sameMoney(rc.price, want)) { rc.price = want; dirty = true; changed++; }
    }
    if (dirty) { // Play routes reads at /oneTimeProducts but the update at /onetimeproducts (monetization.onetimeproducts.patch).
      await api('PATCH', `/onetimeproducts/${s.oneTimeProduct}?updateMask=purchaseOptions&${q}`, otp); console.log(`Updated ${s.oneTimeProduct}`); }
  }
  console.log(`${changed} prices sent to Play. Run verify with the same file to check them.`);
}
async function verify(file) {
  const p = readPlan(file);
  const now = pricesOf(await readProducts());
  const diffs = [];
  for (const slot of SLOTS) for (const [region, want] of Object.entries(p.prices[slot])) {
    const have = now[slot][region];
    if (!have || !sameMoney(have, want)) diffs.push(`${slot} ${region}: Play has ${have ? `${have.currencyCode} ${fmt(have)}` : 'nothing'}, plan ${want.currencyCode} ${fmt(want)}`);
  }
  const total = SLOTS.reduce((n, s) => n + Object.keys(p.prices[s]).length, 0);
  console.log(diffs.length ? `${diffs.length} of ${total} prices differ:\n${diffs.join('\n')}` : `All ${total} prices match ${path.basename(file)}.`);
  process.exitCode = diffs.length ? 1 : 0;
}

// ---------- main ----------
const [cmd, ...args] = process.argv.slice(2);
const flag = n => { const i = args.indexOf(n); return i >= 0 ? args[i + 1] : undefined; };
try {
  if (cmd === 'snapshot') await snapshot();
  else if (cmd === 'plan') await plan();
  else if (cmd === 'apply') {
    const f = flag('--plan'); if (!f) throw new Error('apply needs --plan <file>');
    if (!args.includes('--yes')) throw new Error('apply changes live prices; add --yes once the plan has been approved');
    await apply(f);
  } else if (cmd === 'verify') { const f = flag('--plan'); if (!f) throw new Error('verify needs --plan <file>'); await verify(f); }
  else { console.log('Usage: node tools/play-pricing/pricing.mjs snapshot | plan | apply --plan <file> --yes | verify --plan <file>'); process.exitCode = 2; }
} catch (e) { console.error(e.message); process.exitCode = 1; }
