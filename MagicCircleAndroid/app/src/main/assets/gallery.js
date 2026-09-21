let selected='classic', focused='classic', native=false, initialized=false, enabled=false, activeGroup='all';
let language=MagicI18n.normalize(new URLSearchParams(location.search).get('lang') || navigator.language.split('-')[0]);
const grid=document.getElementById('design-grid');
let hidden=new Set(), media=[], busy=false, readable=true;
const visibleDesigns=()=>readable?CircleDesigns.list.concat(ReferenceDesigns.list).filter(t=>!hidden.has(t.id)).concat(media):[];
const translatedTheme=id=>{
    const custom=media.find(t=>t.id===id);
    return custom?{...custom,desc:text(custom.mime==='image/gif'?'mediaAnimated':'mediaStill'),color:'#d5c5a2',group:'uploads'}
        :MagicI18n.theme(ReferenceDesigns.get(id)||CircleDesigns.get(id),language);
};
const text=(key,values)=>MagicI18n.t(key,language,values);
const cards=new Map();
const directCode=id=>{const code=ReferenceDesigns.get(id)?.code;return DirectCircles.ids.includes(code)?code:null;};
function loadArt(card){
    const image=card.querySelector('img');
    if(!image.hasAttribute('src')){
        const custom=media.find(t=>t.id===card.dataset.theme);
        const code=directCode(card.dataset.theme);
        image.src=custom?custom.url+'?thumb=1':code?'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(DirectCircles.svg(code)):ReferenceDesigns.get(card.dataset.theme)?.thumb||'data:image/svg+xml;charset=utf-8,'+encodeURIComponent(CircleDesigns.svg(card.dataset.theme,'thumb'));
    }
}
// Decode only nearby thumbnails. A long collection should not create thousands of live SVG nodes.
const observer=typeof IntersectionObserver==='function'?new IntersectionObserver(entries=>{
    entries.forEach(entry=>{if(entry.isIntersecting){loadArt(entry.target);observer.unobserve(entry.target);}});
},{rootMargin:'300px'}):null;
function syncCards(){
    const designs=visibleDesigns(), ids=new Set(designs.map(t=>t.id));
    cards.forEach((card,id)=>{if(!ids.has(id)){if(observer)observer.unobserve(card);card.remove();cards.delete(id);}});
    designs.forEach(theme=>{
    if(cards.has(theme.id))return;
    const card=document.createElement('button');
    card.type='button';card.className='card';card.dataset.theme=theme.id;
    card.innerHTML='<span class="check">✓</span><span class="applied-badge" hidden></span><div class="art"><img alt="" decoding="async"></div><span class="name"></span><span class="tag"></span>';
    grid.appendChild(card);cards.set(theme.id,card);
    if(observer)observer.observe(card);else loadArt(card);
});
    if(selected&&!ids.has(selected))selected=designs[0]?.id||'';
    if(!ids.has(focused))focused=selected;
}
function render(){
    syncCards();
    MagicI18n.apply(document,language);document.title=text('galleryTitle');
    const theme=focused?translatedTheme(focused):null;
    document.documentElement.style.setProperty('--ink',theme?.color||'#d5c5a2');
    const hero=document.getElementById('hero-art');
    if(hero.dataset.renderedTheme!==focused){
        hero.replaceChildren();
        if(directCode(focused)){hero.innerHTML=DirectCircles.svg(directCode(focused));const svg=hero.querySelector('svg');svg.setAttribute('aria-hidden','true');svg.style.cssText='display:block;width:100%;height:100%';}
        else if(theme?.url||theme?.thumb){const image=document.createElement('img');image.alt='';image.src=theme.thumb||theme.url+'?thumb=1';hero.appendChild(image);}
        else if(theme)hero.innerHTML=CircleDesigns.svg(focused,'hero');
        else{const empty=document.createElement('span');empty.className='empty-art';empty.textContent='◇';hero.appendChild(empty);}
        hero.dataset.renderedTheme=focused;
    }
    const review=theme?.code?text(directCode(focused)?'directReview':'legacyReview'):text(theme?.group||'all');
    document.getElementById('hero-tag').textContent=theme?(theme.code||focused)+' · '+review:'';
    document.getElementById('hero-title').textContent=theme?.name||text('nothingSelected');
    document.getElementById('hero-description').textContent=theme?.code?text('directReviewHint'):theme?.desc||text(readable?'emptyLibrary':'libraryUnavailable');
    document.getElementById('hero-saved').textContent=theme?text(focused===selected?'saved':'pending'):'';
    document.getElementById('selection-status').textContent=selected?text('current',{name:translatedTheme(selected).name}):text('nothingSelected');
    document.getElementById('apply').disabled=busy||!focused||focused===selected;
    document.getElementById('apply').textContent=text(focused&&focused===selected?'applied':'apply');
    document.getElementById('preview').disabled=busy||!focused;
    document.getElementById('delete-design').disabled=busy||!focused;
    document.getElementById('restore-designs').hidden=hidden.size===0;
    document.getElementById('restore-designs').disabled=busy||!readable;
    document.getElementById('import-media').disabled=!native||busy||!readable;
    document.getElementById('import-media').textContent=text(busy?'mediaLoading':'importMedia');
    document.getElementById('service-label').textContent=text(native?(enabled?'serviceOn':'serviceOff'):'serviceCheck');
    document.getElementById('service-state').classList.toggle('enabled',native&&enabled);
    document.getElementById('service-hint').hidden=!native||enabled;
    document.getElementById('search').placeholder=text('search');
    document.querySelectorAll('[data-language]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.language===language)));
    document.querySelectorAll('.tab').forEach(button=>{
        const group=button.dataset.group;
        const count=visibleDesigns().filter(t=>group==='all'||t.group===group).length;
        button.textContent=text(group)+' · '+count;
        button.setAttribute('aria-pressed',String(group===activeGroup));
    });
    const query=document.getElementById('search').value.trim().toLocaleLowerCase();let count=0;
    cards.forEach((card,id)=>{
        const design=translatedTheme(id);
        card.setAttribute('aria-label',text('select',{name:design.name}));
        card.setAttribute('aria-pressed',String(id===focused));
        card.querySelector('.name').textContent=design.name;
        card.querySelector('.tag').textContent=(design.code||id)+' · '+text(design.code?(directCode(id)?'directReview':'legacyReview'):design.group);
        const badge=card.querySelector('.applied-badge');badge.textContent=text('appliedBadge');badge.hidden=id!==selected;
        card.hidden=(activeGroup!=='all'&&design.group!==activeGroup)||!(design.name+' '+id).toLocaleLowerCase().includes(query);
        if(!card.hidden)count++;
    });
    document.getElementById('design-count').textContent=text('designCount',{count});
    document.getElementById('empty').hidden=count!==0;
    document.getElementById('empty').textContent=text(cards.size?'noResults':'emptyLibrary');
    document.querySelector('#browser-preview iframe').title=text('previewTitle');
}
window.setGalleryState=state=>{
    const previousMedia=new Set(media.map(t=>t.id));
    native=true;selected=state.selected||'';enabled=!!state.enabled;
    busy=!!state.busy;
    readable=state.readable!==false;
    language=MagicI18n.normalize(state.language);
    hidden=new Set(Array.isArray(state.hidden)?state.hidden:[]);
    // Only the native allowlisted endpoint may supply artwork. Filenames remain plain text.
    media=(Array.isArray(state.media)?state.media:[]).filter(t=>/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(t.id)
        && ['image/gif','image/png','image/jpeg'].includes(t.mime)
        && t.url==='https://appassets.androidplatform.net/media/'+t.id)
        .map(t=>({...t,name:String(t.name),group:'uploads'}));
    if(!initialized||media.some(t=>t.id===selected&&!previousMedia.has(t.id))){focused=selected;initialized=true;}
    render();
};
grid.addEventListener('click',event=>{const card=event.target.closest('[data-theme]');if(card){focused=card.dataset.theme;render();}});
document.querySelectorAll('[data-language]').forEach(button=>button.addEventListener('click',()=>{
    language=MagicI18n.normalize(button.dataset.language);render();
    if(native)location.href='magiccircle://language?lang='+language;
}));
document.querySelectorAll('.tab').forEach(button=>button.addEventListener('click',()=>{activeGroup=button.dataset.group;render();}));
document.getElementById('search').addEventListener('input',render);
document.getElementById('apply').addEventListener('click',()=>{
    if(!focused)return;
    if(native)location.href='magiccircle://select?theme='+encodeURIComponent(focused);
    else{selected=focused;render();document.getElementById('selection-status').textContent=text('browserOnly');}
});
const dialog=document.getElementById('browser-preview');let timer;
function closePreview(){clearTimeout(timer);dialog.close();dialog.querySelector('iframe').src='about:blank';}
dialog.addEventListener('cancel',()=>{clearTimeout(timer);dialog.querySelector('iframe').src='about:blank';});
document.getElementById('close-preview').addEventListener('click',closePreview);
document.getElementById('preview').addEventListener('click',()=>{
    if(!focused)return;
    if(native){location.href='magiccircle://preview?theme='+encodeURIComponent(focused);return;}
    const file=ReferenceDesigns.get(focused)?'collection_circle.html':focused==='classic'?'magic_circle.html':'theme_circle.html';
    dialog.querySelector('iframe').src=file+'?theme='+focused+'&lang='+language+'&demo=1';
    dialog.showModal();timer=setTimeout(closePreview,7400);
});
document.getElementById('settings').addEventListener('click',()=>{
    if(native)location.href='magiccircle://settings';else document.getElementById('selection-status').textContent=text('appSettings');
});
document.getElementById('import-media').addEventListener('click',()=>{if(native)location.href='magiccircle://import';});
document.getElementById('delete-design').addEventListener('click',()=>{
    if(!focused)return;
    if(native){location.href='magiccircle://delete?theme='+encodeURIComponent(focused);return;}
    if(confirm(text('removeConfirm'))){hidden.add(focused);render();document.getElementById('selection-status').textContent=text('browserOnly');}
});
document.getElementById('restore-designs').addEventListener('click',()=>{
    if(native)location.href='magiccircle://restore';else{hidden.clear();render();}
});
render();
