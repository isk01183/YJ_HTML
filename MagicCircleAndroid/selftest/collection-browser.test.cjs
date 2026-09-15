const assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../app/src/main/assets');
const url=name=>pathToFileURL(path.join(root,name)).href;
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));await page.context().setOffline(true);
  // Deterministic transport fixture; packaged full-art rendering is checked separately.
  let requests=0,delay=0;
  await page.route('https://appassets.androidplatform.net/collection/**',async route=>{
   requests++;if(delay)await new Promise(r=>setTimeout(r,delay));
   await route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"><circle cx="512" cy="512" r="460" fill="none" stroke="gold" stroke-width="4"/></svg>'});
  });
  await page.goto(url('gallery.html')+'?lang=ko');
  assert.equal(await page.locator('.card').count(),173);
  await page.locator('[data-group=reference]').click();assert.equal(await page.locator('.card:visible').count(),118);
  await page.locator('#search').fill('C03');assert.equal(await page.locator('.card:visible').count(),1);
  await page.locator('[data-theme="ref-C03"]').click();
  assert.match(await page.locator('#hero-art img').getAttribute('src'),/C03.png$/);
  page.once('dialog',d=>d.accept());await page.locator('#delete-design').click();
  assert.equal(await page.locator('[data-theme="ref-C03"]').count(),0);
  await page.locator('#restore-designs').click();assert.equal(await page.locator('[data-theme="ref-C03"]').count(),1);
  await page.evaluate(()=>window.setGalleryState({selected:'',language:'ko',hidden:CircleDesigns.list.map(t=>t.id),media:[]}));
  assert.equal(await page.locator('#selection-status').textContent(),'선택된 항목이 없습니다','Upgrade must preserve explicitly disabled playback');
  assert.equal(await page.locator('.applied-badge:visible').count(),0);
  for(const lang of ['ko','ja','en']){
   await page.goto(url('collection_circle.html')+'?theme=ref-C03&lang='+lang+'&battery=78&run=123');
   assert.equal(await page.locator('#collection-art').evaluate(n=>n.complete&&n.naturalWidth>0),true,'Preload SVG during page loading, before the native 3-second startup window');
   assert.equal(await page.locator('html').evaluate(n=>n.classList.contains('running')),false,'Preloading must not start playback without native approval');
   await page.evaluate(()=>window.startChargingAnimation(5000));
   await page.waitForFunction(()=>document.documentElement.classList.contains('running'));
   assert.equal(await page.locator('html').getAttribute('lang'),lang);
   assert.equal(await page.locator('[data-level]').textContent(),'78');
   const before=requests;assert.equal(await page.evaluate(()=>window.startChargingAnimation()),true);assert.equal(requests,before,'Retries must not reload or restart');
   assert.equal(await page.locator('#collection-art').evaluate(n=>getComputedStyle(n).transform),'none','No whole-image spin/zoom');
   const end=await page.locator('.scene').evaluate(n=>n.getAnimations()[0].effect.getComputedTiming().endTime/n.getAnimations()[0].playbackRate);
   assert.ok(end<=5000);
   await page.evaluate(()=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=7000;}));
   assert.equal(await page.locator('.scene').evaluate(n=>getComputedStyle(n).opacity),'0');
  }
  const before=requests;
  await page.goto(url('collection_circle.html')+'?theme=ref-U04&battery=42');
  assert.equal(await page.locator('.artwork .readout [data-level]').textContent(),'42','Reserved mockup center must show actual battery data');
  await page.goto(url('collection_circle.html')+'?theme=ref-C99');
  assert.equal(await page.evaluate(()=>window.startChargingAnimation()),false);assert.equal(requests,before+1);
  delay=500;
  await page.goto(url('collection_circle.html')+'?theme=ref-C03&run=124',{waitUntil:'domcontentloaded'});
  await page.evaluate(()=>window.startChargingAnimation(300));
  await page.waitForFunction(()=>window.chargingCollectionError===true);
  assert.equal(await page.locator('html').evaluate(n=>n.classList.contains('running')),false,'Late image must not reopen expired sequence');
  for(const width of [320,800,1440]){
   await page.setViewportSize({width,height:900});await page.goto(url('gallery.html')+'?lang=en');
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  }
  assert.deepEqual(errors,[]);console.log('COLLECTION_BROWSER_OK: 173 choices, 118 filter, removal/restore, 3 languages, bounded replay, offline SVG route');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
