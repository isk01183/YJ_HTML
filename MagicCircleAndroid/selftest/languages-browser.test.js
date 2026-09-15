const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const {pathToFileURL} = require('node:url');
const {chromium} = require('playwright');
const assets = path.resolve(__dirname,'../app/src/main/assets');
const url = name => pathToFileURL(path.join(assets,name)).href;
(async () => {
    const browser = await chromium.launch({headless:true, channel:'msedge'});
    try {
        const page = await browser.newPage({viewport:{width:412,height:915}});
        await page.context().setOffline(true);
        page.setDefaultTimeout(4000);
        const errors=[]; page.on('pageerror', error=>errors.push(error.message));
        await page.goto(url('gallery.html'));
        assert.equal(await page.locator('[data-language]').count(),3, 'Three in-app language choices must be available');
        await page.locator('[data-group="previous"]').click();
        for (const [language,title,charging] of [['ko','마법진 보관함','충전 시작'],['en','The Arcane Archive','Charging begins'],['ja','魔法陣の書庫','充電開始']]) {
            await page.locator('[data-language="'+language+'"]').click();
            assert.equal(await page.locator('h1').textContent(),title);
            assert.equal(await page.locator('html').getAttribute('lang'),language);
            await page.locator('[data-theme="raphael"]').click();
            await page.locator('#preview').click();
            const frame = page.frameLocator('#browser-preview iframe');
            await frame.locator('.begin h1').waitFor({state:'attached'});
            assert.equal(await frame.locator('.begin h1').textContent(),charging);
            assert.equal(new URL(await page.locator('#browser-preview iframe').getAttribute('src'),url('gallery.html')).searchParams.get('lang'),language);
            await page.locator('#close-preview').click();
            assert.equal(await page.locator('[data-theme="classic"] .applied-badge').isVisible(),true, 'Preview must not replace the saved theme');
        }
        // Native state restoration updates language without losing a newly focused, unapplied card.
        await page.evaluate(()=>window.setGalleryState({selected:'moon',language:'ko',enabled:true}));
        await page.locator('[data-theme="premium"]').click();
        await page.evaluate(()=>window.setGalleryState({selected:'moon',language:'en',enabled:false}));
        assert.equal(await page.locator('#hero-title').textContent(),'Stargazer’s Sanctuary');
        assert.equal(await page.locator('#service-label').textContent(),'Charging animation: off');
        assert.equal(await page.locator('#service-hint').isVisible(),true);
        assert.equal(await page.locator('#selection-status').textContent(),'Active: Moonlit Runes');
        // Classic and themed pages must both localize, render, and end within the same 7-second sequence.
        for (const language of ['ko','ja','en']) for (const filename of ['magic_circle.html','theme_circle.html']) {
            await page.goto(url(filename)+'?theme=premium&lang='+language+'&battery=78&health=2&status=2&plugged=4');
            await page.evaluate(()=>window.startChargingAnimation());
            assert.equal(await page.locator('html').getAttribute('lang'),language);
            const visibleText=await page.locator('body').innerText();
            if(language!=='ja') assert.ok(!/[\u3040-\u30ff]/u.test(visibleText), filename+' leaks Japanese in '+language);
            await page.evaluate(()=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=4200;}));
            if(filename==='theme_circle.html') assert.equal(await page.locator('[data-level]').first().textContent(),'78');
            await page.evaluate(()=>document.getAnimations().forEach(a=>a.currentTime=7000));
            assert.equal(await page.locator('.scene,#scene').evaluate(node=>getComputedStyle(node).opacity),'0');
        }
        await page.setViewportSize({width:320,height:568});
        await page.goto(url('theme_circle.html')+'?theme=premium&lang=en&demo=1');
        await page.evaluate(()=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=4200;}));
        const bottom=await page.locator('.readout').evaluate(node=>node.getBoundingClientRect().bottom);
        assert.ok(bottom<=568,'Battery readout must fit a compact screen: '+bottom);
        assert.deepEqual(errors,[]);
        console.log('LANGUAGES_BROWSER_OK: 3 languages, preview parity, restored state, classic + themes, 7s');
    } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1;});
