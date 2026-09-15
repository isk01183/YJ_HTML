const assert = require('node:assert/strict');
const designs = require('../app/src/main/assets/circle-designs.js');

// Parse the generated XML shape, not HTML's error-corrected result: malformed groups
// and duplicate attributes can silently erase only part of an Android WebView seal.
function validateSvg(source, id) {
    const stack = [], ids = new Set(), refs = [];
    let elements = 0;
    for (const token of source.matchAll(/<([^>]+)>/g)) {
        const tag = token[1], closing = tag.startsWith('/'), name = tag.match(/^\/?([\w-]+)/)?.[1];
        assert.ok(name, id+' has a valid element');
        if (closing) { assert.equal(stack.pop(),name,id+' has balanced SVG groups'); continue; }
        elements++;
        const attrs = new Set();
        for (const attribute of tag.matchAll(/([\w:-]+)="([^"]*)"/g)) {
            const [,key,value] = attribute;
            assert.ok(!attrs.has(key),id+' has no duplicate '+key+' attribute');
            attrs.add(key);
            assert.ok(!/NaN|Infinity|undefined/.test(value),id+' has finite geometry');
            if (key==='id') { assert.ok(!ids.has(value),id+' has unique resources'); ids.add(value); }
            for (const ref of value.matchAll(/url\(#([^)]*)\)/g)) refs.push(ref[1]);
            if (key==='points') {
                const coordinates = value.trim().split(/[ ,]+/).map(Number);
                assert.ok(coordinates.length>=6 && coordinates.length%2===0,id+' closes a complete polygon');
                assert.ok(coordinates.every(Number.isFinite),id+' polygon coordinates are finite');
            }
            if (key==='d') assert.match(value,/^[Mm]/,id+' path starts at a defined point');
        }
        if (!tag.endsWith('/')) stack.push(name);
    }
    assert.deepEqual(stack,[],id+' closes every SVG element');
    assert.ok(refs.every(ref=>ids.has(ref)),id+' resolves each gradient/filter locally');
    assert.ok([...ids].every(ref=>ref.startsWith('qa-'+id+'-')),id+' scopes resources per card');
    assert.ok(elements<1300,id+' stays within the mobile SVG budget ('+elements+')');
    assert.ok(!/<(?:image|script|foreignObject|animateTransform)\b|(?:href|src)="https?:/i.test(source),id+' stays offline and keeps the seal fixed');
    assert.ok(!/<animate\w*\b/.test(source),id+' has no unstarted SVG timeline');
    return elements;
}

assert.equal(designs.get('not-a-theme').id,'classic','Unknown persisted themes retain the original fallback');
assert.equal(new Set(designs.list.map(t=>t.id)).size,designs.list.length,'Theme IDs do not collide');
const motifs = new Set();
let maxElements = 0;
for (const theme of designs.list.filter(t=>t.group==='collection')) {
    for (const lang of ['ja','ko','en']) {
        assert.ok(theme.names[lang].trim() && theme.descriptions[lang].trim(),theme.id+' supplies '+lang);
    }
    const svg = designs.svg(theme.id,'qa-'+theme.id);
    maxElements=Math.max(maxElements,validateSvg(svg,theme.id));
    const motif = svg.slice(svg.indexOf('<g class="fixed-seals"')).replace(/data-motif="[^"]+"|#[0-9a-f]{6}/gi,'');
    // Compare the actual geometry after removing color and identity; recolored clones fail.
    const withoutDust=motif.slice(0,motif.indexOf('<circle class="mote"'));
    assert.ok(!motifs.has(withoutDust),theme.id+' has distinct fixed geometry');
    motifs.add(withoutDust);
}
assert.equal(motifs.size,42,'All requested additional motifs are available');
console.log('NEW_CIRCLE_DESIGNS_OK: 42 distinct, localized, offline, well-formed fixed motifs; maximum '+maxElements+' SVG elements');

if (process.argv.includes('--visual')) {
    (async()=>{
        const {chromium}=require('playwright');
        const path=require('node:path');
        const browser=await chromium.launch({headless:true,channel:'msedge'});
        try {
            const page=await browser.newPage({viewport:{width:1500,height:1200},deviceScaleFactor:1});
            const errors=[];
            page.on('pageerror',error=>errors.push(error.message));
            page.on('console',msg=>{if(msg.type()==='error')errors.push(msg.text());});
            const themes=designs.list.filter(t=>t.group==='collection');
            await page.setContent('<!doctype html><meta charset="utf-8"><style>body{margin:0;background:#020508;color:#e8dfce;font:12px system-ui}main{display:grid;grid-template-columns:repeat(6,1fr);gap:1px;padding:12px}article{padding:12px 10px 14px;border:1px solid #25313b;background:radial-gradient(ellipse at center,#0a121b 0%,#020407 64%);text-align:center}svg{width:100%;display:block}h2{font-size:12px;font-weight:400;margin:6px 0 2px}p{font-size:9px;color:#8493a1;margin:0}</style><main>'+themes.map(t=>'<article>'+designs.svg(t.id,'qa-'+t.id)+'<h2>'+t.names.ko+'</h2><p>'+t.names.en+'</p></article>').join('')+'</main>');
            const geometry=await page.locator('.fixed-seals').evaluateAll(nodes=>nodes.map(node=>{
                const bounds=node.getBBox();
                return {id:node.getAttribute('data-motif'),width:bounds.width,height:bounds.height,transform:getComputedStyle(node).transform};
            }));
            for(const box of geometry) {
                assert.ok(box.width>100 && box.width<500 && box.height>100 && box.height<500,box.id+' has a visible, bounded motif');
                assert.equal(box.transform,'none',box.id+' does not rotate the central seal');
            }
            assert.deepEqual(errors,[],'Chromium renders every SVG path without errors');
            await page.screenshot({path:path.join(__dirname,'../collection-v18-contact.png'),fullPage:true});
            const {pathToFileURL}=require('node:url');
            await page.setViewportSize({width:412,height:915});
            for(const id of ['celestial-satellites','sage-nexus','seraph-wings','world-tree']) {
                await page.goto(pathToFileURL(path.join(__dirname,'../app/src/main/assets/theme_circle.html')).href+'?theme='+id+'&lang=ko&battery=78&temperature=280&health=2&plugged=2&status=2');
                await page.evaluate(()=>{
                    window.startChargingAnimation();
                    document.getAnimations().forEach(animation=>{animation.pause();animation.currentTime=4200;});
                });
                assert.equal(await page.locator('.ritual').evaluate(node=>getComputedStyle(node).opacity),'1',id+' is visible during the charging ritual');
                assert.equal(await page.locator('.fixed-seals').evaluate(node=>getComputedStyle(node).transform),'none',id+' keeps its central motif still');
                await page.screenshot({path:path.join(__dirname,'../collection-phone-'+id+'.png')});
            }
            assert.deepEqual(errors,[],'Phone charging previews also render without errors');
            console.log('COLLECTION_VISUAL_OK: 42 bounded non-rotating motifs; 4 phone previews at 4200ms; no browser errors');
        } finally { await browser.close(); }
    })().catch(error=>{console.error(error);process.exitCode=1;});
}
