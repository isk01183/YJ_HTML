let selected='native-N01',focused='native-N01',native=false,initialized=false,enabled=false,activeGroup='all';
let language=MagicI18n.normalize(new URLSearchParams(location.search).get('lang')||navigator.language.split('-')[0]);
let hidden=new Set(),media=[],tabs=[],busy=false,readable=true,migrationNotice=null;
let screen='home',wallpaperTarget='home';
let scenes=[],drafts=[],durationMs=7000;
const createButton=document.createElement('button');createButton.id='open-create';createButton.className='destination';
createButton.innerHTML='<span class="symbol" aria-hidden="true">⊞</span><strong data-i18n="createScreen"></strong><span data-i18n="createHint"></span>';
document.querySelector('.home-menu').appendChild(createButton);
const characterButton=document.createElement('button');characterButton.id='open-characters';characterButton.className='destination';
characterButton.innerHTML='<span class="symbol" aria-hidden="true">♧</span><strong data-i18n="characterCreate"></strong><span data-i18n="characterHint"></span>';
document.querySelector('.home-menu').appendChild(characterButton);
characterButton.onclick=()=>{if(native&&!busy)location.href='magiccircle://characters';};
const grid=document.getElementById('design-grid');
const nativeDesign={id:'native-N01',code:'N01',group:'signature',color:'#e4c889'};
const builtins=[nativeDesign,...CircleDesigns.list,...ReferenceDesigns.list];
const builtinIds=new Set(builtins.map(design=>design.id));
const nativeIcon='data:image/svg+xml;charset=utf-8,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="#080d18"/><circle cx="50" cy="50" r="36" fill="none" stroke="#e4c889" stroke-width="1.5"/><circle cx="50" cy="50" r="26" fill="none" stroke="#72cbe6" stroke-width="1"/><path d="M50 16 70 66 24 38h52L30 66Z" fill="none" stroke="#e4c889" stroke-width="1.5"/><text x="50" y="53" fill="#f9e8c3" font-size="12" text-anchor="middle">N01</text></svg>');
const text=(key,values)=>MagicI18n.t(key,language,values);
const visibleDesigns=()=>readable?builtins.filter(theme=>!hidden.has(theme.id)).concat(media,scenes.filter(s=>s.purpose==='CHARGING')):[];
const tab=id=>tabs.find(value=>value.id===id);
const translatedTheme=id=>{
    const scene=scenes.find(s=>s.id===id);
    if(scene)return {...scene,desc:text('createHint'),color:'#d5c5a2',group:'uploads',thumb:scene.url};
    const custom=media.find(theme=>theme.id===id);
    return custom?{...custom,desc:text(custom.mime==='image/gif'?'mediaAnimated':'mediaStill'),color:'#d5c5a2',group:'uploads'}
        :id==='native-N01'?{...nativeDesign,name:text('nativeName'),desc:text('nativeDescription')}
        :MagicI18n.theme(ReferenceDesigns.get(id)||CircleDesigns.get(id),language);
};
const directCode=id=>{const code=ReferenceDesigns.get(id)?.code;return DirectCircles.ids.includes(code)?code:null;};
const nativeArtwork=id=>id==='ref-W03'||id==='ref-R01';
const generatedArtwork=id=>'https://appassets.androidplatform.net/generated/'+id+'.png';
const designCode=id=>translatedTheme(id)?.code||id;
const artUrl=id=>{
    const scene=scenes.find(s=>s.id===id);if(scene)return scene.url;
    const custom=media.find(theme=>theme.id===id),code=directCode(id);
    return id==='native-N01'?nativeIcon:native&&nativeArtwork(id)?generatedArtwork(id):custom?custom.url+'?thumb=1':code
        ?'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(DirectCircles.svg(code))
        :ReferenceDesigns.get(id)?.thumb||'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(CircleDesigns.svg(id,'thumb'));
};
function loadArt(card){
    const image=card.querySelector('img');
    if(image&&!image.hasAttribute('src'))image.src=artUrl(card.dataset.theme||card.dataset.inactiveTheme);
}
const observer=typeof IntersectionObserver==='function'?new IntersectionObserver(entries=>{
    entries.forEach(entry=>{if(entry.isIntersecting){loadArt(entry.target);observer.unobserve(entry.target);}});
},{rootMargin:'300px'}):null;
const cards=new Map();
function syncCards(){
    const designs=visibleDesigns(),ids=new Set(designs.map(theme=>theme.id));
    cards.forEach((card,id)=>{if(!ids.has(id)){if(observer)observer.unobserve(card);card.remove();cards.delete(id);}});
    designs.forEach(theme=>{
        if(cards.has(theme.id))return;
        const card=document.createElement('button');
        card.type='button';card.className='card';card.dataset.theme=theme.id;
        card.innerHTML='<span class="check">✓</span><span class="applied-badge" hidden></span><div class="art"><img alt="" decoding="async"></div><span class="name"></span><span class="tag"></span>';
        grid.appendChild(card);cards.set(theme.id,card);
        if(observer)observer.observe(card);else loadArt(card);
    });
    if(selected&&!ids.has(selected))selected='';
    if(!ids.has(focused))focused=selected&&ids.has(selected)?selected:designs[0]?.id||'';
}
function groupDesigns(){
    const designs=visibleDesigns();
    if(activeGroup==='all')return designs;
    const current=tab(activeGroup);
    return current?designs.filter(theme=>current.members.includes(theme.id)):designs;
}
function chooseGroup(id){
    activeGroup=id;
    const ids=new Set(groupDesigns().map(theme=>theme.id));
    if(!ids.has(focused))focused=groupDesigns()[0]?.id||'';
    render();
}
function renderTabs(){
    const nav=document.getElementById('gallery-tabs'),create=document.getElementById('create-tab');
    const focusedTab=nav.contains(document.activeElement)?document.activeElement.dataset.tabId:null;
    nav.querySelectorAll('[data-tab-id]').forEach(button=>button.remove());
    if(activeGroup!=='all'&&!tab(activeGroup))activeGroup='all';
    const all=nav.querySelector('[data-group="all"]');
    all.textContent=text('all')+' · '+visibleDesigns().length;
    all.setAttribute('aria-pressed',String(activeGroup==='all'));
    tabs.forEach(value=>{
        const button=document.createElement('button');
        button.type='button';button.className='tab';button.dataset.tabId=value.id;
        button.textContent=value.name+' · '+visibleDesigns().filter(theme=>value.members.includes(theme.id)).length;
        button.setAttribute('aria-pressed',String(activeGroup===value.id));
        button.addEventListener('click',()=>chooseGroup(value.id));
        nav.insertBefore(button,create);
        if(value.id===focusedTab)button.focus();
    });
    create.disabled=busy||!readable;
    document.getElementById('tab-actions').hidden=activeGroup==='all';
    document.querySelectorAll('#tab-actions button').forEach(button=>button.disabled=busy||!readable);
}
function render(){
    syncCards();
    MagicI18n.apply(document,language);document.title=text('galleryTitle');
    renderTabs();
    const grouped=groupDesigns();
    if(activeGroup!=='all'&&!grouped.some(theme=>theme.id===focused))focused=grouped[0]?.id||'';
    const theme=focused?translatedTheme(focused):null;
    document.documentElement.style.setProperty('--ink',theme?.color||'#d5c5a2');
    const hero=document.getElementById('hero-art');
    if(hero.dataset.renderedTheme!==focused){
        hero.replaceChildren();
        if(focused==='native-N01'||native&&nativeArtwork(focused)){const image=document.createElement('img');image.alt='';image.src=focused==='native-N01'?nativeIcon:generatedArtwork(focused);hero.appendChild(image);}
        else if(directCode(focused)){hero.innerHTML=DirectCircles.svg(directCode(focused));const svg=hero.querySelector('svg');svg.setAttribute('aria-hidden','true');svg.style.cssText='display:block;width:100%;height:100%';}
        else if(theme?.url||theme?.thumb){const image=document.createElement('img');image.alt='';image.src=theme.thumb||theme.url+'?thumb=1';hero.appendChild(image);}
        else if(theme)hero.innerHTML=CircleDesigns.svg(focused,'hero');
        else{const empty=document.createElement('span');empty.className='empty-art';empty.textContent='◇';hero.appendChild(empty);}
        hero.dataset.renderedTheme=focused;
    }
    const review=focused==='native-N01'||native&&nativeArtwork(focused)?text('nativeLabel')
        :nativeArtwork(focused)?text('browserReference'):theme?.code?text(directCode(focused)?'directReview':'legacyReview'):text(theme?.group||'all');
    document.getElementById('hero-tag').textContent=theme?(theme.code||focused)+' · '+review:'';
    document.getElementById('hero-title').textContent=theme?.name||text('nothingSelected');
    document.getElementById('hero-description').textContent=focused==='native-N01'||native&&nativeArtwork(focused)?theme.desc+' '+text('nativePreviewHint')
        :nativeArtwork(focused)?theme.desc+' '+text('browserReference'):theme?.code?text('directReviewHint'):theme?.desc||text(readable?'emptyLibrary':'libraryUnavailable');
    document.getElementById('hero-saved').textContent=theme?text(focused===selected?'saved':'pending'):'';
    document.getElementById('selection-status').textContent=selected?text('current',{name:translatedTheme(selected).name}):text('nothingSelected');
    document.getElementById('apply').disabled=busy||!focused||focused===selected;
    document.getElementById('apply').textContent=text(focused&&focused===selected?'applied':'apply');
    document.getElementById('preview').disabled=busy||!focused;
    document.getElementById('preview').textContent=text('preview',{seconds:durationMs/1000});
    document.getElementById('edit-information').disabled=busy||!focused||!native;
    const materialButton=document.getElementById('hide-editor-material');
    materialButton.hidden=!media.some(item=>item.id===focused);
    materialButton.disabled=busy||!native||!readable;
    document.getElementById('open-create').disabled=busy||!readable||!native;
    document.querySelectorAll('[data-duration]').forEach(b=>{b.setAttribute('aria-pressed',String(Number(b.dataset.duration)===durationMs));b.disabled=busy||!native||!readable;});
    const deleteButton=document.getElementById('delete-design');
    deleteButton.disabled=busy||!focused;
    deleteButton.textContent=text(focused&&builtinIds.has(focused)?'disableDesign':'deleteDesign');
    document.getElementById('manage-inactive').disabled=!readable;
    document.getElementById('save-tab').disabled=busy||!readable;
    document.getElementById('import-media').disabled=!native||busy||!readable;
    renderScreens();
    document.getElementById('import-media').textContent=text(busy?'mediaLoading':'importMedia');
    document.getElementById('service-label').textContent=text(native?(enabled?'serviceOn':'serviceOff'):'serviceCheck');
    document.getElementById('service-state').classList.toggle('enabled',native&&enabled);
    document.getElementById('service-hint').hidden=!native||enabled;
    const notice=document.getElementById('library-notice');
    notice.hidden=!migrationNotice;notice.textContent=migrationNotice?text(!selected?'selectionEmptyNotice':migrationNotice==='selection_changed'?'selectionChangedNotice':'selectionResetNotice'):'';
    document.getElementById('search').placeholder=text('search');
    document.getElementById('inactive-search').placeholder=text('inactiveSearch');
    document.querySelectorAll('[data-language]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.language===language)));
    const query=document.getElementById('search').value.trim().toLocaleLowerCase(),allowed=new Set(groupDesigns().map(theme=>theme.id));let count=0;
    cards.forEach((card,id)=>{
        const design=translatedTheme(id);
        card.setAttribute('aria-label',text('select',{name:design.name}));
        card.setAttribute('aria-pressed',String(id===focused));
        card.querySelector('.name').textContent=design.name;
        card.querySelector('.tag').textContent=designCode(id)+' · '+(id==='native-N01'||native&&nativeArtwork(id)?text('nativePreviewHint')
            :nativeArtwork(id)?text('browserReference'):text(design.code?(directCode(id)?'directReview':'legacyReview'):design.group));
        const badge=card.querySelector('.applied-badge');badge.textContent=text('appliedBadge');badge.hidden=id!==selected;
        card.hidden=!allowed.has(id)||!(design.name+' '+id+' '+designCode(id)).toLocaleLowerCase().includes(query);
        if(!card.hidden)count++;
    });
    document.getElementById('design-count').textContent=text('designCount',{count});
    document.getElementById('empty').hidden=count!==0;
    document.getElementById('empty').textContent=text(cards.size?'noResults':'emptyLibrary');
    document.querySelector('#browser-preview iframe').title=text('previewTitle');
    if(document.getElementById('inactive-dialog').open)renderInactive();
    if(document.getElementById('tab-members-dialog').open)renderTabMembers();
}
window.setGalleryState=state=>{
    scenes=(Array.isArray(state.scenes)?state.scenes:[]).filter(s=>/^scene-[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(s.id)&&['WALLPAPER','CHARGING'].includes(s.purpose))
        .map(s=>({...s,name:String(s.name),url:'https://appassets.androidplatform.net/scene-thumbnails/'+s.id+'?v='+encodeURIComponent(JSON.stringify(s.layers))}));
    drafts=Array.isArray(state.drafts)?state.drafts:[];
    durationMs=[1000,3000,5000,7000].includes(state.durationMs)?state.durationMs:7000;
    delete document.getElementById('hero-art').dataset.renderedTheme;
    cards.forEach(c=>{c.querySelector('img')?.removeAttribute('src');if(observer)observer.observe(c);else loadArt(c);});
    const previousMedia=new Set(media.map(theme=>theme.id));
    const nativeChanged=!native;native=true;selected=state.selected||'';enabled=!!state.enabled;busy=!!state.busy;readable=state.readable!==false;
    language=MagicI18n.normalize(state.language);hidden=new Set(Array.isArray(state.hidden)?state.hidden:[]);
    if(['home','wallpaper','charging'].includes(state.screen))screen=state.screen;
    tabs=(Array.isArray(state.tabs)?state.tabs:[]).filter(value=>typeof value.id==='string'&&typeof value.name==='string'&&Array.isArray(value.members)).map(value=>({id:value.id,name:value.name,members:[...value.members]}));
    migrationNotice=['selection_reset','selection_changed'].includes(state.migrationNotice)?state.migrationNotice:null;
    media=(Array.isArray(state.media)?state.media:[]).filter(theme=>/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(theme.id)
        &&theme.editorOnly!==true&&['image/gif','image/png','image/jpeg'].includes(theme.mime)&&theme.url==='https://appassets.androidplatform.net/media/'+theme.id)
        .map(theme=>({...theme,name:String(theme.name),group:'uploads'}));
    if(nativeChanged){cards.forEach((card,id)=>{if(nativeArtwork(id)){card.querySelector('img')?.removeAttribute('src');loadArt(card);}});document.getElementById('hero-art').dataset.renderedTheme='';}
    if(!initialized||media.some(theme=>theme.id===selected&&!previousMedia.has(theme.id))){focused=selected;initialized=true;}
    render();
};
const dialogTriggers=new WeakMap();
const wallpaperThemes=()=>visibleDesigns().filter(theme=>nativeArtwork(theme.id)||media.some(item=>item.id===theme.id)).concat(scenes.filter(s=>s.purpose==='WALLPAPER'));
function renderScreens(){
    ['home','wallpaper','charging'].forEach(name=>document.getElementById(name+'-screen').hidden=screen!==name);
    document.getElementById('back-home').hidden=screen==='home';
    document.getElementById('charging-actions').hidden=screen!=='charging';
    document.getElementById('settings').hidden=screen!=='charging';
    document.title=text(screen==='home'?'homeTitle':screen==='wallpaper'?'wallpaperMenu':'chargingMenu');
    const upload=document.getElementById('import-wallpaper');upload.disabled=!native||busy||!readable;upload.textContent=text(busy?'mediaLoading':'importMedia');
    if(screen!=='wallpaper')return;
    document.querySelectorAll('[data-wallpaper-target]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.wallpaperTarget===wallpaperTarget)));
    const container=document.getElementById('wallpaper-grid');container.replaceChildren();
    wallpaperThemes().forEach(theme=>{
        const card=document.createElement('div');card.className='card wallpaper-card';
        const image=document.createElement('img');image.alt='';image.loading='lazy';image.src=artUrl(theme.id);
        const art=document.createElement('div');art.className='art';art.appendChild(image);
        const label=document.createElement('span');label.className='name';label.textContent=translatedTheme(theme.id).name;
        const apply=document.createElement('button');apply.type='button';apply.className='apply-wallpaper';apply.dataset.wallpaperTheme=theme.id;apply.textContent=text('wallpaperUse');apply.disabled=!native||busy;
        apply.addEventListener('click',()=>{if(native&&!busy)location.href='magiccircle://wallpaper?theme='+encodeURIComponent(theme.id)+'&target='+wallpaperTarget;});
        card.append(art,label,apply);container.appendChild(card);
        if(media.some(item=>item.id===theme.id)) {
            const material=document.createElement('button');material.type='button';material.className='apply-wallpaper';material.dataset.editorMaterial=theme.id;
            material.textContent=text('hideEditorMaterial');material.disabled=!native||busy||!readable;material.onclick=()=>hideEditorMaterial(theme.id);card.appendChild(material);
        }
        if(scenes.some(s=>s.id===theme.id)) {
            const edit=document.createElement('button');edit.textContent=text('editScene');edit.className='apply-wallpaper';edit.onclick=()=>editTheme(theme.id);edit.disabled=!native||busy;card.appendChild(edit);
            const remove=document.createElement('button');remove.textContent=text('deleteDesign');remove.className='apply-wallpaper';remove.disabled=!native||busy;remove.onclick=()=>{if(native&&!busy)location.href='magiccircle://delete?theme='+encodeURIComponent(theme.id);};card.appendChild(remove);
        }
    });
    document.getElementById('wallpaper-empty').hidden=wallpaperThemes().length!==0;
}
function showScreen(value){screen=value;render();window.scrollTo(0,0);if(native)location.href='magiccircle://screen?name='+value;}
document.getElementById('open-wallpapers').addEventListener('click',()=>showScreen('wallpaper'));
document.getElementById('open-charging').addEventListener('click',()=>showScreen('charging'));
document.getElementById('back-home').addEventListener('click',()=>showScreen('home'));
document.querySelectorAll('[data-wallpaper-target]').forEach(button=>button.addEventListener('click',()=>{wallpaperTarget=button.dataset.wallpaperTarget;renderScreens();}));
document.getElementById('import-wallpaper').addEventListener('click',()=>{if(native&&!busy)location.href='magiccircle://import-wallpaper';});
window.navigateBack=()=>{
    const dialog=document.querySelector('dialog[open]');if(dialog){if(dialog===previewDialog)closePreview();else closeDialog(dialog);return true;}
    if(screen==='home')return false;showScreen('home');return true;
};
function openDialog(dialog,trigger){dialogTriggers.set(dialog,trigger);dialog.showModal();}
function editTheme(id){if(native&&!busy){document.querySelectorAll('dialog[open]').forEach(closeDialog);location.href='magiccircle://edit?theme='+encodeURIComponent(id);}}
createButton.addEventListener('click',()=>{
    const list=document.getElementById('draft-list');list.replaceChildren();
    drafts.forEach(d=>{const button=document.createElement('button');button.className='back';button.textContent=text('editScene')+' · '+(d.scene?.name||d.key);button.onclick=()=>editTheme(d.key);list.appendChild(button);});
    openDialog(document.getElementById('create-screen-dialog'),createButton);
});
document.getElementById('close-create').onclick=()=>closeDialog(document.getElementById('create-screen-dialog'));
document.querySelectorAll('[data-create-purpose]').forEach(b=>b.onclick=()=>{if(native&&!busy){closeDialog(document.getElementById('create-screen-dialog'));location.href='magiccircle://create?purpose='+b.dataset.createPurpose;}});
document.getElementById('edit-information').onclick=()=>editTheme(focused);
function hideEditorMaterial(id){if(native&&!busy&&readable&&media.some(item=>item.id===id))location.href='magiccircle://editor-material?theme='+encodeURIComponent(id);}
document.getElementById('hide-editor-material').onclick=()=>hideEditorMaterial(focused);
document.querySelectorAll('[data-duration]').forEach(b=>b.onclick=()=>{if(native&&!busy)location.href='magiccircle://duration?ms='+b.dataset.duration;});
function closeDialog(dialog){if(dialog.open)dialog.close();}
document.querySelectorAll('dialog').forEach(dialog=>dialog.addEventListener('close',()=>dialogTriggers.get(dialog)?.focus()));
grid.addEventListener('click',event=>{const card=event.target.closest('[data-theme]');if(card){focused=card.dataset.theme;render();}});
document.querySelector('[data-group="all"]').addEventListener('click',()=>chooseGroup('all'));
document.querySelectorAll('[data-language]').forEach(button=>button.addEventListener('click',()=>{
    language=MagicI18n.normalize(button.dataset.language);render();if(native)location.href='magiccircle://language?lang='+language;
}));
document.getElementById('search').addEventListener('input',render);
document.getElementById('apply').addEventListener('click',()=>{
    if(!focused)return;
    if(native)location.href='magiccircle://select?theme='+encodeURIComponent(focused);
    else{selected=focused;render();document.getElementById('selection-status').textContent=text('browserOnly');}
});
const previewDialog=document.getElementById('browser-preview');let timer;
function closePreview(){clearTimeout(timer);closeDialog(previewDialog);previewDialog.querySelector('iframe').src='about:blank';}
previewDialog.addEventListener('cancel',()=>{clearTimeout(timer);previewDialog.querySelector('iframe').src='about:blank';});
document.getElementById('close-preview').addEventListener('click',closePreview);
document.getElementById('preview').addEventListener('click',()=>{
    if(!focused)return;
    if(native){location.href='magiccircle://preview?theme='+encodeURIComponent(focused);return;}
    if(focused==='native-N01'){document.getElementById('selection-status').textContent=text('nativePreviewHint');return;}
    const file=ReferenceDesigns.get(focused)?'collection_circle.html':focused==='classic'?'magic_circle.html':'theme_circle.html';
    previewDialog.querySelector('iframe').src=file+'?theme='+encodeURIComponent(focused)+'&lang='+language+'&demo=1';
    openDialog(previewDialog,document.getElementById('preview'));timer=setTimeout(closePreview,7400);
});
document.getElementById('settings').addEventListener('click',()=>{
    if(native)location.href='magiccircle://settings';else document.getElementById('selection-status').textContent=text('appSettings');
});
document.getElementById('import-media').addEventListener('click',()=>{if(native)location.href='magiccircle://import';});
document.getElementById('delete-design').addEventListener('click',()=>{
    if(!focused||busy)return;
    const builtin=builtinIds.has(focused),action=builtin?'disable':'delete',id=focused;
    if(native){location.href='magiccircle://'+action+'?theme='+encodeURIComponent(id);return;}
    if(confirm(text('removeConfirm'))){if(builtin)hidden.add(id);else media=media.filter(theme=>theme.id!==id);render();document.getElementById('selection-status').textContent=text('browserOnly');}
});
const inactiveDialog=document.getElementById('inactive-dialog');
function renderInactive(){
    const container=document.getElementById('inactive-grid'),query=document.getElementById('inactive-search').value.trim().toLocaleLowerCase();
    container.replaceChildren();let count=0;
    builtins.filter(theme=>hidden.has(theme.id)).forEach(theme=>{
        const design=translatedTheme(theme.id),card=document.createElement('div');
        card.className='card inactive-card';card.dataset.inactiveTheme=theme.id;
        card.innerHTML='<div class="art"><img alt="" decoding="async"></div><span class="name"></span><span class="tag"></span><button type="button" class="enable"></button>';
        card.querySelector('.name').textContent=design.name;card.querySelector('.tag').textContent=designCode(theme.id);
        const button=card.querySelector('button');button.dataset.enableTheme=theme.id;button.textContent=text('enableDesign');button.disabled=busy||!readable;
        button.addEventListener('click',()=>{if(native)location.href='magiccircle://enable?theme='+encodeURIComponent(theme.id);else{hidden.delete(theme.id);render();}});
        const edit=document.createElement('button');edit.className='enable';edit.textContent=text('editInformation');edit.disabled=!native||busy;edit.onclick=()=>editTheme(theme.id);card.appendChild(edit);
        card.hidden=!(design.name+' '+theme.id+' '+designCode(theme.id)).toLocaleLowerCase().includes(query);
        container.appendChild(card);loadArt(card);if(!card.hidden)count++;
    });
    document.getElementById('inactive-empty').hidden=count!==0;
}
document.getElementById('manage-inactive').addEventListener('click',event=>{renderInactive();openDialog(inactiveDialog,event.currentTarget);});
document.getElementById('inactive-search').addEventListener('input',renderInactive);
document.getElementById('close-inactive').addEventListener('click',()=>closeDialog(inactiveDialog));
let editorMode='create';
const tabEditor=document.getElementById('tab-editor');
function normalizedTabName(){return document.getElementById('tab-name').value.trim().normalize('NFC');}
function openTabEditor(mode){
    editorMode=mode;const current=tab(activeGroup),input=document.getElementById('tab-name');
    document.getElementById('tab-editor-title').textContent=text(mode==='create'?'createTab':'renameTab');
    document.getElementById('tab-error').textContent='';input.value=mode==='rename'&&current?current.name:'';
    openDialog(tabEditor,document.getElementById(mode==='create'?'create-tab':'rename-tab'));input.focus();
}
document.getElementById('create-tab').addEventListener('click',()=>openTabEditor('create'));
document.getElementById('rename-tab').addEventListener('click',()=>openTabEditor('rename'));
document.getElementById('save-tab').addEventListener('click',()=>{
    const name=normalizedTabName(),error=document.getElementById('tab-error'),current=tab(activeGroup);
    if([...name].length<1||[...name].length>40){error.textContent=text('tabNameInvalid');return;}
    const excludedId=editorMode==='rename'?current?.id:'';
    if(tabs.some(value=>value.id!==excludedId&&value.name.trim().normalize('NFC')===name)){error.textContent=text('tabNameDuplicate');return;}
    error.textContent='';if(!native){document.getElementById('selection-status').textContent=text('browserOnly');return;}
    location.href=editorMode==='create'?'magiccircle://tab-create?name='+encodeURIComponent(name)
        :'magiccircle://tab-rename?id='+encodeURIComponent(current.id)+'&name='+encodeURIComponent(name);
});
document.getElementById('close-tab-editor').addEventListener('click',()=>closeDialog(tabEditor));
document.getElementById('cancel-tab-editor').addEventListener('click',()=>closeDialog(tabEditor));
const membersDialog=document.getElementById('tab-members-dialog');
function renderTabMembers(){
    const current=tab(activeGroup),container=document.getElementById('tab-members-grid');container.replaceChildren();
    if(!current)return;
    visibleDesigns().forEach(theme=>{
        const design=translatedTheme(theme.id),member=current.members.includes(theme.id),row=document.createElement('div'),button=document.createElement('button');
        row.className='member-row';const label=document.createElement('span');label.textContent=designCode(theme.id)+' · '+design.name;
        button.type='button';button.dataset.tabMemberTheme=theme.id;button.setAttribute('aria-pressed',String(member));button.textContent=text(member?'removeFromTab':'addToTab');button.disabled=busy||!readable;
        button.addEventListener('click',()=>{if(native)location.href='magiccircle://tab-member?id='+encodeURIComponent(current.id)+'&theme='+encodeURIComponent(theme.id)+'&member='+(member?'0':'1');});
        row.append(label,button);container.appendChild(row);
    });
}
document.getElementById('manage-tab-members').addEventListener('click',event=>{renderTabMembers();openDialog(membersDialog,event.currentTarget);});
document.getElementById('close-tab-members').addEventListener('click',()=>closeDialog(membersDialog));
document.getElementById('delete-tab').addEventListener('click',()=>{
    const current=tab(activeGroup);if(!current||busy)return;
    if(native){location.href='magiccircle://tab-delete?id='+encodeURIComponent(current.id);return;}
    if(confirm(text('deleteTabConfirm'))){tabs=tabs.filter(value=>value.id!==current.id);chooseGroup('all');}
});
render();
