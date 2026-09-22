const assert=require('node:assert/strict');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('playwright');

const assets=path.resolve(__dirname,'../app/src/main/assets');
const gallery=pathToFileURL(path.join(assets,'gallery.html')).href;
const tabId='11111111-1111-4111-8111-111111111111';
const secondTabId='22222222-2222-4222-8222-222222222222';
const activeIds=('N01 C11 C12 C15 C16 C21 C22 C23 C26 C28 C30 W01 W02 W03 W04 W05 F01 F02 F03 F04 F05 A14 R01 crimson-abyss healing-wings snowflake sakura-seal spirit-bloom chronos-gears fate-compass twilight-balance moon raphael layered premium basic blue gold silver violet cyan core minimal').split(' ').map(id=>id==='N01'?'native-N01':/^[A-Z]\d\d$/.test(id)?'ref-'+id:id);

const magicRequest=async(page,click)=>{
  const request=page.waitForEvent('request',{predicate:r=>r.url().startsWith('magiccircle://')});
  await click();
  return (await request).url();
};

(async()=>{
  const browser=await chromium.launch({headless:true,channel:'msedge'});
  try{
    const page=await browser.newPage({viewport:{width:412,height:915}});
    await page.context().setOffline(true);
    page.setDefaultTimeout(5000);
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    await page.goto(gallery+'?lang=ko');
    const builtins=await page.evaluate(()=>[nativeDesign,...CircleDesigns.list,...ReferenceDesigns.list]);
    const hidden=builtins.map(x=>x.id).filter(id=>!activeIds.includes(id));
    const tabs=[{id:tabId,name:'<b>내 도안</b>',members:['ref-C03','ref-W03']}];
    const state={selected:'native-N01',language:'ko',enabled:true,media:[],readable:true,hidden,tabs,activationRevision:1};
    await page.evaluate(value=>window.setGalleryState(value),state);

    assert.equal(await page.locator('#design-grid .card:visible').count(),43);
    assert.equal(await page.locator('[data-tab-id] b').count(),0,'Tab names must be plain text');
    assert.equal(await page.locator('[data-group]').count(),1,'Fixed category tabs must be removed');
    for(const selector of ['#create-tab','#manage-inactive','#delete-design','#preview','#apply']){
      assert.ok((await page.locator(selector).boundingBox()).height>=48,selector+' needs a 48px touch target');
    }
    await page.locator('[data-tab-id="'+tabId+'"]').click();
    assert.equal(await page.locator('#design-grid .card:visible').count(),1,'Inactive members stay out of active tabs');
    await page.locator('[data-tab-id="'+tabId+'"]').focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.evaluate(()=>document.activeElement?.dataset.tabId),tabId,'Enter on a tab must keep keyboard focus on that tab');
    await page.locator('#search').focus();
    await page.evaluate(value=>window.setGalleryState(value),state);
    assert.equal(await page.evaluate(()=>document.activeElement?.id),'search','Refreshing tabs must not steal unrelated focus');

    await page.locator('#manage-inactive').click();
    assert.equal(await page.locator('#inactive-grid .card:visible').count(),131);
    assert.equal(await page.locator('#inactive-dialog').getAttribute('aria-label'),'비활성 도안 관리');
    await page.locator('#inactive-search').fill('C03');
    assert.equal(await page.locator('#inactive-grid .card:visible').count(),1,'Inactive search must include design codes');
    await page.locator('#inactive-search').fill('C03 · 라파엘 · 궤도');
    assert.equal(await page.locator('#inactive-grid .card:visible').count(),1,'Inactive search must include translated names');
    assert.ok(await page.locator('[data-inactive-theme="ref-C03"] .art img').getAttribute('src'),'Inactive designs need static previews');
    assert.equal(await magicRequest(page,()=>page.locator('[data-enable-theme="ref-C03"]').click()),'magiccircle://enable?theme=ref-C03');
    assert.equal(await page.locator('[data-inactive-theme="ref-C03"]').count(),1,'Native UI must wait for saved state');
    await page.evaluate(value=>window.setGalleryState(value),{...state,hidden:hidden.filter(id=>id!=='ref-C03')});
    assert.equal(await page.locator('[data-inactive-theme="ref-C03"]').count(),0);
    await page.locator('#close-inactive').click();
    assert.equal(await page.evaluate(()=>document.activeElement?.id),'manage-inactive','Closing a dialog must restore focus');
    await page.locator('[data-tab-id="'+tabId+'"]').click();
    assert.equal(await page.locator('#design-grid .card:visible').count(),2,'Reactivated members return to their tabs');

    await page.locator('#create-tab').click();
    await page.locator('#tab-name').fill('   ');await page.locator('#save-tab').click();
    assert.notEqual(await page.locator('#tab-error').textContent(),'');
    await page.locator('#tab-name').fill('😀'.repeat(41));await page.locator('#save-tab').click();
    assert.notEqual(await page.locator('#tab-error').textContent(),'','41 code points must be rejected');
    await page.locator('#tab-name').fill('😀'.repeat(40));
    assert.equal(decodeURIComponent((await magicRequest(page,()=>page.locator('#save-tab').click())).split('name=')[1]),'😀'.repeat(40));
    assert.equal(await page.locator('[data-tab-id]').count(),1,'Create waits for Android state');
    await page.locator('#close-tab-editor').click();
    await page.locator('#create-tab').click();await page.locator('#tab-name').fill('<b>내 도안</b>');await page.locator('#save-tab').click();
    assert.notEqual(await page.locator('#tab-error').textContent(),'','Duplicate names must be rejected before saving');
    await page.locator('#close-tab-editor').click();

    await page.locator('[data-tab-id="'+tabId+'"]').click();await page.locator('#rename-tab').click();
    await page.locator('#tab-name').fill('즐겨찾기');
    assert.equal(await magicRequest(page,()=>page.locator('#save-tab').click()),'magiccircle://tab-rename?id='+tabId+'&name='+encodeURIComponent('즐겨찾기'));
    await page.evaluate(value=>window.setGalleryState(value),{...state,hidden:hidden.filter(id=>id!=='ref-C03'),tabs:[{...tabs[0],name:'즐겨찾기'}]});
    assert.equal(await page.locator('[data-tab-id="'+tabId+'"]').textContent(),'즐겨찾기 · 2');
    await page.locator('#close-tab-editor').click();

    await page.locator('#manage-tab-members').click();
    assert.equal(await page.locator('[data-tab-member-theme="ref-C11"]').getAttribute('aria-pressed'),'false');
    assert.equal(await magicRequest(page,()=>page.locator('[data-tab-member-theme="ref-C11"]').click()),'magiccircle://tab-member?id='+tabId+'&theme=ref-C11&member=1');
    assert.equal(await page.locator('[data-tab-member-theme="ref-C11"]').getAttribute('aria-pressed'),'false','Membership waits for Android state');
    assert.equal(await magicRequest(page,()=>page.locator('[data-tab-member-theme="ref-W03"]').click()),'magiccircle://tab-member?id='+tabId+'&theme=ref-W03&member=0');
    assert.equal(await page.locator('[data-tab-member-theme="ref-W03"]').getAttribute('aria-pressed'),'true','Removal also waits for Android state');
    await page.locator('#close-tab-members').click();
    const multiTabs=[{id:tabId,name:'즐겨찾기',members:['ref-C03','ref-W03','ref-C11']},{id:secondTabId,name:'빈 탭',members:['ref-C11']}];
    await page.evaluate(value=>window.setGalleryState(value),{...state,hidden:hidden.filter(id=>id!=='ref-C03'),tabs:multiTabs});
    await page.locator('[data-tab-id="'+tabId+'"]').click();assert.equal(await page.locator('#design-grid .card:visible').count(),3);
    await page.locator('[data-tab-id="'+secondTabId+'"]').click();assert.equal(await page.locator('#design-grid .card:visible').count(),1,'One design may belong to multiple tabs');
    await page.evaluate(value=>window.setGalleryState(value),{...state,tabs:[multiTabs[0],{...multiTabs[1],members:[]}]});
    await page.locator('[data-tab-id="'+secondTabId+'"]').click();assert.equal(await page.locator('#design-grid .card:visible').count(),0,'Empty tabs must remain');
    await page.evaluate(value=>window.setGalleryState(value),{...state,busy:true,tabs:[multiTabs[0],{...multiTabs[1],members:[]}]});
    assert.equal(await page.locator('#design-grid .card:visible').count(),0,'State refreshes must not focus a design outside an empty tab');
    assert.equal(await page.locator('#hero-art img,#hero-art svg').count(),0);
    await page.evaluate(value=>window.setGalleryState(value),{...state,tabs:[multiTabs[0],{...multiTabs[1],members:[]}]});

    await page.locator('[data-tab-id="'+tabId+'"]').click();
    let nativeConfirmCount=0;
    const suppressConfirm=async dialog=>{nativeConfirmCount++;await dialog.dismiss();};
    page.on('dialog',suppressConfirm);
    assert.equal(await magicRequest(page,()=>page.locator('#delete-tab').click()),'magiccircle://tab-delete?id='+tabId);
    page.off('dialog',suppressConfirm);
    assert.equal(nativeConfirmCount,0,'Native deletion must reach Android even when WebView suppresses JS confirm');
    assert.equal(await page.locator('[data-tab-id="'+tabId+'"]').count(),1,'Delete waits for Android state');
    await page.evaluate(value=>window.setGalleryState(value),{...state,tabs:multiTabs});
    assert.equal(await page.locator('[data-tab-id="'+tabId+'"]').count(),1,'Canceled native deletion keeps the authoritative tab state');
    assert.equal(await page.locator('#delete-tab').isDisabled(),false,'Cancellation must leave the library available');
    await page.evaluate(value=>window.setGalleryState(value),{...state,busy:true,tabs:multiTabs});
    assert.equal(await page.locator('#delete-tab').isDisabled(),true,'Approved native mutation stays busy until saved state arrives');
    await page.evaluate(value=>window.setGalleryState(value),{...state,tabs:[multiTabs[1]]});
    assert.equal(await page.locator('[data-tab-id="'+tabId+'"]').count(),0,'Confirmed native saved state removes the tab');

    // A standalone browser still owns its own explicit confirmation.
    const standalone=await browser.newPage();
    await standalone.goto(gallery+'?lang=en');
    await standalone.evaluate(({id})=>{tabs=[{id,name:'Browser tab',members:[]}];chooseGroup(id);},{id:tabId});
    standalone.once('dialog',async dialog=>{assert.match(dialog.message(),/designs will not be deleted/);await dialog.dismiss();});
    await standalone.locator('#delete-tab').click();
    assert.equal(await standalone.locator('[data-tab-id="'+tabId+'"]').count(),1,'Browser cancellation must not delete');
    standalone.once('dialog',async dialog=>{await dialog.accept();});
    await standalone.locator('#delete-tab').click();
    assert.equal(await standalone.locator('[data-tab-id="'+tabId+'"]').count(),0,'Browser approval deletes only the tab');
    await standalone.close();

    const allButNative=builtins.map(x=>x.id).filter(id=>id!=='native-N01');
    await page.evaluate(value=>window.setGalleryState(value),{...state,hidden:allButNative,tabs:[]});
    assert.equal(await magicRequest(page,()=>page.locator('#delete-design').click()),'magiccircle://disable?theme=native-N01');
    assert.equal(await page.locator('#design-grid .card:visible').count(),1,'Disable waits for Android state');
    await page.evaluate(value=>window.setGalleryState(value),{...state,selected:'',hidden:builtins.map(x=>x.id),tabs:[],migrationNotice:'selection_changed'});
    assert.equal(await page.locator('#design-grid .card:visible').count(),0,'The final active design may be disabled');
    assert.equal(await page.locator('#apply').isDisabled(),true);
    for(const [language,pattern] of [['ko',/선택된 도안이 없습니다/],['ja',/選択中のデザインはありません/],['en',/No design is selected/]]){
      await page.evaluate(value=>window.setGalleryState(value),{...state,language,selected:'',hidden:builtins.map(x=>x.id),tabs:[],migrationNotice:'selection_changed'});
      assert.match(await page.locator('#library-notice').textContent(),pattern,'An empty selection must not claim another design is active');
    }
    await page.evaluate(value=>window.setGalleryState(value),{...state,migrationNotice:''});
    assert.equal(await page.locator('#library-notice').isHidden(),true,'Cleared native notices must disappear');

    const mediaId='12345678-90ab-4cde-8123-456789abcdef';
    const media=[{id:mediaId,name:'내 업로드',mime:'image/png',url:'https://appassets.androidplatform.net/media/'+mediaId}];
    await page.evaluate(value=>window.setGalleryState(value),{...state,selected:mediaId,hidden,media,busy:false,tabs:[]});
    assert.equal(await magicRequest(page,()=>page.locator('#delete-design').click()),'magiccircle://delete?theme='+mediaId,'Uploads are deleted, not disabled');
    await page.evaluate(value=>window.setGalleryState(value),{...state,busy:true,tabs:[]});
    assert.equal(await page.locator('#delete-design').isDisabled(),true);
    assert.equal(await page.locator('#create-tab').isDisabled(),true);
    await page.locator('#manage-inactive').click();
    assert.equal(await page.locator('[data-enable-theme]').first().isDisabled(),true,'Busy state disables saved mutations');
    await page.locator('#close-inactive').click();

    for(const [lang,label] of [['ko','비활성 도안 관리'],['ja','非表示デザインを管理'],['en','Manage inactive designs']]){
      await page.evaluate(({state,lang})=>window.setGalleryState({...state,language:lang}),{state,lang});
      assert.equal(await page.locator('#manage-inactive').textContent(),label);
    }
    assert.deepEqual(errors,[]);
    console.log('LIBRARY_BROWSER_OK: 43 active, 131 inactive, tabs, validation, round trips, busy state, 3 languages');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
