// node raster.js <in.svg|png|gif|webp> <out.png> [width]  -- flattens any logo to an RGBA PNG for colour sampling.
const sharp = require(process.env.SHARP_DIR + "/node_modules/sharp");
const [input, out, width] = process.argv.slice(2);
sharp(input, { density: 200, animated: false }).resize({ width: Number(width || 400), withoutEnlargement: false })
  .ensureAlpha().png().toFile(out).catch(e => { console.error(e.message); process.exit(1); });
