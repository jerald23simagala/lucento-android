// Renders every <section class="asset"> in assets.html to ../<id>-<w>x<h>.png at its exact pixel size.
// Usage (from this folder):  NODE_PATH=<path to a node_modules with playwright> node render.cjs [id ...]
// Uses the installed Microsoft Edge (channel "msedge"); Playwright's bundled Chromium isn't needed.
// A tiny local server is used because headless Edge can stall on file:// pages here.
const fs = require("fs");
const http = require("http");
const path = require("path");
const { chromium } = require("playwright");

const TYPES = { ".html": "text/html", ".svg": "image/svg+xml", ".webp": "image/webp", ".woff2": "font/woff2", ".png": "image/png" };

(async () => {
  const only = process.argv.slice(2);
  const server = http.createServer((req, res) => {
    const file = path.join(__dirname, decodeURIComponent(new URL(req.url, "http://x").pathname));
    if (!file.startsWith(__dirname) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { res.writeHead(404); return res.end(); }
    res.writeHead(200, { "Content-Type": TYPES[path.extname(file)] || "application/octet-stream" });
    fs.createReadStream(file).pipe(res);
  }).listen(0, "127.0.0.1");
  await new Promise((r) => server.once("listening", r));
  const browser = await chromium.launch({ channel: process.env.PW_CHANNEL || "msedge" });
  const page = await browser.newPage({ viewport: { width: 1800, height: 1000 }, deviceScaleFactor: 1 });
  // The "load" event sometimes never fires in headless Edge on this PC, so wait for what the render needs instead:
  // the DOM, every <img>, the CSS backgrounds, and the fonts.
  await page.goto(`http://127.0.0.1:${server.address().port}/assets.html`, { waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => [...document.images].every((i) => i.complete && i.naturalWidth > 0), null, { timeout: 30000 });
  await page.evaluate(async () => {
    const urls = new Set();
    for (const el of document.querySelectorAll("*")) {
      for (const pseudo of [null, "::before"]) {
        const m = getComputedStyle(el, pseudo).backgroundImage.match(/url\("?(.*?)"?\)/);
        if (m) urls.add(m[1]);
      }
    }
    await Promise.all([...urls].map((u) => new Promise((r) => { const i = new Image(); i.onload = i.onerror = r; i.src = u; })));
    await document.fonts.ready;
  });
  await page.waitForTimeout(300);
  const ids = await page.$$eval("section.asset", (s) => s.map((e) => e.id));
  for (const id of ids) {
    if (only.length && !only.includes(id)) continue;
    const el = await page.$(`#${id}`);
    const box = await el.boundingBox();
    const out = path.join(__dirname, "..", `${id}-${Math.round(box.width)}x${Math.round(box.height)}.png`);
    await el.screenshot({ path: out });
    console.log("wrote", path.relative(path.join(__dirname, ".."), out));
  }
  await browser.close();
  server.close();
})();
