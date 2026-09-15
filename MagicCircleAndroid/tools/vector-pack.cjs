'use strict';
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const crypto = require('node:crypto');
const expectedIds = Object.entries({ A: 30, B: 30, C: 30, E: 8, F: 5, G: 5, U: 4, W: 5, R: 1 })
  .flatMap(([group, count]) => Array.from({ length: count }, (_, i) => group + String(i + 1).padStart(2, '0')));
const levelsFor = id => ['C03', 'R01'].includes(id) ? 32 : 20;

function validateSvg(svg) {
  if (!/<svg\b/.test(svg) || /<(?:image|script|foreignObject|animate\w*)\b|data:image|\b(?:href|xlink:href)=["'](?:https?:|\/\/)|NaN|Infinity/i.test(svg)) {
    throw new Error('Expected safe vector SVG without embedded raster or external content');
  }
  for (const match of svg.matchAll(/<path\b([^>]*?)\bd="([^"]*)"([^>]*)>/g)) {
    if (/data-open-stroke="true"/.test(match[1] + match[3])) continue;
    const starts = (match[2].match(/m/gi) || []).length;
    const ends = (match[2].match(/z/gi) || []).length;
    if (starts !== ends) throw new Error('Every filled contour must be closed');
  }
}

function compactPath(d) {
  // Only rewrite the absolute command dialect emitted by the source tracer.
  // Integer coordinates preserve all four decimal places without cumulative drift.
  if (/[a-zA-KN-PR-UW-Y]/.test(d) || /\d\.\d{5}/.test(d)) return d;
  const format = value => String(value / 10000).replace(/^(-?)0\./, '$1.');
  const pack = values => values.map(format).join(' ').replace(/ -/g, '-');
  let x = 0, y = 0, startX = 0, startY = 0;
  const output = [];
  for (const match of d.matchAll(/([MLQHVZ])([^MLQHVZ]*)/g)) {
    const command = match[1];
    const points = (match[2].match(/-?\d+(?:\.\d+)?/g) || []).map(value => Math.round(Number(value) * 10000));
    const count = { M: 2, L: 2, Q: 4, H: 1, V: 1, Z: 0 }[command];
    if (points.length !== count || points.some(value => !Number.isSafeInteger(value))) return d;
    if (command === 'M') {
      [x, y] = points; startX = x; startY = y;
      output.push('M' + pack(points));
    } else if (command === 'Z') {
      output.push('Z'); x = startX; y = startY;
    } else if (command === 'Q') {
      const relative = 'q' + pack([points[0] - x, points[1] - y, points[2] - x, points[3] - y]);
      const absolute = 'Q' + pack(points);
      output.push(relative.length < absolute.length ? relative : absolute);
      [x, y] = points.slice(2);
    } else {
      const nextX = command === 'V' ? x : points[0], nextY = command === 'H' ? y : points.at(-1);
      const relative = nextX === x ? 'v' + format(nextY - y) : nextY === y ? 'h' + format(nextX - x) : 'l' + pack([nextX - x, nextY - y]);
      const absolute = 'L' + pack([nextX, nextY]);
      output.push(relative.length < absolute.length ? relative : absolute);
      x = nextX; y = nextY;
    }
  }
  return output.join('');
}

function compactSvg(svg) {
  validateSvg(svg);
  return svg.replace(/<metadata>[\s\S]*?<\/metadata>/g, '').replace(/\bd="([^"]*)"/g, (_, d) => `d="${compactPath(d)}"`);
}

function reduceLevels(svg, levels) {
  if (!Number.isInteger(levels) || levels < 2 || levels > 32) throw new RangeError('levels must be an integer from 2 to 32');
  if (levels === 32) return svg;
  const starts = levels === 20 ? [...Array.from({ length: 8 }, (_, i) => i), ...Array.from({ length: 12 }, (_, i) => 8 + i * 2)]
    : Array.from({ length: levels }, (_, i) => Math.floor(i * 32 / levels));
  const bands = new Map(starts.map((first, i) => {
    const next = starts[i + 1] ?? 32;
    return [first, Math.round((Math.min(255, (first + 1) * 8) + Math.min(255, next * 8)) / 2)];
  }));
  // Reuse selected source contours exactly; only adjacent RGB intensity bands merge.
  return svg.replace(/<g id="([^"]+)-(\d)-(\d+)-([^"]+)" data-layer="([^"]+)"><path fill="(#[0-9a-f]{6})" fill-rule="evenodd" d="([^"]+)"\/><\/g>/g,
    (group, prefix, channel, value, region, layer, color, d) => {
      const index = Math.round(Number(value) / 8) - 1;
      if (!bands.has(index)) return '';
      const rgb = [0, 0, 0]; rgb[Number(channel)] = bands.get(index);
      const paint = '#' + rgb.map(n => n.toString(16).padStart(2, '0')).join('');
      return group.replace(`fill="${color}"`, `fill="${paint}"`);
    });
}

