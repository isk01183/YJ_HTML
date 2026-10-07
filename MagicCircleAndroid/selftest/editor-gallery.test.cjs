const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
(async()=>{const b=await chromium.launch({channel:'msedge',headless:true});try{
 const p=await b.newPage({viewport:{width:412,height:915}});const actions=[],errors=[];
 p.on('request',r=>{if(r.url().startsWith('magiccircle://'))actions.push(r.url())});p.on('pageerror',e=>errors.push(e.message));
 await p.goto(pathToFileURL(path.resolve(__dirname,'../app/src/main/assets/gallery.html')).href+'?lang=ko');
 await p.evaluate(()=>setGalleryState({selected:'classic',language:'ko',enabled:true,hidden:[],media:[]}));
 assert.equal(await p.locator('#home-screen button').count(),3);
 await p.locator('#open-create').click();await p.locator('[data-create-purpose="charging"]').click();
 await p.waitForTimeout(150);assert(actions.includes('magiccircle://create?purpose=charging'));
 assert.equal(await p.locator('#create-screen-dialog').isVisible(),false);
 await p.locator('#open-charging').click();
 assert.equal(await p.locator('#edit-information').isVisible(),true);
 await p.locator('#edit-information').click();await p.waitForTimeout(150);assert(actions.includes('magiccircle://edit?theme=classic'));
 await p.locator('[data-duration="3000"]').click();await p.waitForTimeout(150);assert(actions.includes('magiccircle://duration?ms=3000'));
 await p.evaluate(()=>setGalleryState({selected:'classic',language:'ko',enabled:true,hidden:[],media:[],durationMs:3000}));
 assert.equal(await p.locator('#preview').innerText(),'▷ 3초 미리보기');
 const legacy='10000000-1111-4111-8111-111111111111',normal='20000000-1111-4111-8111-111111111111',material='30000000-1111-4111-8111-111111111111';
 const scene='scene-40000000-1111-4111-8111-111111111111';
 const state={selected:'classic',language:'ko',enabled:true,hidden:[],tabs:[],screen:'charging',media:[
   {id:legacy,name:'Legacy import',mime:'image/png',url:'https://appassets.androidplatform.net/media/'+legacy},
   {id:normal,name:'Normal import',mime:'image/jpeg',url:'https://appassets.androidplatform.net/media/'+normal,editorOnly:false},
   {id:material,name:'Editor material',mime:'image/png',url:'https://appassets.androidplatform.net/media/'+material,editorOnly:true}
 ],scenes:[{id:scene,name:'Saved scene',purpose:'CHARGING',layers:[{mediaId:material}]}]};
 await p.evaluate(value=>setGalleryState(value),state);
 assert.equal(await p.locator('[data-theme="'+material+'"]').count(),0,'Editor materials must not be standalone charging designs');
 assert.equal(await p.locator('#hide-editor-material').isVisible(),false,'Built-in designs cannot become editor materials');
 await p.locator('[data-theme="'+scene+'"]').click();
 assert.equal(await p.locator('#hide-editor-material').isVisible(),false,'Saved scenes cannot become editor materials');
 await p.locator('[data-theme="'+legacy+'"]').click();
 assert.equal(await p.locator('#hide-editor-material').innerText(),'편집 재료로 숨기기');
 const chargingRequest=p.waitForEvent('request',{predicate:r=>r.url().startsWith('magiccircle://editor-material')});
 await p.locator('#hide-editor-material').click();assert.equal((await chargingRequest).url(),'magiccircle://editor-material?theme='+legacy);
 assert.equal(await p.locator('[data-theme="'+legacy+'"]').count(),1,'Wait for native confirmation before hiding');
 await p.evaluate(value=>setGalleryState({...value,busy:true}),state);
 assert.equal(await p.locator('#hide-editor-material').isDisabled(),true);
 await p.evaluate(value=>setGalleryState({...value,screen:'wallpaper',scenes:[{...value.scenes[0],purpose:'WALLPAPER'}]}),state);
 assert.equal(await p.locator('[data-wallpaper-theme="'+material+'"]').count(),0,'Editor materials must not be standalone wallpapers');
 assert.equal(await p.locator('[data-editor-material]').count(),2,'Only ordinary imported images offer hiding');
 assert.equal(await p.locator('[data-wallpaper-theme="'+scene+'"]').count(),1,'Scenes retain their editor material layers');
 const wallpaperRequest=p.waitForEvent('request',{predicate:r=>r.url().startsWith('magiccircle://editor-material')});
 await p.locator('[data-editor-material="'+normal+'"]').click();assert.equal((await wallpaperRequest).url(),'magiccircle://editor-material?theme='+normal);
 for(const [language,label] of [['en','Hide as editor material'],['ja','編集素材として非表示']]){
   await p.evaluate(({state,language})=>setGalleryState({...state,screen:'wallpaper',language}),{state,language});
   assert.equal(await p.locator('[data-editor-material="'+legacy+'"]').innerText(),label);
 }
 await p.evaluate(value=>setGalleryState({...value,screen:'wallpaper',busy:true}),state);
 assert.equal(await p.locator('[data-editor-material]:enabled').count(),0);
 await p.evaluate(value=>setGalleryState({...value,media:value.media.map(item=>({...item,editorOnly:true}))}),state);
 assert.equal(await p.locator('[data-theme="'+legacy+'"],[data-theme="'+normal+'"]').count(),0,'Confirmed materials leave the gallery');
 assert.equal(await p.locator('[data-theme="'+scene+'"]').count(),1);
 assert.deepEqual(errors,[]);
 console.log('EDITOR_GALLERY_OK');
}finally{await b.close()}})().catch(e=>{console.error(e);process.exitCode=1});
