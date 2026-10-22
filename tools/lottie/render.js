// Renders frames of Lottie files with lottie-web (SVG) into one contact-sheet PNG for visual review.
// usage: node render.js out.png bg size frames json1 [json2 ...]
const { chromium } = require('playwright-core');
const fs = require('fs');
(async () => {
  const [out, bg, size, framesArg, ...files] = process.argv.slice(2);
  const frames = framesArg.split(',').map(Number);
  const lottieSrc = fs.readFileSync(require.resolve('lottie-web/build/player/lottie.min.js'), 'utf8');
  const data = files.map(f => JSON.parse(fs.readFileSync(f, 'utf8')));
  const html = `<html><body style="margin:0;background:${bg};font:11px sans-serif;color:#888">
    <div id="g" style="display:grid;grid-template-columns:repeat(${frames.length}, ${size}px);gap:6px;padding:6px"></div>
    <script>${lottieSrc}</script></body></html>`;
  const exe = ['/opt/pw-browsers/chromium-1194/chrome-linux/chrome', '/opt/pw-browsers/chromium'].find(p => fs.existsSync(p) && fs.statSync(p).isFile());
  const browser = await chromium.launch(exe ? { executablePath: exe } : {});
  const width = frames.length * (+size + 6) + 6;
  const page = await browser.newPage({ viewport: { width, height: 200 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));
  page.on('console', m => { if (m.type() === 'error' || m.type() === 'warning') errors.push(m.text()); });
  await page.setContent(html);
  await page.evaluate(({ data, frames, size }) => {
    const g = document.getElementById('g');
    data.forEach((d) => frames.forEach(f => {
      const cell = document.createElement('div');
      cell.style.cssText = `width:${size}px;height:${size}px;position:relative`;
      const lab = document.createElement('div'); lab.textContent = d.nm + ' @' + f;
      lab.style.cssText = 'position:absolute;left:2px;top:0;z-index:2';
      cell.appendChild(lab); g.appendChild(cell);
      const a = lottie.loadAnimation({ container: cell, renderer: 'svg', loop: false, autoplay: false, animationData: JSON.parse(JSON.stringify(d)) });
      a.goToAndStop(f, true);
    }));
  }, { data, frames, size: +size });
  await page.waitForTimeout(300);
  const h = await page.evaluate(() => document.body.scrollHeight);
  await page.setViewportSize({ width, height: h });
  await page.screenshot({ path: out });
  if (errors.length) console.log('ERRORS:\n' + errors.join('\n'));
  await browser.close();
})();