async function buildAsset(id, source, output, levels, applyQuality = svg => svg) {
  if (!expectedIds.includes(id)) throw new RangeError('Unknown collection ID');
  const sharp = require('sharp');
  const started = performance.now();
  const svg = compactSvg(reduceLevels(applyQuality(source, id), levels));
  validateSvg(svg);
  const bytes = Buffer.from(svg), gzip = zlib.gzipSync(bytes, { level: 9 });
  for (const dir of ['art', 'thumbs']) fs.mkdirSync(path.join(output, dir), { recursive: true });
  fs.writeFileSync(path.join(output, 'art', id + '.svgz'), gzip);
  const render = await sharp(bytes, { limitInputPixels: false }).resize(1024, 1024, { fit: 'contain', background: '#000' }).png().toBuffer();
  await sharp(render).resize(320, 320).png({ compressionLevel: 9, adaptiveFiltering: true }).toFile(path.join(output, 'thumbs', id + '.png'));
  const times = Array.from({ length: 5 }, () => {
    const start = performance.now(); zlib.gunzipSync(gzip); return performance.now() - start;
  }).sort((a, b) => a - b);
  return { id, levelsPerChannel: /mix-blend-mode:screen/.test(svg) ? levels : null,
    sourceSvgSha256: crypto.createHash('sha256').update(source).digest('hex'),
    svgSha256: crypto.createHash('sha256').update(bytes).digest('hex'),
    sourceBytes: Buffer.byteLength(source), svgBytes: bytes.length, gzipBytes: gzip.length,
    pathCount: (svg.match(/<path\b/g) || []).length, closedContours: (svg.match(/Z/g) || []).length,
    thumbnailBytes: fs.statSync(path.join(output, 'thumbs', id + '.png')).size,
    gunzipMedianMs: Number(times[2].toFixed(2)), buildMs: Math.round(performance.now() - started) };
}

