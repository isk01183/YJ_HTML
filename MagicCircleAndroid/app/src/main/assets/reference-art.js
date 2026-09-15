(function(root){
 'use strict';
 const catalog=typeof module!=='undefined'&&module.exports?require('./reference-catalog.js'):root.ReferenceCatalog;
 const highlights=[
  [1,1,[0,0,426,349],'기본 · 마도 발동','基本・魔導発動','Arcane Awakening'],
  [1,2,[427,0,426,349],'신성 · 축복','神聖・祝福','Sacred Blessing'],
  [1,3,[854,0,426,349],'어둠 · 금지된 마법','闇・禁じられた魔法','Forbidden Darkness'],
  [1,4,[24,407,600,371],'자연 · 치유','自然・癒し','Nature’s Healing'],
  [1,5,[642,407,632,371],'우주 · 천체','宇宙・天体','Celestial Cosmos'],
  [5,1,[0,0,426,379],'얼음의 마법진','氷の魔法陣','Circle of Ice'],
  [5,2,[427,0,426,379],'빛의 마법진','光の魔法陣','Circle of Light'],
  [5,3,[854,0,426,379],'어둠의 마법진','闇の魔法陣','Circle of Darkness'],
  [5,4,[140,424,500,368],'자연의 마법진','自然の魔法陣','Circle of Nature'],
  [5,5,[632,428,500,364],'우주의 마법진','宇宙の魔法陣','Circle of the Cosmos']
 ].map(([sheet,index,rect,ko,ja,en])=>({id:'ref-'+sheet+'-'+String(index).padStart(2,'0'),sheet,index,rect,names:{ko,ja,en}}));
 const list=[...catalog,...highlights].sort((a,b)=>a.sheet-b.sheet||a.index-b.index).map(record=>{
  const number=String(record.index).padStart(2,'0');
  const descriptions={ko:'첨부 이미지 '+record.sheet+' · '+number+'번 마법진',ja:'添付画像 '+record.sheet+'・'+number+'番の魔法陣',en:'Reference image '+record.sheet+' · Circle '+number};
  return {...record,group:'reference',name:record.names.ja,desc:descriptions.ja,descriptions,
   color:'#e4ddce',accent:'#e7efff',tag:'REF '+record.sheet+' / '+number,core:'reference',rings:0,orbits:0};
 });
 function svg(theme){
  const [x,y,w,h]=theme.rect,scale=600/Math.max(w,h),width=w*scale,height=h*scale;
  // Clip the original sheet through a nested viewport; meet preserves its aspect ratio.
  // The JPEG bytes stay unchanged, with no generative redraw or extra color/glow filter.
  return '<svg class="circle-svg reference-svg" viewBox="0 0 600 600" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'+
   '<svg class="fixed-seals reference-viewport" x="'+(600-width)/2+'" y="'+(600-height)/2+'" width="'+width+'" height="'+height+'" viewBox="'+[x,y,w,h].join(' ')+'" overflow="hidden">'+
   '<image href="artwork/sheet-'+theme.sheet+'.jpg" x="0" y="0" width="1280" height="'+(theme.sheet===3?1170:853)+'" preserveAspectRatio="none"/>'+
   '</svg></svg>';
 }
 const api={list,svg};
 if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.ReferenceArt=api;
})(typeof globalThis==='undefined'?this:globalThis);
