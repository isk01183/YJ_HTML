const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
(async()=>{const b=await chromium.launch({channel:'msedge',headless:true});try{
 const p=await b.newPage();const actions=[];p.on('request',r=>{if(r.url().startsWith('magiccircle://'))actions.push(r.url())});
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
 console.log('EDITOR_GALLERY_OK');
}finally{await b.close()}})().catch(e=>{console.error(e);process.exitCode=1});
