const {chromium}=require('playwright');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
const assert=require('node:assert/strict');
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});try {
 const page=await browser.newPage();
 for(const [file,theme] of [['magic_circle','classic'],['theme_circle','premium'],['theme_circle','layered']]) {
  await page.goto(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/'+file+'.html')).href+'?theme='+theme+'&editableInfo=1');
  assert.equal(await page.evaluate(()=>typeof window.prepareChargingAnimation),'function');
  assert.equal(await page.evaluate(()=>window.prepareChargingAnimation()),true);
  assert.equal(await page.evaluate(()=>document.documentElement.classList.contains('running')),false);
  await page.evaluate(()=>window.showChargeEditorFrame());
  for(const item of await page.locator('[data-level]').all()) assert.equal(await item.isVisible(),false);
  await page.evaluate(()=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=6300}));
  for(const item of await page.locator('.finish h2,.finish p').all()) assert.equal(await item.isVisible(),false,'Custom information must hide the original completion message');
  assert.equal(await page.evaluate(()=>document.getAnimations().filter(a=>a.effect.getTiming().iterations===Infinity).every(a=>a.playbackRate===1)),true);
 }
 console.log('CHARGE_INFO_BROWSER_OK');
} finally {await browser.close()}})().catch(e=>{console.error(e);process.exitCode=1});
