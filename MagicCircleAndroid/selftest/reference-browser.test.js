const assert=require('node:assert/strict');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('playwright');
const designs=require('../app/src/main/assets/circle-designs.js');
const assets=path.resolve(__dirname,'../app/src/main/assets');
const url=name=>pathToFileURL(path.join(assets,name)).href;
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.context().setOffline(true);
  await page.goto(url('gallery.html')+'?lang=ko');
  assert.equal(await page.locator('#hero-art image').getAttribute('href'),'artwork/sheet-1.jpg');
  for(const [sheet,count] of [[1,5],[2,30],[3,30],[4,30],[5,5]]){
   await page.locator('[data-group="sheet-'+sheet+'"]').click();
   assert.equal(await page.locator('.card:visible').count(),count);
   await page.locator('.card:visible').first().click();
   await page.locator('#apply').click();
   assert.equal(await page.locator('#apply').isDisabled(),true);
  }
  for(const language of ['ko','ja','en']){
   await page.locator('[data-language="'+language+'"]').click();
   await page.locator('#preview').click();
   const frame=page.frameLocator('iframe');
   await frame.locator('.reference-svg image').waitFor({state:'attached'});
   assert.equal(await frame.locator('html').getAttribute('lang'),language);
   assert.equal(await frame.locator('image').getAttribute('href'),'artwork/sheet-5.jpg');
   await page.locator('#close-preview').click();
  }
  await page.locator('[data-language="ko"]').click();
  await page.locator('[data-group="sheet-1"]').click();
  await page.locator('[data-theme="ref-1-01"]').click();
  await page.screenshot({path:path.join(__dirname,'../reference-gallery-mobile.png'),fullPage:true});
  // Inspect exactly the production renderer, not a second drawing/cropping implementation.
  await page.setViewportSize({width:1440,height:1000});
  for(const sheet of [1,2,3,4,5]){
   await page.goto(url('gallery.html'));
   await page.evaluate(sheet=>{
    document.head.insertAdjacentHTML('beforeend','<style>body{background:#000;color:#ddd}main{display:grid;grid-template-columns:repeat(5,1fr);gap:8px;padding:12px}article{min-width:0;border:1px solid #333}h2{font:14px sans-serif;text-align:center;height:32px;margin:8px}.art{aspect-ratio:1}</style>');
    document.body.innerHTML='<main>'+CircleDesigns.list.filter(t=>t.sheet===sheet).map(t=>'<article><div class="art">'+CircleDesigns.svg(t.id)+'</div><h2>'+t.id+' · '+t.names.ko+'</h2></article>').join('')+'</main>';
   },sheet);
   await page.evaluate(async()=>{await Promise.all([...document.querySelectorAll('image')].map(el=>new Promise((resolve,reject)=>{const image=new Image();image.onload=resolve;image.onerror=reject;image.src=el.getAttribute('href');})));});
   await page.screenshot({path:path.join(__dirname,'../reference-production-sheet-'+sheet+'.png'),fullPage:true});
  }
  assert.deepEqual(errors,[]);
  console.log('REFERENCE_BROWSER_OK: offline original images, 5 sheet filters, apply, trilingual preview, production contact sheets');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
