const assert = require('node:assert/strict');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {chromium} = require('playwright');
const designs = require('../app/src/main/assets/circle-designs.js');

(async () => {
    const browser = await chromium.launch({headless:true, channel:'msedge'});
    try {
        const page = await browser.newPage({viewport:{width:412,height:915},deviceScaleFactor:1});
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        const assets = path.resolve(__dirname,'../app/src/main/assets');
        await page.goto(pathToFileURL(path.join(assets,'gallery.html')).href+'?lang=ja');
        assert.equal(await page.locator('.card').count(),designs.list.length);
        await page.locator('[data-theme="raphael"]').click();
        assert.equal(await page.locator('#hero-title').textContent(),'智慧の大賢者');
        assert.equal(await page.locator('#apply').isEnabled(),true);
        await page.locator('#preview').click();
        assert.equal(await page.locator('#browser-preview').isVisible(),true);
        assert.equal(await page.locator('[data-theme="classic"] .applied-badge').isVisible(),true);
        await page.locator('#close-preview').click();
        assert.equal(await page.locator('#browser-preview').isVisible(),false);
        await page.locator('#apply').click();
        assert.equal(await page.locator('#apply').isDisabled(),true);
        await page.locator('[data-group="variation"]').click();
        assert.equal(await page.locator('.card:visible').count(),8);
        await page.locator('[data-group="all"]').click();
        await page.screenshot({path:path.join(__dirname,'../gallery-mobile.png')});
        await page.locator('[data-group="collection"]').click();
        await page.locator('[data-language="ko"]').click();
        await page.locator('#search').fill('벚꽃');
        assert.equal(await page.locator('.card:visible').count(),1);
        await page.locator('.card:visible').click();
        assert.equal(await page.locator('#hero-art').getAttribute('data-rendered-theme'),'sakura-seal');
        await page.locator('#search').fill('no-such-magic-circle');
        assert.equal(await page.locator('#empty').isVisible(),true);
        await page.locator('#search').fill('');
        for (const theme of designs.list.filter(theme=>theme.id!=='classic')) {
            await page.goto(pathToFileURL(path.join(assets,'theme_circle.html')).href+'?theme='+theme.id+'&lang=ja&battery=78&temperature=280&health=2&status=2&plugged=2');
            await page.evaluate(()=>{
                window.startChargingAnimation();
                document.getAnimations().forEach(animation=>{animation.pause();animation.currentTime=4200;});
            });
            assert.equal(await page.locator('#theme-name').textContent(),theme.name);
            assert.equal(await page.locator('[data-level]').first().textContent(),'78');
            assert.equal(await page.locator('.fixed-seals').evaluate(node=>getComputedStyle(node).transform),'none');
            assert.equal(await page.locator('.ritual').evaluate(node=>getComputedStyle(node).opacity),'1');
            assert.equal(await page.locator('.circle-svg').evaluate(node=>Number.isFinite(node.getBBox().width)),true);
            if (theme.group==='signature') await page.screenshot({path:path.join(__dirname,'../theme-'+theme.id+'.png')});
            await page.evaluate(()=>document.getAnimations().forEach(animation=>animation.currentTime=7000));
            assert.equal(await page.locator('.scene').evaluate(node=>getComputedStyle(node).opacity),'0');
        }
        await page.setViewportSize({width:1280,height:800});
        await page.goto(pathToFileURL(path.join(assets,'theme_circle.html')).href+'?theme=premium&demo=1');
        await page.evaluate(()=>document.getAnimations().forEach(animation=>{animation.pause();animation.currentTime=4200;}));
        assert.equal(await page.locator('#temperature').textContent(),'—');
        await page.screenshot({path:path.join(__dirname,'../theme-tablet.png')});
        assert.deepEqual(errors,[]);
        console.log('BROWSER_DESIGNS_OK: selection, '+designs.list.length+' themes, search, real/unknown battery, fixed stars, 7s, tablet');
    } finally { await browser.close(); }
})().catch(error=>{console.error(error);process.exitCode=1;});
