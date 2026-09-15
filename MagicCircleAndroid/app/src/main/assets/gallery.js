let selected='classic', focused='ref-1-01', native=false, initialized=false, enabled=false, activeGroup='reference';
let language=MagicI18n.normalize(new URLSearchParams(location.search).get('lang') || navigator.language.split('-')[0]);
const grid=document.getElementById('design-grid');
const translatedTheme=id=>MagicI18n.theme(CircleDesigns.get(id),language);
const text=(key,values)=>MagicI18n.t(key,language,values);
const cards=new Map();
function loadArt(card){
    const design=CircleDesigns.get(card.dataset.theme);
    if(design.group==='reference'){
        const art=card.querySelector('.art');
        if(!art.querySelector('svg'))art.innerHTML=CircleDesigns.svg(design.id,'thumb');
        return;
    }
    const image=card.querySelector('img');
    if(!image.hasAttribute('src'))image.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(CircleDesigns.svg(card.dataset.theme,'thumb'));
}
// Decode only nearby thumbnails. A long collection should not create thousands of live SVG nodes.
const observer=typeof IntersectionObserver==='function'?new IntersectionObserver(entries=>{
    entries.forEach(entry=>{if(entry.isIntersecting){loadArt(entry.target);observer.unobserve(entry.target);}});
},{rootMargin:'300px'}):null;
CircleDesigns.list.forEach(theme=>{
    const card=document.createElement('button');
    card.type='button';card.className='card';card.dataset.theme=theme.id;
    card.innerHTML='<span class="check">✓</span><span class="applied-badge" hidden></span><div class="art"><img alt="" decoding="async"></div><span class="name"></span><span class="tag"></span>';
    grid.appendChild(card);cards.set(theme.id,card);
    if(observer)observer.observe(card);else loadArt(card);
});
const matchesGroup=(theme,group)=>group==='all'||(group==='previous'?theme.group!=='reference':group.startsWith('sheet-')?theme.sheet===Number(group.slice(6)):theme.group===group);
const category=theme=>theme.group==='reference'?text('sheet-'+theme.sheet)+' · '+String(theme.index).padStart(2,'0'):text(theme.group);
function render(){
    MagicI18n.apply(document,language);document.title=text('galleryTitle');
    const theme=translatedTheme(focused);
    document.documentElement.style.setProperty('--ink',theme.color);
    const hero=document.getElementById('hero-art');
    if(hero.dataset.renderedTheme!==focused){hero.innerHTML=CircleDesigns.svg(focused,'hero');hero.dataset.renderedTheme=focused;}
    document.getElementById('hero-tag').textContent=category(theme);
    document.getElementById('hero-title').textContent=theme.name;
    document.getElementById('hero-description').textContent=theme.desc;
    document.getElementById('hero-saved').textContent=text(focused===selected?'saved':'pending');
    document.getElementById('selection-status').textContent=text('current',{name:translatedTheme(selected).name});
    document.getElementById('apply').disabled=focused===selected;
    document.getElementById('apply').textContent=text(focused===selected?'applied':'apply');
    document.getElementById('service-label').textContent=text(native?(enabled?'serviceOn':'serviceOff'):'serviceCheck');
    document.getElementById('service-state').classList.toggle('enabled',native&&enabled);
    document.getElementById('service-hint').hidden=!native||enabled;
    document.getElementById('search').placeholder=text('search');
    document.querySelectorAll('[data-language]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.language===language)));
    document.querySelectorAll('.tab').forEach(button=>{
        const group=button.dataset.group;
        const count=CircleDesigns.list.filter(t=>matchesGroup(t,group)).length;
        button.textContent=text(group)+' · '+count;
        button.setAttribute('aria-pressed',String(group===activeGroup));
    });
    const query=document.getElementById('search').value.trim().toLocaleLowerCase();let count=0;
    cards.forEach((card,id)=>{
        const design=translatedTheme(id);
        card.setAttribute('aria-label',text('select',{name:design.name}));
        card.setAttribute('aria-pressed',String(id===focused));
        card.querySelector('.name').textContent=design.name;
        card.querySelector('.tag').textContent=category(design);
        const badge=card.querySelector('.applied-badge');badge.textContent=text('appliedBadge');badge.hidden=id!==selected;
        card.hidden=!matchesGroup(design,activeGroup)||!design.name.toLocaleLowerCase().includes(query);
        if(!card.hidden)count++;
    });
    document.getElementById('design-count').textContent=text('designCount',{count});
    document.getElementById('empty').hidden=count!==0;
    document.querySelector('#browser-preview iframe').title=text('previewTitle');
}
window.setGalleryState=state=>{
    native=true;selected=CircleDesigns.get(state.selected).id;enabled=!!state.enabled;
    language=MagicI18n.normalize(state.language);
    if(!initialized){focused=CircleDesigns.get(selected).group==='reference'?selected:'ref-1-01';initialized=true;}
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
    if(native)location.href='magiccircle://select?theme='+encodeURIComponent(focused);
    else{selected=focused;render();document.getElementById('selection-status').textContent=text('browserOnly');}
});
const dialog=document.getElementById('browser-preview');let timer;
function closePreview(){clearTimeout(timer);dialog.close();dialog.querySelector('iframe').src='about:blank';}
dialog.addEventListener('cancel',()=>{clearTimeout(timer);dialog.querySelector('iframe').src='about:blank';});
document.getElementById('close-preview').addEventListener('click',closePreview);
document.getElementById('preview').addEventListener('click',()=>{
    if(native){location.href='magiccircle://preview?theme='+encodeURIComponent(focused);return;}
    const file=focused==='classic'?'magic_circle.html':'theme_circle.html';
    dialog.querySelector('iframe').src=file+'?theme='+focused+'&lang='+language+'&demo=1';
    dialog.showModal();timer=setTimeout(closePreview,7400);
});
document.getElementById('settings').addEventListener('click',()=>{
    if(native)location.href='magiccircle://settings';else document.getElementById('selection-status').textContent=text('appSettings');
});
render();
