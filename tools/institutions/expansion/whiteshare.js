// node whiteshare.js <file.svg>  -> share of opaque pixels (alpha > 200) that are near-white (min channel > 225),
// rasterised at 240px wide: the same measurement as lucento-wallet-design/tools/designs.py white_pixel_share().
const sharp = require(process.env.SHARP_DIR + "/node_modules/sharp");
(async () => {
  const { data } = await sharp(process.argv[2], { density: 200 }).resize({ width: 240 }).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  let opaque = 0, white = 0;
  for (let i = 0; i < data.length; i += 4) if (data[i + 3] > 200) { opaque++; if (Math.min(data[i], data[i + 1], data[i + 2]) > 225) white++; }
  console.log(opaque ? (white / opaque).toFixed(4) : "null");
})().catch(e => { console.error(e.message); process.exit(1); });
