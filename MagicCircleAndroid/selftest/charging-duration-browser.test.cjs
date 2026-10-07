const {chromium}=require('playwright');
const assert=require('node:assert/strict'),path=require('node:path');
const {pathToFileURL}=require('node:url');
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});try{
 const page=await browser.newPage();page.setDefaultTimeout(5000);
 await page.route('https://appassets.androidplatform.net/**',r=>r.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><circle cx="32" cy="32" r="30" fill="red"/></svg>'}));
 for(const [file,query] of [['magic_circle','theme=classic'],['theme_circle','theme=raphael'],['collection_circle','theme=ref-A01'],['media_circle','media=11111111-1111-4111-8111-111111111111&mime=image/png']]) {
  for(const ms of [1000,3000,5000,7000]) {
   await page.goto(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/'+file+'.html')).href+'?'+query+'&run='+ms);
   await page.waitForFunction(()=>prepareChargingAnimation());
   assert.equal(await page.evaluate(()=>document.documentElement.classList.contains('running')),false);
   assert.equal(await page.evaluate(()=>startChargingAnimation(0)),false);
   assert.equal(await page.evaluate(ms=>startChargingAnimation(ms),ms),true);
   const timings=await page.evaluate(()=>document.getAnimations().map(a=>({end:a.effect.getComputedTiming().endTime,rate:a.playbackRate,infinite:a.effect.getTiming().iterations===Infinity})));
   for(const t of timings)if(t.infinite)assert.equal(t.rate,1);else assert.ok(t.end/t.rate<=ms+8,file+' exceeds deadline');
  }
 }
 console.log('CHARGING_DURATION_OK: 4 renderer pages x 4 durations; preparation does not play; infinite loops stay at original speed');
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
