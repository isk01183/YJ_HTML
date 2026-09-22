'use strict';
// Regressions: offset botanical roots, doubled wheel shafts/squares, crowded faces/dials,
// and auxiliary strokes that compete with the authored primary silhouettes.
const assert=require('node:assert/strict'),path=require('node:path');
const {chromium}=require('playwright');
const {pathToFileURL}=require('node:url');
const assets=path.resolve(__dirname,'../app/src/main/assets');
require(path.join(assets,'direct-extra.js'));require(path.join(assets,'direct-circles.js'));
const designs=require(path.join(assets,'circle-designs.js'));
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'msedge'}),failures=[];
 try{
  const page=await browser.newPage({offline:true});
  for(const code of ['C11','C12','C21','C26','C30','A14','crimson-abyss']){
   const svg=code==='crimson-abyss'?designs.svg(code,'art-test'):DirectCircles.svg(code);
   assert.doesNotMatch(svg,/<image\b|NaN|Infinity/);
   await page.setContent(svg);
   const result=await page.evaluate(code=>{
    const all=sel=>[...document.querySelectorAll(sel)],crisp=all('[data-outline] path');
    const d=p=>p.getAttribute('d'),near=(a,b)=>Math.abs(a-b)<.02;
    const point=(e,x,y)=>{const p=new DOMPoint(x,y).matrixTransform(e.ownerSVGElement.getScreenCTM().inverse().multiply(e.getScreenCTM()));return [p.x,p.y];};
    const bounds=e=>{const b=e.getBBox(),a=point(e,b.x,b.y),z=point(e,b.x+b.width,b.y+b.height);return {left:Math.min(a[0],z[0]),right:Math.max(a[0],z[0]),top:Math.min(a[1],z[1]),bottom:Math.max(a[1],z[1])};};
    const opacity=e=>{let o=1;for(;e&&e.tagName!=='svg';e=e.parentElement)o*=Number(getComputedStyle(e).opacity);return o;};
    const width=e=>parseFloat(getComputedStyle(e).strokeWidth);
    if(code==='C11'){
     const aux=crisp.filter(p=>d(p).includes('M90 35V80')),main=crisp.find(p=>d(p).startsWith('M90 30L95'));
     return {aux:aux.map(p=>[opacity(p),width(p)]),main:[opacity(main),width(main)],arches:all('[data-outline] [data-inner-arch]').length};
    }
    if(code==='C12')return {roots:all('[data-outline] [data-petal]').map(p=>({d:d(p),start:point(p,...d(p).match(/^M([\d.]+) ([\d.]+)/).slice(1).map(Number)),end:point(p,...d(p).match(/([\d.]+) ([\d.]+)Z$/).slice(1).map(Number))})),branches:crisp.filter(p=>d(p).startsWith('M90 9')&&d(p).includes('90 65')).map(p=>d(p))};
    if(code==='C21'){
     const face=crisp.find(p=>p.getAttribute('fill')==='#190910'),eyes=crisp.find(p=>p.getAttribute('fill')==='#ffe4df'),horns=all('[data-outline] [data-horn]');
     const eyeBoxes=d(eyes).match(/M[^M]+/g).map(part=>{const e=eyes.cloneNode();e.setAttribute('d',part);eyes.parentElement.append(e);const b=bounds(e);e.remove();return b;});
     return {face:bounds(face),eyes:bounds(eyes),eyeBoxes,horns:horns.map(bounds)};
    }
    if(code==='C26'){
     // Extract actual M/L/H/V segments (including compound paths), then transform
     // to SVG coordinates so mirrored/rotated overlaps cannot evade the check.
     const segments=[];
     for(const p of crisp){let x=0,y=0;for(const m of d(p).matchAll(/([MLHV])([\d.\s,-]+)/g)){const v=m[2].trim().split(/[\s,]+/).map(Number),cmd=m[1];let nx=cmd==='V'?x:v[0],ny=cmd==='H'?y:cmd==='V'?v[0]:v[1];if(cmd!=='M')segments.push([point(p,x,y),point(p,nx,ny)]);x=nx;y=ny;}}
     const coverage=[[90,60],[120,90],[90,120],[60,90]].map(([x,y])=>segments.filter(([a,b])=>(near(a[0],x)&&near(b[0],x)&&Math.min(a[1],b[1])<y&&Math.max(a[1],b[1])>y)||(near(a[1],y)&&near(b[1],y)&&Math.min(a[0],b[0])<x&&Math.max(a[0],b[0])>x)).length);
     const joints=all('[data-outline] [data-axis-bridge]').map(p=>{const group=p.parentElement,spoke=all('[data-outline] [data-wheel-spoke]').find(s=>s.parentElement.getAttribute('transform')===group.getAttribute('transform'));return [point(p,90,28),point(group.querySelector('[data-axis-ornament]'),90,28),point(p,90,39),point(spoke,90,39),point(group.querySelector('[data-axis-shaft]'),90,81),point(spoke,90,81)];});
     return {coverage,joints,spokes:all('[data-outline] [data-wheel-spoke]').map(p=>d(p)),bridges:all('[data-outline] [data-axis-bridge]').map(p=>d(p)),axes:all('[data-outline] [data-axis-shaft]').map(p=>d(p))};
    }
    if(code==='C30'){
     const polygons=crisp.filter(p=>/^M[\d\s.,L-]+Z$/i.test(d(p))).map(p=>(d(p).match(/-?\d+(?:\.\d+)?/g)||[]).reduce((a,v,i,vs)=>i%2?a:(a.push(point(p,+v,+vs[i+1])),a),[]));
     const atRadius=(r,n)=>polygons.filter(ps=>ps.length===n&&ps.every(q=>near(Math.hypot(q[0]-90,q[1]-90),r)));
     const canonical=ps=>ps.map(p=>p.map(x=>x.toFixed(2)).join(',')).sort().join(';');
     return {squares49:atRadius(49,4).map(canonical),squares16:atRadius(16,4).map(canonical),triangles:atRadius(49,3).map(canonical),satellites:all('[data-outline] [data-satellite]').map(e=>({id:e.dataset.satellite,sigil:e.querySelector('path').getAttribute('d')})),lights:all('[data-outline="highlights"] circle').filter(c=>c.getAttribute('r')==='5').map(c=>[+c.getAttribute('cx'),+c.getAttribute('cy')])};
    }
    if(code==='A14'){
     const nums=all('[data-outline] text'),circles=all('[data-outline="geometry"] circle');
     return {mechanism:Math.max(...circles.map(e=>+e.getAttribute('r'))),gear:crisp.find(p=>p.hasAttribute('data-clock-gear'))?.getAttribute('d'),numbers:nums.map(e=>({text:e.textContent,x:+e.getAttribute('x'),y:+e.getAttribute('y')-3})),hands:crisp.filter(p=>d(p).startsWith('M100 100L')).map(p=>d(p))};
    }
    const motif=document.querySelector('.fixed-seals'),layers=[...motif.children],primary=motif.querySelector('[data-seal-layer="primary"]'),secondary=motif.querySelector('[data-seal-layer="secondary"]'),lattice=motif.querySelector('[data-seal-layer="lattice"]');
    return {layers:layers.map(e=>e.dataset.sealLayer),polygons:all('.fixed-seals polygon').map(p=>p.getAttribute('points').split(' ').sort().join(' ')),hierarchy:primary&&secondary&&lattice?[opacity(lattice),opacity(primary),width(secondary),width(primary)]:null,primary:primary?.querySelector('polygon').getAttribute('points'),thorns:all('[data-seal-layer="thorns"] path').map(p=>({d:d(p),transform:p.parentElement.getAttribute('transform')})),joins:all('.fixed-seals path,.fixed-seals polygon').filter(p=>p.tagName==='polygon'||/Z$/i.test(d(p))).map(p=>getComputedStyle(p).strokeLinejoin)};
   },code);
   try{
    if(code==='C11'){assert.equal(result.arches,8);assert.equal(result.aux.length,4);assert.ok(result.aux.every(([o,w])=>o<result.main[0]&&w<result.main[1]),'C11 auxiliary shafts must be dimmer/thinner than spears');}
    if(code==='C12'){
     assert.equal(result.roots.length,8);
     for(const p of result.roots){assert.ok([...p.start,...p.end].every(x=>Math.abs(x-90)<.01),'C12 petal roots must meet actual rotation center');assert.match(p.d,/^M90 90(?=[A-Z\s]).*90 90Z$/);}
     assert.equal(result.branches.length,8);assert.ok(result.branches.every(d=>/90 65.*Z M90 65/.test(d)),'C12 upper leaf must attach at lower tip');
    }
    if(code==='C21'){
     const {face,eyes,horns}=result;assert.equal(horns.length,2);
     for(const b of [face,eyes])assert.ok(Math.abs(b.left+b.right-180)<.02,'C21 face/eyes reflect about center');
     assert.equal(result.eyeBoxes.length,2);assert.ok(Math.abs(result.eyeBoxes[0].left+result.eyeBoxes[1].right-180)<.02&&Math.abs(result.eyeBoxes[0].top-result.eyeBoxes[1].top)<.02,'C21 eyes share height and reflected extrema');
     assert.ok(Math.abs(horns[0].left+horns[1].right-180)<.02&&Math.abs(horns[0].top-horns[1].top)<.02,'C21 horn extrema remain mirrored');
     assert.ok((face.right-face.left)/(horns[1].right-horns[0].left)<.34,'C21 face should not crowd horn span');assert.ok(Math.abs(face.bottom-153)<.01);
    }
    if(code==='C26'){assert.deepEqual(result.coverage,[1,1,1,1],'C26 cardinal shaft intervals must each be stroked once');assert.equal(result.spokes.length,8);assert.equal(result.bridges.length,4);assert.ok(result.bridges.every(d=>d==='M90 28V39'));assert.ok(result.axes.every(d=>d==='M90 39V81'));for(const joint of result.joints)for(let i=0;i<6;i+=2)assert.ok(Math.hypot(joint[i][0]-joint[i+1][0],joint[i][1]-joint[i+1][1])<.01,'C26 transformed endpoints share exact joints');}
    if(code==='C30'){
     assert.equal(result.squares49.length,1,'C30 radius49 square cyclic duplicates');assert.equal(result.squares16.length,1,'C30 radius16 square cyclic duplicates');assert.equal(new Set(result.triangles).size,2);
     assert.equal(new Set(result.satellites.map(s=>s.id)).size,8);assert.equal(new Set(result.satellites.map(s=>s.sigil)).size,8);assert.equal(result.lights.length,4);assert.ok(result.lights.every(([x,y])=>Math.abs(Math.hypot(x-90,y-90)-51)<.01));
    }
    if(code==='A14'){
     assert.ok(result.mechanism/92>=.58&&result.mechanism/92<=.64,'A14 numeral belt needs room outside mechanism');
     assert.deepEqual(result.numbers.map(n=>n.text),['XII','I','II','III','IV','V','VI','VII','VIII','IX','X','XI']);
     result.numbers.forEach((n,i)=>{const angle=(i*30-90)*Math.PI/180;assert.ok(Math.abs((n.x-100)/68-Math.cos(angle))<.001&&Math.abs((n.y-100)/68-Math.sin(angle))<.001);assert.ok(Math.hypot(n.x-100,n.y-100)>result.mechanism+5&&Math.hypot(n.x-100,n.y-100)<82-5);});
     assert.deepEqual(result.hands,['M100 100L100 54M100 100L125 116']);
     assert.match(result.gear,/Z$/);const pts=result.gear.match(/-?\d+(?:\.\d+)?/g).map(Number);assert.equal(pts.length,192);
     for(let i=0;i<96;i++)assert.ok(Math.abs(Math.hypot(pts[i*2]-100,pts[i*2+1]-100)-(i%4===1||i%4===2?56:54.5))<.01,'A14 gear teeth repeat evenly');
    }
    if(code==='crimson-abyss'){
     assert.deepEqual(result.layers,['lattice','primary','secondary','thorns','core'],'Crimson layers must separate visual roles');assert.ok(result.hierarchy[0]<result.hierarchy[1]&&result.hierarchy[2]<result.hierarchy[3]);
     assert.equal(new Set(result.polygons).size,result.polygons.length,'Crimson closed polygons do not duplicate');
     const radii=result.primary.split(' ').map(p=>Math.hypot(...p.split(',').map(Number)));assert.equal(radii.filter(r=>Math.abs(r-183)<.02).length,8);
     assert.equal(result.thorns.length,8);assert.equal(new Set(result.thorns.map(t=>t.transform)).size,8);assert.ok(result.thorns.every(t=>/^M0-185.*0-185Z$/.test(t.d)));assert.ok(result.joins.every(j=>j==='round'));
    }
    console.log('ART_OK '+code);
   }catch(e){failures.push(code+': '+e.message);}
  }
  await page.setViewportSize({width:412,height:915});
  await page.clock.install();
  for(const code of ['C11','C12','C21','C26','C30','A14','crimson-abyss']){
   const crimson=code==='crimson-abyss';
   await page.goto(pathToFileURL(path.join(assets,crimson?'theme_circle.html':'collection_circle.html')).href+'?theme='+(crimson?code:'ref-'+code)+'&lang=ko&battery=78');
   await page.clock.runFor(20);
   assert.equal(await page.evaluate(()=>startChargingAnimation(7000)),true);
   const fixed=page.locator(crimson?'.fixed-seals path,.fixed-seals polygon':'[data-fixed] [data-outline] path');
   const matrices=()=>fixed.evaluateAll(es=>es.map(e=>{const m=e.getScreenCTM();return [m.a,m.b,m.c,m.d,m.e,m.f].map(v=>+v.toFixed(6));}));
   await page.evaluate(()=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=0;}));
   const start=await matrices();assert.ok(start.length>0);
   for(const ms of [1000,2800,3500,5200,6000,7000]){
    await page.evaluate(ms=>document.getAnimations().forEach(a=>a.currentTime=ms),ms);
    try{assert.deepEqual(await matrices(),start,code+' effective transforms at '+ms+'ms');}catch(e){failures.push(e.message.split('\n')[0]);}
    if(ms===3500&&process.env.ART_CAPTURE_DIR)await page.screenshot({path:path.join(process.env.ART_CAPTURE_DIR,code+'-playback-3500.png')});
   }
   assert.equal(await page.locator('.scene').evaluate(e=>getComputedStyle(e).opacity),'0');
   await page.clock.runFor(7100);
   console.log('ART_PLAYBACK_CHECKED '+code+' 0..7000ms');
  }
  await page.goto(pathToFileURL(path.join(assets,'theme_circle.html')).href+'?theme=classic');
  await page.evaluate(()=>startChargingAnimation(7000));
  const classicScale=async ms=>page.locator('.ritual').evaluate((e,ms)=>{document.getAnimations().forEach(a=>{a.pause();a.currentTime=ms;});return getComputedStyle(e).transform;},ms);
  assert.notEqual(await classicScale(0),await classicScale(3500),'Other themes retain their entrance zoom');
 }finally{await browser.close();}
 assert.deepEqual(failures,[]);
 console.log('V113_ART_OK: seven actual geometry and hierarchy regressions');
})().catch(e=>{console.error(e);process.exitCode=1;});
