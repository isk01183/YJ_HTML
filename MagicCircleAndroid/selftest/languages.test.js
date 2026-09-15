const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const source = path.join(__dirname, '../app/src/main/assets/i18n.js');
assert.ok(fs.existsSync(source), 'Shared offline language support is missing');
const i18n = require(source);
const designs = require('../app/src/main/assets/circle-designs.js');

// Invalid URL language values must not select arbitrary object properties or markup.
for (const language of [null, '', 'fr', '__proto__', 'constructor', '<script>']) {
    assert.equal(i18n.normalize(language), 'en');
}
assert.equal(i18n.t('charging', 'ko'), '충전 중');
assert.equal(i18n.t('charging', 'ja'), '充電中');
assert.equal(i18n.t('charging', 'en'), 'Charging');
assert.equal(i18n.t('designCount', 'en', {count:55}), '55 designs');
for (const language of ['ko', 'ja', 'en']) {
    for (const [key, translations] of Object.entries(i18n.strings)) {
        assert.equal(typeof translations[language], 'string', key + ' missing ' + language);
        assert.ok(translations[language].length, key + ' empty ' + language);
    }
    for (const theme of designs.list) {
        const translated = i18n.theme(theme, language);
        assert.ok(translated.name && translated.desc, theme.id + ' missing translated text');
        if (language !== 'ja') assert.ok(!/[\u3040-\u30ff]/u.test(translated.name + translated.desc), theme.id + ' leaked Japanese');
    }
}
const info = designs.battery(new URLSearchParams('battery=78&health=2&status=2&plugged=4'));
assert.deepEqual(i18n.battery(info, 'ko'), {...info, health:'양호', status:'충전 중', connection:'무선'});
assert.deepEqual(i18n.battery(info, 'en'), {...info, health:'Good', status:'Charging', connection:'Wireless'});
assert.equal(i18n.battery(designs.battery(new URLSearchParams()), 'en').health, 'Unknown');
console.log('LANGUAGES_OK: translations, theme coverage, battery, invalid-language fallback');
