'use strict';
// Catches a stale picture used instead of direct paths, wrong IDs/status, and replay deadlines.
const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs'),zlib=require('node:zlib');
const {pathToFileURL}=require('node:url'),{chromium}=require('playwright');
const assets=path.resolve(__dirname,'../app/src/main/assets');
const refs=require(path.join(assets,'collection-catalog.js'));
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];let legacyRequests=0;
  page.on('pageerror',e=>errors.push(e.message));await page.context().setOffline(true);
  await page.route('https://appassets.androidplatform.net/collection/**',async route=>{
   legacyRequests++;const code=/\/([A-Z]\d{2})\.svg/.exec(route.request().url())?.[1];
   assert.ok(refs.get('ref-'+code));
   await route.fulfill({contentType:'image/svg+xml',body:zlib.gunzipSync(fs.readFileSync(path.join(assets,'collection/art',code+'.svgz')))});
  });
  const go=(file,query='')=>page.goto(pathToFileURL(path.join(assets,file)).href+query);
  await go('gallery.html','?lang=ko');
  assert.equal(await page.evaluate(()=>typeof globalThis.DirectCircles),'object','App must load the directly authored renderer');
  assert.deepEqual((await page.evaluate(()=>DirectCircles.ids)).sort(),refs.list.map(t=>t.code).sort(),'All 118 codes must use directly authored paths');
  assert.equal(await page.locator('.card').count(),174,'Keep all 173 existing choices and add N01');
  assert.equal(await page.locator('.card').first().getAttribute('data-theme'),'native-N01');
  assert.match(await page.locator('[data-theme="native-N01"] .art img').getAttribute('src'),/^data:image\/svg\+xml/);
  for(const [lang,hint] of [['ko','네이티브 화면은 앱의 미리보기에서 확인'],['ja','ネイティブ画面はアプリのプレビューで確認'],['en','View the native screen in the app preview']]){
   await page.locator(`[data-language="${lang}"]`).click();
   assert.match(await page.locator('[data-theme="native-N01"] .tag').textContent(),new RegExp(hint));
   assert.match(await page.locator('#hero-description').textContent(),new RegExp(hint));
  }
  for(const lang of ['ko','ja','en']){
   await page.locator(`[data-language="${lang}"]`).click();
   for(const code of ['C17','A01','R01','W03']){
    await page.locator('#search').fill(code);await page.locator(`[data-theme="ref-${code}"]`).click();
    assert.ok((await page.locator('#hero-title').textContent()).includes(code),'Stable code in every language');
    assert.ok((await page.locator('#hero-tag').textContent()).includes(code),'Explicit review identifier');
    assert.equal(await page.locator('#hero-art svg').getAttribute('aria-hidden'),'true','Localized adjacent title names the decorative drawing');
   }
  }
  await page.locator('#search').fill('C20');await page.locator('[data-theme="ref-C20"]').click();
  assert.equal(await page.locator('#hero-art svg[data-direct-circle="C20"]').count(),1,'Selected preview uses the same paths as charging');
  for(const code of ['A01','B17','C03','C20','C25','E03','F05','G04','R01','U04','W03','W05']){
   const before=legacyRequests;
   for(let run=0;run<2;run++){
    await go('collection_circle.html',`?theme=ref-${code}&lang=ko&battery=78&run=${run}`);
    await page.clock.install();
    assert.equal(await page.locator(`[data-direct-circle="${code}"]`).count(),1);
    assert.equal(await page.locator('#collection-art').count(),0,'Direct renderer must not load an old SVG image');
    assert.match(await page.locator('#review-code').textContent(),new RegExp(code));
    assert.match(await page.locator('#review-code').textContent(),/검토용/);
    assert.equal(await page.evaluate(()=>window.startChargingAnimation(4500)),true);
    const fixed=await page.locator('#direct-art svg,[data-fixed]').evaluateAll(es=>es.map(e=>{const m=e.getScreenCTM();return [m.a,m.b,m.c,m.d,m.e,m.f];}));
    assert.equal(await page.locator('#direct-art svg').getAttribute('aria-hidden'),'true');
    await page.clock.runFor(2200);
    // Virtual JS time does not advance the browser compositor's CSS animation clock.
    const duration=await page.locator('.scene').evaluate(e=>{const a=e.getAnimations()[0];return a.effect.getComputedTiming().endTime/a.playbackRate;});
    assert.ok(duration<=4500,'CSS playback consumes the original remaining budget');
    await page.evaluate(()=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=3500;}));
    assert.ok(Number(await page.locator('.scene').evaluate(e=>getComputedStyle(e).opacity))>.9);
    assert.deepEqual(await page.locator('#direct-art svg,[data-fixed]').evaluateAll(es=>es.map(e=>{const m=e.getScreenCTM();return [m.a,m.b,m.c,m.d,m.e,m.f];})),fixed,'Effective transforms, including CSS and ancestors, remain fixed');
    assert.equal(await page.evaluate(()=>window.startChargingAnimation(7000)),true,'Retry does not restart');
    await page.clock.runFor(2400);
    await page.evaluate(()=>document.getAnimations().forEach(a=>{a.currentTime=7000;}));
    assert.equal(await page.locator('.scene').evaluate(e=>getComputedStyle(e).opacity),'0');
    assert.equal(await page.evaluate(()=>window.startChargingAnimation()),false,'Expired sequence cannot reopen');
   }
   assert.equal(legacyRequests,before,'No original-image fallback for direct art');
  }
  await go('collection_circle.html','?theme=ref-A01&lang=en');
  assert.match(await page.locator('#review-code').textContent(),/A01.*review/i,'New artwork must be explicitly marked for review');
  assert.equal(await page.evaluate(()=>window.startChargingAnimation()),true);
  await go('collection_circle.html','?theme=ref-C99');
  assert.equal(await page.evaluate(()=>window.startChargingAnimation()),false);
  await go('gallery.html','?lang=ko');
  for(const theme of refs.list){
   const card=page.locator(`[data-theme="${theme.id}"]`);await card.scrollIntoViewIfNeeded();
   await page.waitForFunction(id=>document.querySelector(`[data-theme="${id}"] img`).hasAttribute('src'),theme.id);
   await card.locator('img').evaluate(async (img,id)=>{try{await img.decode();if(!img.naturalWidth)throw new Error('empty')}catch(e){throw new Error(id+' thumbnail: '+e.message)}},theme.id);
  }
  assert.deepEqual(errors,[]);
  console.log('DIRECT_APP_OK: stable review IDs, 3 languages, 174 choices including N01, actual inline SVG, offline repeat, deadline');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
