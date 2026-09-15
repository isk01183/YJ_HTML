'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

(async () => {
  // Catches damaged curve coordinates, fill rules, color stacks, and unclosed contours.
  const modulePath = path.join(__dirname, '../tools/vector-pack.cjs');
  assert.ok(fs.existsSync(modulePath), 'lossless vector packer must exist');
  const { compactSvg, validateSvg, expectedIds, reduceLevels, buildAsset, levelsFor } = require(modulePath);
  const sharp = require('sharp');
  const source = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"><metadata>source report</metadata><rect width="1024" height="1024" fill="#000"/><g style="mix-blend-mode:screen;isolation:isolate" data-layer="rune-ring"><path fill="#80a0ff" fill-rule="evenodd" d="M400.25 300.125L500.25 300.125Q550.5 350.625 500.25 400.125L400.25 400.125L400.25 300.125ZM420 325L470 325L470 375L420 375Z"/></g><path fill="none" stroke="#fff" data-open-stroke="true" d="M400 450L500 450"/><path fill="#ff0" d="M10 10C20 0 30 0 40 10Z"/></svg>';
  const packed = compactSvg(source);
  assert.ok(packed.length < source.length, 'redundant coordinate bytes should be reduced');
  assert.match(packed, /data-layer="rune-ring"/);
  assert.doesNotMatch(packed, /<metadata/);
  assert.deepEqual(await sharp(Buffer.from(packed)).raw().toBuffer(), await sharp(Buffer.from(source)).raw().toBuffer(), 'lossless packing must render identical curves, colors, holes, and strokes');
  assert.equal(zlib.gunzipSync(zlib.gzipSync(packed)).toString(), packed);
  assert.throws(() => validateSvg(source.replace('Z"/></svg>', '"/></svg>')), /closed/);
  for (const unsafe of ['<image href="x.png"/>', '<script/>', '<foreignObject/>', '<use href="https://example.com/a.svg"/>']) {
    assert.throws(() => validateSvg(source.replace('</svg>', unsafe + '</svg>')), /vector|external|unsafe/i);
  }
  assert.equal(expectedIds.length, 118);
  assert.equal(new Set(expectedIds).size, 118);
  assert.ok(expectedIds.includes('A30') && expectedIds.includes('W05') && expectedIds.includes('R01'));
  assert.ok(!expectedIds.includes('D01') && !expectedIds.includes('U05'));
  assert.equal(typeof reduceLevels, 'function', 'bounded tonal optimization must exist');
  const contours = '<svg xmlns="http://www.w3.org/2000/svg" width="4" height="1"><rect width="4" height="1" fill="#000"/><g id="F01-source-red" style="mix-blend-mode:screen;isolation:isolate">' +
    '<g id="F01-source-0-8-decoration" data-layer="decoration"><path fill="#080000" fill-rule="evenodd" d="M0 0H4V1H0Z"/></g>' +
    '<g id="F01-source-0-16-decoration" data-layer="decoration"><path fill="#100000" fill-rule="evenodd" d="M1 0H4V1H1Z"/></g>' +
    '<g id="F01-source-0-24-decoration" data-layer="decoration"><path fill="#180000" fill-rule="evenodd" d="M2 0H4V1H2Z"/></g></g></svg>';
  const reduced = reduceLevels(contours, 16);
  assert.deepEqual([...await sharp(Buffer.from(reduced)).removeAlpha().raw().toBuffer()], [12,0,0, 12,0,0, 28,0,0, 28,0,0], 'tonal midpoint remapping must avoid a dark bias');
  assert.match(reduced, /d="M0 0H4V1H0Z"/);
  assert.match(reduced, /d="M2 0H4V1H2Z"/);
  assert.equal(reduceLevels(contours, 32), contours, 'full tonal mode must be lossless');
  assert.match(reduceLevels(contours, 12), /fill="#200000"/, 'uneven 12-band boundaries must use their actual midpoint');
  const shadows = reduceLevels(contours, 20);
  assert.deepEqual([...await sharp(Buffer.from(shadows)).removeAlpha().raw().toBuffer()], [8,0,0, 16,0,0, 24,0,0, 24,0,0], '20-band profile must preserve source shadow colors rather than add green/blue bands');
  assert.throws(() => reduceLevels(contours, 0), /levels/);
  assert.throws(() => reduceLevels(contours, 33), /levels/);
  assert.equal(typeof buildAsset, 'function', 'asset packaging must create a gzip SVG and a thumbnail from that SVG');
  const temp = fs.mkdtempSync(path.join(require('node:os').tmpdir(), 'vector-pack-test-'));
  try {
    const report = await buildAsset('C03', source, temp, 8);
    const delivered = zlib.gunzipSync(fs.readFileSync(path.join(temp, 'art/C03.svgz'))).toString();
    assert.equal(delivered, packed, 'non-channel measured art must keep its full palette');
    assert.equal(report.gzipBytes, fs.statSync(path.join(temp, 'art/C03.svgz')).size);
    assert.equal((await sharp(path.join(temp, 'thumbs/C03.png')).metadata()).width, 320);
    await assert.rejects(() => buildAsset('../escape', source, temp, 8), /ID/);
  } finally { fs.rmSync(temp, { recursive: true }); }
  assert.equal(typeof levelsFor, 'function', 'package budget must retain extra tonal levels for painted premium art');
  assert.equal(levelsFor('W04'), 20);
  assert.equal(levelsFor('F05'), 20);
  assert.equal(levelsFor('G01'), 20);
  assert.equal(levelsFor('A01'), 20);
  assert.equal(levelsFor('C03'), 32);
  assert.equal(levelsFor('R01'), 32);
  if (process.argv.includes('--full')) {
    const collection = path.join(__dirname, '../app/src/main/assets/collection');
    assert.ok(fs.existsSync(path.join(collection, 'pack-report.json')), 'all 118 shipped vector resources must have a pack report');
    const report = JSON.parse(fs.readFileSync(path.join(collection, 'pack-report.json')));
    assert.deepEqual(report.records.map(record => record.id).sort(), [...expectedIds].sort());
    assert.deepEqual(fs.readdirSync(path.join(collection, 'art')).sort(), expectedIds.map(id => id + '.svgz').sort());
    assert.deepEqual(fs.readdirSync(path.join(collection, 'thumbs')).sort(), expectedIds.map(id => id + '.png').sort());
    let gzipTotal = 0, thumbsTotal = 0;
    for (const record of report.records) {
      assert.equal(record.levelsPerChannel, ['C03', 'R01'].includes(record.id) ? null : 20, record.id + ' must use the final 20-band color profile');
      const zipped = fs.readFileSync(path.join(collection, 'art', record.id + '.svgz'));
      const svg = zlib.gunzipSync(zipped);
      validateSvg(svg.toString());
      assert.equal(require('node:crypto').createHash('sha256').update(svg).digest('hex'), record.svgSha256, record.id + ' must match audited vector');
      const source = fs.readFileSync(path.resolve(__dirname, '../../magic-circle-master/output/svg', record.id + '.svg'));
      assert.equal(require('node:crypto').createHash('sha256').update(source).digest('hex'), record.sourceSvgSha256, record.id + ' source SVG must remain unchanged');
      assert.equal(zipped.length, record.gzipBytes);
      const thumb = path.join(collection, 'thumbs', record.id + '.png');
      const info = await sharp(thumb).metadata();
      assert.equal(info.width, 320); assert.equal(info.height, 320);
      gzipTotal += zipped.length; thumbsTotal += fs.statSync(thumb).size;
    }
    assert.equal(gzipTotal, report.gzipTotalBytes);
    assert.equal(thumbsTotal, report.thumbnailTotalBytes);
    assert.ok(gzipTotal + thumbsTotal < 230000000, 'collection must remain within the approved 230 MB release target');
    console.log('VECTOR_PACK_FULL_OK: 118 unique vector-only assets, hashes, dimensions and package budget');
  }
  console.log('VECTOR_PACK_OK: lossless paths/colors, closed contours, no embedded/external content, exact 118 IDs');
})().catch(error => { console.error(error); process.exitCode = 1; });
