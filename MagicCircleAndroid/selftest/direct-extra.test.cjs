'use strict';
// Catches missing catalog entries, malformed/blank SVG, raster fallbacks, broken
// closed contours, non-finite geometry, clipped aspect ratios and network use.
const assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path');
const root=path.resolve(__dirname,'..'), output=path.join(root,'docs');
const ids=['A','B'].flatMap(g=>Array.from({length:30},(_,i)=>g+String(i+1).padStart(2,'0')))
  .concat(['E01','E02','E03','E04','E05','E06','E07','E08','F01','F02','F03','F04','F05','G01','G02','G03','G04','G05','U01','U02','U03','U04','W01','W02','W04','W05']);
const reference=path.resolve(root,'../magic-circle-master/reference');
async function main(){
  if(process.argv.includes('--sources')) {
    const sharp=require('sharp'), manifest=JSON.parse(fs.readFileSync(path.join(reference,'manifest.json')));
    for(const group of ['A','B','E','F','G','U','W']){
      const assets=manifest.assets.filter(a=>ids.includes(a.id)&&a.group===group), size=group==='W'||group==='U'?400:300, columns=group==='A'||group==='B'?5:4;
      const composite=[];
      for(let i=0;i<assets.length;i++){
        const a=assets[i], x=i%columns*size,y=Math.floor(i/columns)*(size+26);
        composite.push({input:await sharp(path.join(reference,a.native_path)).resize(size,size,{fit:'contain',background:'#030609'}).png().toBuffer(),left:x,top:y});
        composite.push({input:Buffer.from(`<svg width="${size}" height="26"><text x="8" y="19" fill="white" font-size="18">${a.id}</text></svg>`),left:x,top:y+size});
      }
      await sharp({create:{width:columns*size,height:Math.ceil(assets.length/columns)*(size+26),channels:3,background:'#030609'}}).composite(composite).png().toFile(path.join(output,`remaining-source-${group}.png`));
    }
    console.log('Native source contact sheets written (comparison only).');return;
  }
  const file=path.join(root,'app/src/main/assets/direct-extra.js');
  assert(fs.existsSync(file),'remaining 86 direct drawings must exist');
  require(file);
  assert.deepEqual([...globalThis.DirectCircleExtra.ids].sort(),[...ids].sort());
  assert(Object.isFrozen(DirectCircleExtra));
  assert.throws(()=>DirectCircleExtra.svg('C03'),RangeError);
  const {chromium}=require('playwright');
  const browser=await chromium.launch({channel:'msedge',headless:true});
  try{
    const context=await browser.newContext({viewport:{width:1000,height:1000},offline:true}),page=await context.newPage();
    const errors=[];page.on('pageerror',e=>errors.push(String(e)));
    const network=[];page.on('request',r=>network.push(r.url()));
    for(const id of ids){
      const svg=DirectCircleExtra.svg(id);
      assert.equal(svg,DirectCircleExtra.svg(id),id+' deterministic');
      assert(!/<(?:image|foreignObject|script|use|animate)\b|(?:href=|data:|NaN|Infinity)/i.test(svg),id+' native static geometry');
      await page.setContent(`<style>body{margin:0;background:#020509}svg{display:block;width:100%;height:100vh}</style>${svg}`);
      if(id==='A19'){
        const width=await page.evaluate(()=>{const e=document.querySelector('[data-outline="center-core"] [data-fixed="crystal"]'),m=e.getCTM(),s=document.querySelector('svg').getCTM();return parseFloat(getComputedStyle(e).strokeWidth)*Math.hypot(m.a,m.b)/Math.hypot(s.a,s.b);});
        assert(width<2,'normalized crystal must keep fine contour width after scaling');
      }
      if(['A09','B18','B24','W01','W02','W04','W05'].includes(id)){
        const detail=await page.evaluate(id=>{
          const s=document.querySelector('svg'),hit=(selector,x,y)=>{const e=s.querySelector(selector);if(!e)return false;const point=new DOMPoint(x,y).matrixTransform(s.getCTM()).matrixTransform(e.getCTM().inverse());return e.isPointInFill(point);};
          const box=name=>{const e=s.querySelector(`[data-tree-part="${name}"]`);if(!e)return null;const b=e.getBBox();return {x:b.x,y:b.y,width:b.width,height:b.height};};
          return {moonLower:hit('[data-moon="central"]',100,126),moonUpper:hit('[data-moon="central"]',100,74),
            topLower:hit('[data-moon="top"]',100,40),topUpper:hit('[data-moon="top"]',100,14),
            leftLit:hit('[data-moon="left"]',35,100),leftDark:hit('[data-moon="left"]',17,100),
            rightLit:hit('[data-moon="right"]',165,100),rightDark:hit('[data-moon="right"]',183,100),
            lotus:(()=>{const b=s.querySelector('[data-lotus="upright"]')?.getBBox();return b?{x:b.x,y:b.y,width:b.width,height:b.height}:null;})(),
            lotusTip:hit('[data-lotus="upright"] path[data-lotus-petal="center"]',100,82),
            crown:box('crown'),trunk:box('trunk'),roots:box('roots')};
        },id);
        const requiredContours=await page.evaluate(()=>[...document.querySelectorAll('[data-moon], [data-lotus-petal="center"], [data-tree-part="trunk"] path')].map(e=>e.getAttribute('d')));
        assert(requiredContours.length>0&&requiredContours.every(d=>/Z\s*$/i.test(d)),id+' required silhouettes explicitly close independently of data-closed');
        if(id==='A09')assert(detail.moonLower&&!detail.moonUpper,'A09 central crescent opens upward');
        if(id==='B18')assert(detail.topLower&&!detail.topUpper&&detail.leftLit&&!detail.leftDark&&detail.rightLit&&!detail.rightDark,'B18 vertical top crescent and facing lateral half moons');
        if(id==='B24')assert(detail.lotus&&detail.lotus.y<60&&detail.lotus.height>65&&Math.abs(detail.lotus.x+detail.lotus.width/2-100)<1&&detail.lotusTip,'B24 upright centered lotus reaches above its base');
        if(id[0]==='W'){
          assert(detail.crown&&detail.trunk&&detail.roots,id+' distinct authored tree regions');
          assert(detail.crown.width>105&&detail.crown.height>55&&detail.crown.y<130,id+' broad branch silhouette');
          assert(detail.trunk.height>55&&detail.trunk.width>10&&detail.trunk.width<50,id+' tapering central trunk');
          assert(detail.roots.width>80&&detail.roots.y>=180,id+' roots spread below trunk');
          if(id==='W01')assert(detail.roots.y>=200&&detail.roots.y+detail.roots.height>=245,'W01 fine roots descend beneath broad mint crown');
          if(id==='W02')assert(detail.crown.y<111&&detail.roots.height>35,'W02 tall blossom crown and curled roots');
          if(id==='W04')assert(detail.roots.y<190&&detail.roots.y+detail.roots.height>255,'W04 high side tendril and low central root drop');
          if(id==='W05')assert(detail.trunk.width<30&&detail.roots.y+detail.roots.height>=258,'W05 slender intertwined trunk and pointed root pendant');
        }
      }
      const result=await page.evaluate(()=>{
        const s=document.querySelector('svg'),doc=new DOMParser().parseFromString(s.outerHTML,'image/svg+xml');
        const closed=[...s.querySelectorAll('path[data-closed]')];
        return {error:!!doc.querySelector('parsererror'),title:!!s.querySelector('title')?.textContent,
          preserve:s.getAttribute('preserveAspectRatio'),layers:[...s.querySelectorAll('[data-layer]')].map(e=>e.dataset.layer),
          lengths:[...s.querySelectorAll('path')].map(e=>e.getTotalLength()),closed:closed.length,
          bad:closed.filter(e=>!/Z\s*$/i.test(e.getAttribute('d'))).length,
          ids:[...s.querySelectorAll('[id]')].map(e=>e.id),fixed:!!s.querySelector('[data-fixed]'),
          width:s.getBoundingClientRect().width,scroll:document.documentElement.scrollWidth};
      });
      assert(!result.error&&result.title&&result.fixed,id+' parsed/titled/fixed');
      assert.equal(result.preserve,'xMidYMid meet',id);
      for(const layer of ['outer-ring','rune-ring','geometry','center-core','particles'])assert(result.layers.includes(layer),id+' '+layer);
      assert(result.closed>0&&result.bad===0,id+' closed contours');
      assert(result.lengths.length>10&&result.lengths.every(Number.isFinite),id+' valid paths');
      assert.equal(new Set(result.ids).size,result.ids.length,id+' unique IDs');
      assert(result.ids.every(x=>x.startsWith(id+'-')),id+' ID namespace');
      assert.equal(result.width,result.scroll,id+' viewport fits');
      for(const width of [320,768]){
        await page.setViewportSize({width,height:width===320?700:1024});
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),width,id+' responsive');
      }
      await page.setViewportSize({width:1000,height:1000});
    }
    assert.deepEqual(errors,[]);assert.deepEqual(network,[]);
    for(const group of ['A','B','E','F','G','U','W']){
      const groupIds=ids.filter(id=>id.startsWith(group));
      await page.setViewportSize({width:1500,height:1000});
      await page.setContent(`<style>body{margin:0;background:#020509;color:#dae9f1;font:18px sans-serif;display:grid;grid-template-columns:repeat(5,1fr)}article{width:300px}svg{width:300px;height:${group==='W'?534:300}px;display:block}b{display:block;padding:7px}</style>`+groupIds.map(id=>`<article>${DirectCircleExtra.svg(id)}<b>${id}</b></article>`).join(''));
      await page.screenshot({path:path.join(output,`remaining-render-${group}.png`),fullPage:true});
    }
    const manifest=JSON.parse(fs.readFileSync(path.join(reference,'manifest.json')));
    const comparisons=['A14','B19','E01','F01','W04'];
    await page.setViewportSize({width:1000,height:900});
    await page.setContent('<style>body{margin:0;background:#020509;color:#dbe5ed;font:20px sans-serif}.row{display:grid;grid-template-columns:1fr 1fr}figure{margin:0;padding:12px}img,svg{display:block;width:100%;height:470px;object-fit:contain}.tree img,.tree svg{height:850px}</style>'+comparisons.map(id=>{
      const asset=manifest.assets.find(a=>a.id===id),data=fs.readFileSync(path.join(reference,asset.native_path)).toString('base64');
      return `<section class="row ${id[0]==='W'?'tree':''}"><figure><figcaption>${id} native reference</figcaption><img src="data:image/png;base64,${data}"></figure><figure><figcaption>${id} directly authored SVG</figcaption>${DirectCircleExtra.svg(id)}</figure></section>`;
    }).join(''));
    await page.screenshot({path:path.join(output,'remaining-comparison.png'),fullPage:true});
    const reviewIds=['A09','B18','B24','W01','W02','W04','W05'];
    await page.setContent('<style>body{margin:0;background:#020509;color:#dbe5ed;font:20px sans-serif}.row{display:grid;grid-template-columns:1fr 1fr}figure{margin:0;padding:12px}img,svg{display:block;width:100%;height:470px;object-fit:contain}.tree img,.tree svg{height:850px}</style>'+reviewIds.map(id=>{
      const asset=manifest.assets.find(a=>a.id===id),data=fs.readFileSync(path.join(reference,asset.native_path)).toString('base64');
      return `<section class="row ${id[0]==='W'?'tree':''}"><figure><figcaption>${id} native reference</figcaption><img src="data:image/png;base64,${data}"></figure><figure><figcaption>${id} review correction</figcaption>${DirectCircleExtra.svg(id)}</figure></section>`;
    }).join(''));
    await page.screenshot({path:path.join(output,'remaining-review-fixes.png'),fullPage:true});
    console.log(`PASS: ${ids.length} direct drawings, Edge SVG parsing/path closure, 320/768/1000px, offline, seven render sheets.`);
  }finally{await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
