import { test } from 'node:test';
import assert from 'node:assert/strict';
import { Texture, Mesh, BoxGeometry, MeshBasicMaterial, Scene } from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import * as memoryHelpers from './model-memory.js';
const {ModelMemory,imageDimensions}=memoryHelpers;

const MiB=1024*1024;
function png(width,height) {
  const bytes=new Uint8Array(24),view=new DataView(bytes.buffer);
  bytes.set([137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82]);
  view.setUint32(16,width);view.setUint32(20,height);return bytes;
}
function parserFor(images=[png(4096,4096)],load=()=>{}) {
  const body=new Uint8Array(images.reduce((sum,image)=>sum+image.length,0));let offset=0;
  const bufferViews=images.map(image=>{body.set(image,offset);const view={buffer:0,byteOffset:offset,byteLength:image.length};offset+=image.length;return view;});
  return {json:{buffers:[{byteLength:body.length}],bufferViews,
    images:images.map((_,bufferView)=>({bufferView,mimeType:'image/png'})),
    textures:images.map((_,source)=>({source})),accessors:[]},
    extensions:{KHR_binary_glTF:{body:body.buffer}},textureLoader:{isImageBitmapLoader:true,load}};
}
function bitmap(width=4096,height=4096) {return {width,height,closes:0,close(){this.closes++;}};}
function loadImage(parser,url) {return new Promise((resolve,reject)=>parser.textureLoader.load(url,resolve,undefined,reject));}
const flush=()=>new Promise(resolve=>setImmediate(resolve));

test('reads original PNG and JPEG dimensions without decoding or resizing',()=>{
  assert.deepEqual(imageDimensions(png(4096,2048),'image/png'),{width:4096,height:2048});
  const jpeg=Uint8Array.from([255,216,255,224,0,4,0,0,255,194,0,11,8,8,0,16,0,1,1,17,0]);
  assert.deepEqual(imageDimensions(jpeg,'image/jpeg'),{width:4096,height:2048});
  for(const [data,mime] of [[png(0,1),'image/png'],[png(4097,1),'image/png'],[jpeg.slice(0,17),'image/jpeg'],[png(2,2),'image/jpeg']]) {
    assert.throws(()=>imageDimensions(data,mime));
  }
});

test('refuses unavailable budgets and over-budget models before the first decode',()=>{
  for(const budget of [0,-1,NaN,Infinity,undefined])assert.throws(()=>new ModelMemory(budget,{}),{kind:'MEMORY'});
  const info={},memory=new ModelMemory(100*MiB,info),parser=parserFor();let calls=0;
  parser.textureLoader.load=()=>{calls++;};
  assert.throws(()=>memory.plugin(parser).beforeRoot(),{kind:'MEMORY'});
  assert.equal(calls,0);assert.equal(info.texturePixels,16777216);
  assert.ok(info.estimatedBytes>200*MiB);
});

test('rejects textures exceeding the GPU limit instead of letting Three resize them',()=>{
  const memory=new ModelMemory(768*MiB,{});
  assert.throws(()=>memory.plugin(parserFor(),2048).beforeRoot(),/GPU/);
});

test('rejects overlapping buffer views whose separate slices exceed the memory budget',()=>{
  const parser=parserFor([]),memory=new ModelMemory(96*MiB,{});
  parser.extensions.KHR_binary_glTF.body=new ArrayBuffer(16*MiB);
  parser.json.buffers[0].byteLength=16*MiB;
  parser.json.bufferViews=Array.from({length:40},()=>({buffer:0,byteOffset:0,byteLength:16*MiB}));
  assert.throws(()=>memory.plugin(parser).beforeRoot(),{kind:'MEMORY'});
});

test('first-frame GPU upload failure reports MEMORY even after another GL error',()=>{
  const errors=[1280,1285,0];let rendered=false;
  const renderer={render(){rendered=true;},getContext(){return {NO_ERROR:0,OUT_OF_MEMORY:1285,getError:()=>errors.shift() ?? 0};}};
  assert.equal(typeof memoryHelpers.renderFrame,'function');
  assert.throws(()=>memoryHelpers.renderFrame(renderer,{}, {},true),{kind:'MEMORY'});
  assert.equal(rendered,true);
});

test('render exceptions and other GL failures report CONTEXT, while healthy frames succeed',()=>{
  assert.equal(typeof memoryHelpers.renderFrame,'function');
  const renderer={render(){throw new Error('upload failed');}};
  assert.throws(()=>memoryHelpers.renderFrame(renderer,{},{}),{kind:'CONTEXT'});
  let rendered=0;const errors=[1282,0];
  renderer.render=()=>rendered++;
  renderer.getContext=()=>({NO_ERROR:0,OUT_OF_MEMORY:1285,getError:()=>errors.shift() ?? 0});
  assert.throws(()=>memoryHelpers.renderFrame(renderer,{}, {},true),{kind:'CONTEXT'});
  memoryHelpers.renderFrame(renderer,{}, {},true);
  assert.equal(rendered,2);
});

test('accounts for accessors, repeated texture sources and a single decode scratch image',()=>{
  const info={},memory=new ModelMemory(768*MiB,info),parser=parserFor([png(1024,1024),png(512,512)]);
  parser.json.textures.push({source:0,sampler:1});
  parser.json.accessors=[{count:1000,type:'VEC3',componentType:5126},{count:2000,type:'SCALAR',componentType:5123,sparse:{count:1}}];
  memory.plugin(parser).beforeRoot();
  // 32 MiB margin + 240 encoded/view bytes + 80,000 accessor bytes + 5 MiB CPU + 12 MiB GPU + 4 MiB scratch.
  assert.equal(info.estimatedBytes,55654768);
  assert.equal(info.texturePixels,1310720);assert.equal(info.budgetBytes,805306368);
});

