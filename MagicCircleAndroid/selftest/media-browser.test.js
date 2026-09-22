const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {pathToFileURL} = require('node:url');
const {chromium} = require('playwright');
const sharp = require('sharp');
const designs = require('../app/src/main/assets/circle-designs.js');
const builtins = [{id:'native-N01'}].concat(designs.list,require('../app/src/main/assets/collection-catalog.js').list);
const assets = path.resolve(__dirname, '../app/src/main/assets');
const file = name => pathToFileURL(path.join(assets, name)).href;
const id = '12345678-90ab-4cde-8123-456789abcdef';
const mediaUrl = 'https://appassets.androidplatform.net/media/' + id;
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a6S8AAAAASUVORK5CYII=', 'base64');
// Real two-frame GIF: one red frame then one blue, one second each, loop forever.
const gif = Buffer.from('47494638396101000100800000ff00000000ff21ff0b4e45545343415045322e30030100000021f90400640000002c000000000100010000020244010021f90400640000002c00000000010001000002024c01003b','hex');

(async () => {
  const browser = await chromium.launch({headless:true, channel:'msedge'});
  try {
    const page = await browser.newPage({viewport:{width:412,height:915}});
    await page.context().setOffline(true);
    const errors=[]; page.on('pageerror', error=>errors.push(error.message));
    await page.route('https://appassets.androidplatform.net/media/**', route => route.fulfill({contentType:'image/png',body:png}));
    await page.goto(file('gallery.html')+'?lang=ko');
    assert.equal(await page.locator('#import-media').count(),1,'Users need an import action in the existing gallery');
    const media=[{id,name:'<img src=x onerror=alert(1)> 여행 사진.jpg',url:mediaUrl,mime:'image/jpeg'}];
    await page.evaluate(({id,media})=>window.setGalleryState({selected:id,language:'ko',enabled:true,hidden:['classic','moon'],media}),{id,media});
    assert.equal(await page.locator('.card:visible').count(),builtins.length-1);
    assert.equal(await page.locator('#hero-title').textContent(),media[0].name,'Imported names must remain plain text');
    assert.equal(await page.locator('#hero-title img').count(),0);
    assert.equal(await page.locator('#import-media').isEnabled(),true);
    assert.equal(await page.locator('#manage-inactive').isVisible(),true);
    assert.equal(await page.locator('#delete-design').isEnabled(),true);
    assert.equal(await page.locator('#hero-art img').getAttribute('src'),mediaUrl+'?thumb=1');
    await page.evaluate(({id,media})=>window.setGalleryState({selected:id,language:'ko',enabled:true,busy:true,hidden:['classic','moon'],media}),{id,media});
    assert.equal(await page.locator('#import-media').isDisabled(),true,'An in-flight import must disable duplicate operations');
    assert.equal(await page.locator('#delete-design').isDisabled(),true);
    await page.evaluate(({id,media})=>window.setGalleryState({selected:id,language:'ko',enabled:true,busy:false,hidden:['classic','moon'],media}),{id,media});
    await page.locator('#search').fill('여행');
    assert.equal(await page.locator('.card:visible').count(),1);
    // The native delete round trip removes the selected item. No stale preview may remain.
    await page.evaluate(hidden=>window.setGalleryState({selected:'',language:'ko',enabled:true,hidden,media:[]}),builtins.map(x=>x.id));
    assert.equal(await page.locator('.card:visible').count(),0);
    assert.equal(await page.locator('#preview').isDisabled(),true);
    assert.equal(await page.locator('#apply').isDisabled(),true);
    assert.equal(await page.locator('#delete-design').isDisabled(),true);
    assert.equal(await page.locator('#hero-art img,#hero-art svg').count(),0);
    // A returned Android state must immediately recover a valid selection after an empty library.
    await page.evaluate(()=>window.setGalleryState({selected:'classic',language:'en',enabled:true,hidden:[],media:[]}));
    await page.locator('[data-group="all"]').click(); await page.locator('#search').fill('');
    assert.equal(await page.locator('.card:visible').count(),builtins.length);
    assert.equal(await page.locator('#preview').isEnabled(),true);
    await page.evaluate(({id,media})=>window.setGalleryState({selected:id,language:'en',enabled:true,hidden:[],media}),{id,media});
    assert.equal(await page.locator('#hero-title').textContent(),media[0].name,'A newly imported selected file should be focused immediately');
    await page.evaluate(()=>window.setGalleryState({selected:'',language:'ko',enabled:true,readable:false,hidden:[],media:[]}));
    assert.equal(await page.locator('#import-media').isDisabled(),true,'A damaged manifest must not be overwritten');
    assert.equal(await page.locator('#preview').isDisabled(),true);
    assert.equal(await page.locator('.applied-badge:visible').count(),0,'Unreadable selection must not appear as classic');
    assert.match(await page.locator('#hero-description').textContent(),/목록을 읽을 수 없습니다/);
    await page.evaluate(({id,media})=>window.setGalleryState({selected:id,language:'en',enabled:true,readable:true,hidden:[],media}),{id,media});
    for(const width of [320,412,800]) {
      await page.setViewportSize({width,height:915});
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'Gallery must fit '+width);
    }
    // Browser deletion uses the real same collection filter, without pretending to save app data.
    await page.goto(file('gallery.html')+'?lang=ko');
    page.once('dialog', dialog=>dialog.accept()); await page.locator('#delete-design').click();
    assert.equal(await page.locator('[data-theme="native-N01"]').isVisible(),false);
    await page.locator('#manage-inactive').click();
    await page.locator('[data-enable-theme="native-N01"]').click();
    await page.locator('#close-inactive').click();
    assert.equal(await page.locator('[data-theme="native-N01"]').isVisible(),true);
    for(const language of ['ko','ja','en']) {
      await page.goto(file('media_circle.html')+'?media='+id+'&lang='+language+'&battery=78&mime=image%2Fjpeg');
      await page.evaluate(()=>window.startChargingAnimation());
      await page.waitForFunction(()=>document.getElementById('media-art')?.naturalWidth>0);
      assert.equal(await page.evaluate(()=>window.startChargingAnimation()),true);
      assert.equal(await page.locator('html').getAttribute('lang'),language);
      assert.equal(await page.locator('[data-level]').textContent(),'78');
      await page.evaluate(()=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=3500;}));
      assert.equal(await page.locator('.scene').evaluate(n=>getComputedStyle(n).opacity),'1');
      assert.equal(await page.locator('#media-art').evaluate(n=>getComputedStyle(n).objectFit),'contain');
      await page.evaluate(()=>document.getAnimations().forEach(a=>a.currentTime=7000));
      assert.equal(await page.locator('.scene').evaluate(n=>getComputedStyle(n).opacity),'0');
    }
    await page.goto(file('media_circle.html')+'?media=../../secret&lang=ko');
    assert.equal(await page.evaluate(()=>window.startChargingAnimation()),false,'Invalid imported ID must not start');
    assert.equal(await page.locator('#media-art').getAttribute('src'),null);
    for(const filename of ['media_circle.html','theme_circle.html','magic_circle.html']) {
      await page.goto(file(filename)+'?media='+id+'&theme=raphael&lang=ko');
      await page.evaluate(()=>window.startChargingAnimation(5000));
      await page.waitForFunction(()=>document.documentElement.classList.contains('running'));
      const duration=await page.locator('.scene,#scene').evaluate(n=>Math.max(...n.getAnimations().map(a=>a.effect.getComputedTiming().endTime/a.playbackRate)));
      assert.ok(duration<=5000,'Fade must complete within the remaining native deadline: '+filename+' '+duration);
    }
    await page.unroute('https://appassets.androidplatform.net/media/**');
    await page.route('https://appassets.androidplatform.net/media/**',route=>route.fulfill({contentType:'image/gif',body:gif}));
    const redOrBlue=async()=>{
      const screenshot=await page.locator('#media-art').screenshot();
      const {data,info}=await sharp(screenshot).raw().toBuffer({resolveWithObject:true});
      const offset=(Math.floor(info.height/2)*info.width+Math.floor(info.width/2))*info.channels;
      return data[offset]>data[offset+2]?'red':'blue';
    };
    for(let run=1;run<=2;run++) {
      await page.goto(file('media_circle.html')+'?media='+id+'&lang=ko&mime=image%2Fgif&run='+run);
      await page.evaluate(()=>window.startChargingAnimation());
      await page.waitForFunction(()=>document.documentElement.classList.contains('running'));
      await page.addStyleTag({content:'.scene{animation:none!important;opacity:1!important}'});
      assert.equal(await redOrBlue(),'red','Every newly loaded GIF must start from frame one');
      let advanced=false;
      for(let attempt=0;attempt<12;attempt++) {
        if(await redOrBlue()==='blue'){advanced=true;break;}
        await new Promise(resolve=>setTimeout(resolve,100));
      }
      assert.ok(advanced,'GIF must really animate, not remain a static poster');
      await page.evaluate(()=>window.startChargingAnimation());
      assert.equal(await redOrBlue(),'blue','Readiness retries must not restart a running GIF');
    }
    assert.deepEqual(errors,[]);
    console.log('MEDIA_BROWSER_OK: import UI, safe names, disable/delete/enable, empty library, 3 languages, 7s offline media');
  } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