async function main() {
  const args = process.argv.slice(2), prototype = args.includes('--prototype');
  const levelOverride = args.find(arg => arg.startsWith('--levels='))?.split('=')[1];
  const sourceRoot = path.resolve(__dirname, '../../magic-circle-master');
  const selected = args.filter(arg => expectedIds.includes(arg));
  const ids = selected.length ? selected : prototype ? ['F05', 'G01', 'W04', 'C03'] : [...expectedIds.filter(id => id[0] !== 'A'), ...expectedIds.filter(id => id[0] === 'A')];
  const output = prototype ? fs.mkdtempSync(path.join(require('node:os').tmpdir(), 'v110-vector-prototype-')) : path.resolve(__dirname, '../app/src/main/assets/collection');
  const qualityPath = path.join(__dirname, 'art-quality.cjs');
  const applyQuality = (svg, id) => !prototype && fs.existsSync(qualityPath) ? require(qualityPath).applyQuality(svg, id) : svg;
  const records = [];
  let browser;
  if (prototype) browser = await require('playwright').chromium.launch({ headless: true, channel: 'msedge' });
  try {
    for (const id of ids) {
      const sourceFile = path.join(sourceRoot, 'output/svg', id + '.svg');
      const source = fs.readFileSync(sourceFile, 'utf8');
      const record = await buildAsset(id, source, output, levelOverride ? Number(levelOverride) : levelsFor(id), applyQuality);
      if (prototype) {
        const sharp = require('sharp'), { pathToFileURL } = require('node:url');
        const shippedFile = path.join(output, id + '.svg');
        fs.writeFileSync(shippedFile, zlib.gunzipSync(fs.readFileSync(path.join(output, 'art', id + '.svgz'))));
        const page = await browser.newPage({ viewport: { width: 1024, height: 1024 }, deviceScaleFactor: 1 });
        const shots = [];
        for (const [name, file] of [['source', sourceFile], ['shipped', shippedFile]]) {
          const started = performance.now();
          const wrapper = path.join(output, `${id}-${name}.html`);
          fs.writeFileSync(wrapper, `<html><body style="margin:0;background:#000"><img style="width:1024px;height:1024px;object-fit:contain" src="${pathToFileURL(file).href}"></body></html>`);
          await page.goto(pathToFileURL(wrapper).href, { waitUntil: 'load', timeout: 120000 });
          await page.locator('img').evaluate(async img => { await img.decode(); await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))); });
          const image = await page.screenshot({ path: path.join(output, `${id}-${name}-1024.png`), timeout: 120000 });
          record[name + 'Edge1024Ms'] = Math.round(performance.now() - started);
          shots.push(image);
        }
        const phone = await browser.newPage({ viewport: { width: 1440, height: 3120 }, deviceScaleFactor: 1 });
        const phoneWrapper = path.join(output, `${id}-phone.html`);
        fs.writeFileSync(phoneWrapper, `<html><body style="margin:0;background:#000"><img style="width:1440px;height:3120px;object-fit:contain" src="${pathToFileURL(shippedFile).href}"></body></html>`);
        const phoneStart = performance.now();
        await phone.goto(pathToFileURL(phoneWrapper).href, { waitUntil: 'load', timeout: 120000 });
        record.shippedPhoneImgDecodeMs = await phone.locator('img').evaluate(async img => {
          const start = performance.now(); await img.decode(); await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))); return Math.round(performance.now() - start);
        });
        await phone.screenshot({ path: path.join(output, `${id}-phone.png`), timeout: 120000 });
        record.shippedPhoneTotalMs = Math.round(performance.now() - phoneStart);
        record.phoneCache = 'new browser context; empty cache';
        await phone.close();
        await page.close();
        const [a, b] = await Promise.all(shots.map(image => sharp(image).removeAlpha().raw().toBuffer()));
        let total = 0, foregroundTotal = 0, foregroundSamples = 0, square = 0, maximum = 0;
        for (let i = 0; i < a.length; i += 3) {
          const foreground = Math.max(a[i], a[i + 1], a[i + 2]) > 24;
          for (let j = 0; j < 3; j++) {
            const error = Math.abs(a[i + j] - b[i + j]); total += error; square += error * error; maximum = Math.max(maximum, error);
            if (foreground) { foregroundTotal += error; foregroundSamples++; }
          }
        }
        record.edgeRgbMae = Number((total / a.length).toFixed(3));
        record.foregroundRgbMae = Number((foregroundTotal / foregroundSamples).toFixed(3));
        record.edgeRgbRmse = Number(Math.sqrt(square / a.length).toFixed(3));
        record.maximumChannelDifference = maximum;
        await sharp({ create: { width: 1024, height: 512, channels: 3, background: '#000' } })
          .composite(await Promise.all(shots.map(async (image, i) => ({ input: await sharp(image).resize(512, 512).toBuffer(), left: i * 512, top: 0 }))))
          .png().toFile(path.join(output, `${id}-comparison.png`));
      }
      records.push(record);
      console.log(JSON.stringify(record));
    }
    const report = { version: '1.10', format: 'gzip-compressed SVG; RGB PNG thumbnails rendered from shipped vector at 1024 then reduced to 320',
      optimization: 'Lossless absolute-to-relative path syntax; nested RGB contours reduced by tonal band selection and midpoint repaint. Retained coordinates and analytic shapes unchanged.',
      tonalProfile: '20-band mode preserves original8,16,24,32,40,48,56,64 shadow paints;12 paired bands cover72..255. Other reduced modes use equal-index bands.',
      limitations: [
        'Tonal band selection is lossy: soft gradients can show color banding. It preserves retained path coordinates, not pixel-identical color or every one of the original 32 intensity boundaries.',
        'Original review-master limits remain: low-resolution source details, regional rather than semantic layers, connected A-sheet captions, and the already-cleared U04 battery center.',
        'Desktop Edge timing is a diagnostic, not an Android device timing guarantee; the complete charging lifecycle still needs device verification.'
      ],
      expectedCount: 118, count: records.length, levelsPerChannel: levelOverride ? Number(levelOverride) : '20; measured C03/R01 unchanged',
      gzipTotalBytes: records.reduce((sum, record) => sum + record.gzipBytes, 0),
      thumbnailTotalBytes: records.reduce((sum, record) => sum + record.thumbnailBytes, 0), records };
    fs.writeFileSync(path.join(output, 'pack-report.json'), JSON.stringify(report, null, 2));
    console.log(JSON.stringify({ output, count: report.count, gzipTotalBytes: report.gzipTotalBytes, thumbnailTotalBytes: report.thumbnailTotalBytes }));
  } finally { if (browser) await browser.close(); }
}

module.exports = { expectedIds, compactSvg, validateSvg, reduceLevels, buildAsset, levelsFor };
if (require.main === module) main().catch(error => { console.error(error); process.exitCode = 1; });