test('serializes decodes and retains active bitmaps until disposal, closing each unique bitmap once',async()=>{
  const callbacks=[],info={},image=bitmap(),memory=new ModelMemory(768*MiB,info);
  const parser=parserFor([png(4096,4096)],(url,ok)=>callbacks.push({url,ok}));
  memory.plugin(parser).beforeRoot();
  const first=loadImage(parser,'blob:one'),second=loadImage(parser,'blob:two');
  await flush();assert.deepEqual(callbacks.map(c=>c.url),['blob:one']);
  callbacks[0].ok(image);await first;await flush();
  assert.equal(image.closes,0);assert.equal(callbacks.length,2);
  callbacks[1].ok(image);await second;
  assert.equal(info.peakDecodes,1);assert.equal(info.decodedImages,1);
  assert.equal(info.activeDecodes,0);assert.equal(info.liveBitmaps,1);
  memory.dispose();memory.dispose();assert.equal(image.closes,1);assert.equal(info.liveBitmaps,0);
});

test('disposal cancels queued work and closes a bitmap arriving after disposal',async()=>{
  const callbacks=[],info={},memory=new ModelMemory(768*MiB,info),image=bitmap();
  const parser=parserFor(undefined,(url,ok)=>callbacks.push(ok));memory.plugin(parser).beforeRoot();
  const first=loadImage(parser,'blob:one'),second=loadImage(parser,'blob:two');
  const rejected=Promise.all([assert.rejects(first),assert.rejects(second)]);
  await flush();assert.equal(info.activeDecodes,1);memory.dispose();callbacks[0](image);await rejected;
  assert.equal(callbacks.length,1);assert.equal(image.closes,1);assert.equal(info.decodedImages,0);
  assert.equal(info.activeDecodes,0);assert.equal(info.liveBitmaps,0);
});

test('a decode error closes earlier images and remains fatal even if the loader swallows it',async()=>{
  const callbacks=[],memory=new ModelMemory(768*MiB,{}),image=bitmap();
  const parser=parserFor(undefined,(url,ok,progress,fail)=>callbacks.push({ok,fail}));memory.plugin(parser).beforeRoot();
  const first=loadImage(parser,'blob:one');await flush();callbacks[0].ok(image);await first;
  const second=loadImage(parser,'blob:two'),third=loadImage(parser,'blob:three');
  const rejected=Promise.all([assert.rejects(second,/decode failed/),assert.rejects(third,/decode failed/)]);
  await flush();callbacks[1].fail(new Error('decode failed'));await rejected;
  assert.equal(callbacks.length,2);assert.equal(image.closes,1);
  assert.throws(()=>memory.check(),/decode failed/);
});

test('GPU accounting distinguishes color-space variants sharing one bitmap',()=>{
  const info={},memory=new ModelMemory(280*MiB,info),parser=parserFor();memory.plugin(parser).beforeRoot();
  const a=new Texture(bitmap()),b=a.clone();b.colorSpace='srgb';
  const scene=new Scene();scene.add(new Mesh(new BoxGeometry(),new MeshBasicMaterial({map:a,alphaMap:b})));
  assert.throws(()=>memory.check({scenes:[scene]}),{kind:'MEMORY'});
  scene.children[0].geometry.dispose();scene.children[0].material.dispose();a.dispose();b.dispose();memory.dispose();
});

test('pinned GLTFLoader cannot report a failed image as a successful guarded model',async()=>{
  const oldSelf=globalThis.self,oldCreate=globalThis.createImageBitmap;
  globalThis.self=globalThis;globalThis.createImageBitmap=async()=>{throw new Error('decode failed');};
  const memory=new ModelMemory(768*MiB,{}),loader=new GLTFLoader();
  loader.register(parser=>memory.plugin(parser));
  const parser=parserFor([png(1,1)]);
  const json={asset:{version:'2.0'},...parser.json,scenes:[{nodes:[0]}],nodes:[{mesh:0}],
    meshes:[{primitives:[{attributes:{POSITION:0},material:0}]}],
    accessors:[{componentType:5126,count:3,type:'VEC3',min:[0,0,0],max:[1,1,0]}],
    materials:[{pbrMetallicRoughness:{baseColorTexture:{index:0}}}]};
  const encoded=new TextEncoder().encode(JSON.stringify(json));const jsonSize=Math.ceil(encoded.length/4)*4;
  const data=new ArrayBuffer(12+8+jsonSize+8+24),view=new DataView(data),bytes=new Uint8Array(data);
  view.setUint32(0,0x46546c67,true);view.setUint32(4,2,true);view.setUint32(8,data.byteLength,true);
  view.setUint32(12,jsonSize,true);view.setUint32(16,0x4e4f534a,true);bytes.fill(32,20,20+jsonSize);bytes.set(encoded,20);
  view.setUint32(20+jsonSize,24,true);view.setUint32(24+jsonSize,0x004e4942,true);bytes.set(png(1,1),28+jsonSize);
  try {const gltf=await loader.parseAsync(data,'');assert.throws(()=>memory.check(gltf),/decode failed/);}
  finally {memory.dispose();globalThis.self=oldSelf;globalThis.createImageBitmap=oldCreate;}
});
