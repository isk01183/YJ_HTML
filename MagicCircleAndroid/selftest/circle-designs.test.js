const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = path.join(__dirname, '../app/src/main/assets/circle-designs.js');
assert.ok(fs.existsSync(source), 'The selectable circle renderer has not been implemented.');
const designs = require(source);

// Missing or malformed battery data must never turn into a convincing fake measurement.
assert.equal(designs.battery(new URLSearchParams()).percent, '—');
assert.equal(designs.battery(new URLSearchParams('battery=-1&temperature=')).temperature, '—');
assert.equal(designs.battery(new URLSearchParams('battery=101')).percent, '—');
assert.equal(designs.battery(new URLSearchParams('battery=78&temperature=280')).percent, '78');
assert.equal(designs.battery(new URLSearchParams('battery=78&temperature=280')).temperature, '28.0°C');
assert.equal(designs.battery(new URLSearchParams('battery=0&temperature=0')).temperature, '0.0°C');
assert.equal(designs.battery(new URLSearchParams('health=1')).health, '不明');
assert.equal(designs.battery(new URLSearchParams('health=2')).health, '良好');
assert.equal(designs.battery(new URLSearchParams('status=5&plugged=1')).status, '充電完了');
assert.equal(designs.battery(new URLSearchParams('status=4&plugged=1')).status, '充電待機');
assert.equal(designs.battery(new URLSearchParams('status=3&plugged=0')).status, '未接続');
assert.equal(designs.battery(new URLSearchParams('status=2&plugged=0')).status, '未接続');

assert.equal(designs.get('../../other').id, 'classic');
for (const theme of designs.list) {
    const svg = designs.svg(theme.id, 'test-' + theme.id);
    assert.ok(svg.startsWith('<svg'), theme.id + ' renders a circle');
    assert.ok(!/NaN|undefined|Infinity/.test(svg), theme.id + ' geometry is finite');
    // Every independently rendered card needs its own filter/gradient ids.
    const ids = [...svg.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
    assert.equal(new Set(ids).size, ids.length, theme.id + ' has no duplicate SVG ids');
    assert.ok(ids.every(id => id.startsWith('test-' + theme.id)), theme.id + ' scopes its SVG resources');
}
console.log('CIRCLE_DESIGNS_OK');
