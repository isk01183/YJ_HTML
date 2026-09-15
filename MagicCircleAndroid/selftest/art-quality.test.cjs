'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

(async () => {
  const modulePath = path.join(__dirname, '../tools/art-quality.cjs');
  assert.ok(fs.existsSync(modulePath), 'confirmed panel separators must be removed before packaging');
  const { applyQuality } = require(modulePath);
  const sharp = require('sharp');
  const cases = [
    { id: 'A04', size: 231, x: 220, top: 27 },
    { id: 'A05', size: 237, x: 230, top: 30 }
  ];
  const render = svg => sharp(Buffer.from(svg)).resize(1024, 1024).removeAlpha().raw().toBuffer();
  const channelAt = (bytes, x, y) => bytes.subarray((y * 1024 + x) * 3, (y * 1024 + x) * 3 + 3);
  function verify(before, after, item) {
    const scale = 1024 / item.size;
    assert.ok(Math.max(...channelAt(before, Math.round((item.x + 1) * scale), 512)) > 20,
      item.id + ' original separator is visible');
    assert.equal(Math.max(...channelAt(after, Math.round((item.x + 1) * scale), 512)), 0,
      item.id + ' separator must become black');
    let changed = 0;
    for (let y = 0; y < 1024; y++) for (let x = 0; x < 1024; x++) {
      const index = (y * 1024 + x) * 3;
      const different = before[index] !== after[index] || before[index + 1] !== after[index + 1]
        || before[index + 2] !== after[index + 2];
      if (!different) continue;
      changed++;
      assert.ok(x >= Math.floor((item.x - 1) * scale) && x <= Math.ceil((item.x + 3) * scale)
        && y >= Math.floor((item.top - 1) * scale) && y <= Math.ceil((item.top + 177) * scale),
        item.id + ' must preserve every pixel outside the confirmed separator at ' + x + ',' + y);
    }
    assert.ok(changed > 0, item.id + ' needs a real rendered change');
  }
  for (const item of cases) {
    // Wrong native scale/padding must leave the cyan line or damage nearby artwork/caption.
    const source = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">'
      + '<rect width="1024" height="1024" fill="#000"/><g transform="scale(' + 1024 / item.size + ')">'
      + '<rect x="' + item.x + '" y="' + item.top + '" width="2" height="176" fill="#00bfff"/>'
      + '<circle cx="110" cy="110" r="90" fill="none" stroke="#fff"/>'
      + '<path d="M80 190H150V200H80Z" fill="#fff"/></g></svg>';
    const clean = applyQuality(source, item.id);
    verify(await render(source), await render(clean), item);
    assert.equal(applyQuality(clean, item.id), clean, 'repack must not stack duplicate masks');
    assert.equal(applyQuality(source, 'A13'), source, 'ambiguous caption glyphs stay unchanged');
    assert.equal(applyQuality(source, 'U04'), source, 'clean mockup crop stays unchanged');
    for (const other of ['constructor', '__proto__', null, 'ref-A04', 'A040']) {
      assert.equal(applyQuality(source, other), source, 'only exact audited IDs can receive a mask');
    }
    assert.throws(() => applyQuality(source.replace('0 0 1024 1024', '0 0 512 512'), item.id),
      /viewBox/, 'changed source coordinates must fail instead of masking unrelated art');
  }
  if (process.argv.includes('--master')) {
    const output = fs.mkdtempSync(path.join(require('node:os').tmpdir(), 'v110-art-quality-'));
    for (const item of cases) {
      const source = fs.readFileSync(path.join(__dirname, '../../magic-circle-master/output/svg', item.id + '.svg'), 'utf8');
      const cleaned = applyQuality(source, item.id);
      const before = await render(source), after = await render(cleaned);
      verify(before, after, item);
      for (const [name, bytes] of [['before', before], ['after', after]]) {
        await sharp(bytes, { raw: { width: 1024, height: 1024, channels: 3 } })
          .png().toFile(path.join(output, item.id + '-' + name + '.png'));
      }
    }
    console.log('ART_QUALITY_MASTER_PNGS=' + output);
  }
  console.log('ART_QUALITY_OK: exact panel cleanup; all outside pixels, captions and unrelated designs preserved');
})().catch(error => { console.error(error); process.exitCode = 1; });
