const assert=require('node:assert/strict');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('playwright');
(async()=>{
  const browser=await chromium.launch({headless:true,channel:'msedge'});
  try{
    const page=await browser.newPage({viewport:{width:412,height:915}});
    await page.context().setOffline(true);page.setDefaultTimeout(5000);
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.goto(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/gallery.html')).href);
    const state={selected:'native-N01',language:'ko',enabled:true,media:[],hidden:[],tabs:[],readable:true};
    const w03Locator=page.locator('[data-theme="ref-W03"]'),w03Card=await w03Locator.elementHandle();
    await w03Locator.scrollIntoViewIfNeeded();
    await page.waitForFunction(()=>document.querySelector('[data-theme="ref-W03"] img')?.hasAttribute('src'));
    assert.match(await w03Card.$eval('img',image=>image.getAttribute('src')),/^data:image\/svg\+xml/,'Standalone card is preloaded');
    await w03Locator.click();
    assert.match(await page.locator('#hero-tag').textContent(),/브라우저 참고 미리보기/);
    assert.equal(await page.locator('#hero-art svg').count(),1,'Standalone browser keeps the SVG comparison');
    await page.evaluate(value=>setGalleryState(value),state);
    assert.equal(await w03Card.$eval('img',image=>image.getAttribute('src')),'https://appassets.androidplatform.net/generated/ref-W03.png','The same preloaded card must switch to generated native art');
    await page.locator('[data-theme="ref-W03"]').click();
    assert.equal(await page.locator('#hero-art img').getAttribute('src'),'https://appassets.androidplatform.net/generated/ref-W03.png');
    await page.locator('[data-theme="ref-R01"]').scrollIntoViewIfNeeded();
    await page.waitForFunction(()=>document.querySelector('[data-theme="ref-R01"] img')?.hasAttribute('src'));
    assert.equal(await page.locator('[data-theme="ref-R01"] img').getAttribute('src'),'https://appassets.androidplatform.net/generated/ref-R01.png');
    await page.locator('[data-theme="native-N01"]').click();
    for(const [target,label] of [['home','배경화면 변경'],['lock','잠금화면 변경']]){
      const button=page.locator('#wallpaper-'+target);
      assert.equal(await button.textContent(),label);
      assert.ok((await button.boundingBox()).height>=48);
      await button.click();
      assert.ok((await page.locator('#wallpaper-dialog').boundingBox()).height<600,'Two wallpaper choices should use a compact dialog');
      assert.deepEqual(await page.locator('[data-wallpaper-theme]').evaluateAll(items=>items.map(item=>item.dataset.wallpaperTheme)),['ref-W03','ref-R01']);
      for(const theme of ['ref-W03','ref-R01']){
        const request=page.waitForEvent('request',{predicate:r=>r.url().startsWith('magiccircle://wallpaper')});
        await page.locator('[data-wallpaper-theme="'+theme+'"]').click();
        assert.equal((await request).url(),'magiccircle://wallpaper?theme='+theme+'&target='+target);
        if(theme==='ref-W03')await button.click();
      }
      assert.equal(await page.locator('#apply').isDisabled(),true,'Wallpaper chooser must not change charging selection');
      assert.equal(await page.evaluate(()=>document.activeElement.id),'wallpaper-'+target);
    }
    await page.evaluate(value=>setGalleryState({...value,hidden:['ref-W03']}),state);
    await page.locator('#wallpaper-home').click();
    assert.deepEqual(await page.locator('[data-wallpaper-theme]').evaluateAll(items=>items.map(item=>item.dataset.wallpaperTheme)),['ref-R01']);
    await page.locator('#close-wallpaper').click();
    await page.evaluate(value=>setGalleryState({...value,hidden:['ref-W03','ref-R01']}),state);
    assert.equal(await page.locator('#wallpaper-home').isDisabled(),true);
    await page.evaluate(value=>setGalleryState({...value,busy:true}),state);
    assert.equal(await page.locator('#wallpaper-lock').isDisabled(),true);
    for(const [language,label] of [['ja','ホーム画面を変更'],['en','Change wallpaper']]){
      await page.evaluate(({state,language})=>setGalleryState({...state,language}),{state,language});
      assert.equal(await page.locator('#wallpaper-home').textContent(),label);
    }
    assert.deepEqual(errors,[]);
    console.log('WALLPAPER_BROWSER_OK: fixed targets, W03/R01 only, availability, charging independence, focus, languages');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
