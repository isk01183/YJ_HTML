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
    const requests=[];page.on('request',request=>{if(request.url().startsWith('magiccircle://'))requests.push(request.url());});
    await page.goto(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/gallery.html')).href+'?lang=ko');
    const media=['image/jpeg','image/png','image/gif'].map((mime,i)=>({id:'0000000'+i+'-1111-4111-8111-111111111111',mime,name:'내 파일 '+i,url:'https://appassets.androidplatform.net/media/0000000'+i+'-1111-4111-8111-111111111111'}));
    const state={selected:'native-N01',language:'ko',enabled:true,media,hidden:[],tabs:[],readable:true};
    await page.evaluate(value=>setGalleryState(value),state);
    assert.equal(await page.locator('#home-screen').isVisible(),true);
    assert.equal(await page.locator('#charging-screen').isVisible(),false);
    assert.equal(await page.locator('#charging-actions').isVisible(),false);
    assert.equal(await page.locator('#home-screen button').count(),3);
    await page.screenshot({path:path.resolve(__dirname,'../docs/v114-home.png')});
    await page.locator('#open-wallpapers').click();
    assert.equal(await page.locator('#wallpaper-screen').isVisible(),true);
    assert.equal(await page.locator('[data-wallpaper-theme]').count(),5,'Two designs plus JPG, PNG, GIF');
    assert.equal(await page.locator('#import-wallpaper').isEnabled(),true);
    const importing=page.waitForEvent('request',{predicate:r=>r.url()==='magiccircle://import-wallpaper'});
    await page.locator('#import-wallpaper').click();await importing;
    for(const target of ['home','lock']){
      await page.locator('[data-wallpaper-target="'+target+'"]').click();
      for(const theme of ['ref-W03','ref-R01',...media.map(x=>x.id)]){
        const request=page.waitForEvent('request',{predicate:r=>r.url().startsWith('magiccircle://wallpaper')});
        await page.locator('[data-wallpaper-theme="'+theme+'"]').click();
        assert.equal((await request).url(),'magiccircle://wallpaper?theme='+theme+'&target='+target);
      }
    }
    assert.equal(await page.evaluate(()=>selected),'native-N01','Wallpaper changes must not alter charging selection');
    await page.evaluate(value=>setGalleryState({...value,screen:'wallpaper',busy:true}),state);
    assert.equal(await page.locator('#import-wallpaper').isDisabled(),true);
    assert.equal(await page.locator('[data-wallpaper-theme]:enabled').count(),0);
    await page.evaluate(value=>setGalleryState({...value,screen:'wallpaper',hidden:['ref-W03']}),state);
    assert.equal(await page.locator('[data-wallpaper-theme="ref-W03"]').count(),0);
    assert.equal(await page.evaluate(()=>navigateBack()),true);
    assert.equal(await page.locator('#home-screen').isVisible(),true);
    assert.equal(await page.evaluate(()=>navigateBack()),false);
    await page.locator('#open-charging').click();
    assert.equal(await page.locator('#charging-actions').isVisible(),true);
    await page.locator('#manage-inactive').click();
    assert.equal(await page.evaluate(()=>navigateBack()),true);
    assert.equal(await page.locator('#inactive-dialog').isVisible(),false);
    assert.equal(await page.locator('#charging-screen').isVisible(),true);
    await page.locator('#back-home').click();
    for(const [language,label] of [['ja','壁紙 / ロック画面'],['en','Wallpaper / Lock screen']]){
      await page.evaluate(({state,language})=>setGalleryState({...state,screen:'home',language}),{state,language});
      assert.equal(await page.locator('#open-wallpapers strong').textContent(),label);
    }
    for(const width of [320,412,800]){
      await page.setViewportSize({width,height:915});
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'No horizontal overflow');
    }
    assert.deepEqual(errors,[]);
    console.log('WALLPAPER_BROWSER_OK: home menus, JPG/PNG/GIF, targets, charging independence, back, availability, languages');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
