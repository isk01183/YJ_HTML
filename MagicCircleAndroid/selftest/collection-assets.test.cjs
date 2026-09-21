const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),zlib=require('node:zlib'),crypto=require('node:crypto');
const {pathToFileURL}=require('node:url'),{chromium}=require('playwright'),sharp=require('sharp');
const refs=require('../app/src/main/assets/collection-catalog.js'),{validateSvg}=require('../tools/vector-pack.cjs');
const assets=path.resolve(__dirname,'../app/src/main/assets'),folder=path.join(assets,'collection');
(async()=>{
 const expected=refs.list.map(t=>t.code).sort();
 assert.deepEqual(fs.readdirSync(path.join(folder,'art')).map(f=>f.replace(/\.svgz$/,'')).sort(),expected);
 assert.deepEqual(fs.readdirSync(path.join(folder,'thumbs')).map(f=>f.replace(/\.png$/,'')).sort(),expected);
 const report=JSON.parse(fs.readFileSync(path.join(folder,'pack-report.json'),'utf8'));
 assert.equal(report.count,118);assert.deepEqual(report.records.map(r=>r.id).sort(),expected);
 for(const record of report.records){
  const packed=fs.readFileSync(path.join(folder,'art',record.id+'.svgz')),svg=zlib.gunzipSync(packed);
  validateSvg(svg.toString());assert.equal(packed.length,record.gzipBytes);assert.equal(svg.length,record.svgBytes);
  assert.equal(crypto.createHash('sha256').update(svg).digest('hex'),record.svgSha256);
  const metadata=await sharp(path.join(folder,'thumbs',record.id+'.png')).metadata();assert.equal(metadata.width,320);assert.equal(metadata.height,320);
 }
 const browser=await chromium.launch({headless:true,channel:'msedge'}),records=[];
 const qa=process.argv.find(a=>a.startsWith('--qa='))?.slice(5);
 if(qa)fs.mkdirSync(qa,{recursive:true});
 try{
  const page=await browser.newPage({viewport:{width:480,height:1040},deviceScaleFactor:3});
  await page.context().setOffline(true);let errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.route('https://appassets.androidplatform.net/collection/**',async route=>{
   const id=/\/([A-Z]\d{2})\.svg/.exec(route.request().url())?.[1];assert.ok(expected.includes(id));
   await route.fulfill({contentType:'image/svg+xml',headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; style-src 'unsafe-inline'; sandbox"},body:zlib.gunzipSync(fs.readFileSync(path.join(folder,'art',id+'.svgz')))});
  });
  for(const theme of refs.list){
   const start=performance.now();
   await page.goto(pathToFileURL(path.join(assets,'collection_circle.html')).href+'?theme='+theme.id+'&lang=ko&battery=78&run='+Date.now(),{timeout:20000});
   const loadMs=performance.now()-start;
   assert.equal(await page.locator('[data-direct-circle]').count(),1,theme.id+' must use native paths');
   assert.equal(await page.locator('[data-direct-circle]').getAttribute('data-direct-circle'),theme.code);
   assert.equal(await page.locator('#collection-art').count(),0,'No legacy contour image in the new charging renderer');
   assert.equal(await page.evaluate(ms=>window.startChargingAnimation(Math.max(0,7000-ms)),loadMs),true,theme.id+' must be ready inside the connection budget on this PC');
   await page.evaluate(()=>document.getAnimations().forEach(a=>{a.pause();a.currentTime=3500;}));
   const save=qa&&['C03','R01','W03','F05','G01','U04','A04','B17'].includes(theme.code);
   await page.screenshot(save?{path:path.join(qa,theme.code+'-phone.png')}:{});
   const paintedMs=Math.round(performance.now()-start);
   assert.ok(paintedMs<7000,theme.id+' actual first painted frame missed the PC 7-second budget: '+paintedMs);
   records.push({id:theme.code,pageLoadMs:Math.round(loadMs),paintedFrameMs:paintedMs});
   if(records.length%20===0)console.log('SVG_RENDER_PROGRESS '+records.length+'/118');
  }
  assert.deepEqual(errors,[]);
  records.sort((a,b)=>b.paintedFrameMs-a.paintedFrameMs);
  const result={count:records.length,platform:'Windows Edge headless; 480x1040 CSS pixels, DPR3; offline synthetic Android URL emulation, not Android device',slowest:records.slice(0,10),records};
  if(qa)fs.writeFileSync(path.join(qa,'render-report.json'),JSON.stringify(result,null,2));
  console.log('COLLECTION_ASSETS_OK '+JSON.stringify({count:118,gzipBytes:report.gzipTotalBytes,slowest:result.slowest}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
