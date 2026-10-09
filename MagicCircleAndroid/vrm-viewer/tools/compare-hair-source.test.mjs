import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,writeFileSync,readFileSync,readdirSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fixture,hairRigFixture,glb,setFloat} from './hair-source.fixture.mjs';
import * as comparison from './compare-hair-source.mjs';
const {compareHairSources}=comparison;

test('private hair rig changes are allowed but common head and accessory springs are protected',()=>{
  const f=hairRigFixture(),base=glb(f);f.json.nodes[5].translation=[0,.2,0];
  f.json.extensions.VRMC_springBone.springs[0].joints[0].stiffness=2;
  assert.equal(compareHairSources(base,glb(f)).status,'candidate');
  const r=comparison.hairAssemblyMap(base,glb(f));
  assert.equal(r.status,'candidate');assert.deepEqual(r.baseHairNodes,[5,6]);
  assert.deepEqual(r.donorHairSprings,[0]);assert.ok(r.commonNodes.some(n=>n.base===1&&n.donor===1));
  f.json.nodes[1].translation=[0,1.1,0];assert.equal(compareHairSources(base,glb(f)).status,'rejected');
});
test('mixedHairAccessorySpringIsRejected',()=>{
  const f=hairRigFixture(),base=glb(f);f.json.extensions.VRMC_springBone.springs[0].joints.unshift({node:1});
  assert.notEqual(compareHairSources(base,glb(f)).status,'candidate');
});

test('textureDisplayNameDoesNotChangeAppearance but sampler and pixels do',()=>{
  const f=fixture(),offset=f.bin.length;f.bin=Buffer.concat([f.bin,Buffer.from([1,2,3,4])]);
  f.json.buffers[0].byteLength=f.bin.length;f.json.bufferViews.push({buffer:0,byteOffset:offset,byteLength:4});
  f.json.images=[{bufferView:f.json.bufferViews.length-1,mimeType:'image/png'}];
  f.json.textures=[{name:'export_16',source:0}];f.json.materials[0].pbrMetallicRoughness.baseColorTexture={index:0};
  const base=glb(f);f.json.textures[0].name='export_15';
  assert.equal(compareHairSources(base,glb(f)).status,'identical');
  f.json.samplers=[{wrapS:33071}];f.json.textures[0].sampler=0;
  assert.equal(compareHairSources(base,glb(f)).status,'rejected');
});

test('hairAssemblyMap exists and rejects protected changes',()=>{
  assert.equal(typeof comparison.hairAssemblyMap,'function');
  const f=fixture(),base=glb(f);setFloat(f,f.position,0,.25);
  const r=comparison.hairAssemblyMap(base,glb(f));assert.equal(r.status,'rejected');
  assert.deepEqual(r.commonNodes,[]);
});

test('new exact Hair02 material is recognized without accepting unknown names',()=>{
  const f=fixture(),base=glb(f);f.json.materials[2].name='N00_000_Hair_00_HAIR (Instance)';
  setFloat(f,f.position,9,.4);
  assert.equal(compareHairSources(base,glb(f)).status,'candidate');
  f.json.materials[2].name='RandomHair';assert.equal(compareHairSources(base,glb(f)).status,'unsupported');
});

test('identicalIsNotANewHair',()=>{
  const bytes=glb(fixture()),r=compareHairSources(bytes,bytes);
  assert.equal(r.status,'identical');assert.equal(r.visualReviewRequired,true);assert.deepEqual(r.changes,[]);
});
test('isolatedHairIsOnlyACandidate: shared Body arrays do not make hair into clothing',()=>{
  const f=fixture(),base=glb(f);setFloat(f,f.position,9,.4);
  const r=compareHairSources(base,glb(f));assert.equal(r.status,'candidate');
  assert.equal(r.visualReviewRequired,true);assert.deepEqual(r.blockers,[]);
  assert.ok(r.changes.every(s=>s.startsWith('hair:')));
});

for(const [label,edit] of [
  ['face position',f=>setFloat(f,f.position,0,.2)],
  ['UV',f=>setFloat(f,f.uv,0,.2)],
  ['normal',f=>setFloat(f,f.normal,0,.2)],
  ['morph',f=>setFloat(f,f.morph,0,.2)],
  ['weight',f=>setFloat(f,f.weights,0,.5)],
  ['inverse bind',f=>setFloat(f,f.inverseBind,12,.2)],
  ['humanoid transform',f=>{f.json.nodes[1].translation=[0,1.1,0];}],
  ['rabbit ear',f=>{f.json.materials[3].pbrMetallicRoughness.baseColorFactor=[.5,1,1,1];}],
  ['expression',f=>{f.json.extensions.VRMC_vrm.expressions.preset.blink.morphTargetBinds[0].weight=.2;}],
  ['scene root',f=>{f.json.scenes[0].nodes.pop();}],
  ['mesh default weights',f=>{f.json.meshes[2].weights=[.5];}]
]) test(`protectsFaceOutfitAndRig: ${label}`,()=>{
  const f=fixture(),base=glb(f);edit(f);const r=compareHairSources(base,glb(f));
  assert.equal(r.status,'rejected');assert.ok(r.blockers.length>0);
});

test('protectsFaceOutfitAndRig: embedded clothing texture changes',()=>{
  const f=fixture(),offset=f.bin.length;f.bin=Buffer.concat([f.bin,Buffer.from([1,2,3,4])]);
  f.json.buffers[0].byteLength=f.bin.length;
  f.json.bufferViews.push({buffer:0,byteOffset:offset,byteLength:4});
  f.json.images=[{bufferView:f.json.bufferViews.length-1,mimeType:'image/png'}];f.json.textures=[{source:0}];
  f.json.materials[0].pbrMetallicRoughness.baseColorTexture={index:0};
  const base=glb(f);f.bin[offset]=9;
  assert.equal(compareHairSources(base,glb(f)).status,'rejected');
});

test('ambiguousPhysicsIsNotApproved: changed spring on a shared bone',()=>{
  const f=fixture();f.json.extensions.VRMC_springBone={specVersion:'1.0',springs:[{joints:[{node:1,stiffness:1}]}]};
  const base=glb(f);f.json.extensions.VRMC_springBone.springs[0].joints[0].stiffness=2;
  const r=compareHairSources(base,glb(f));assert.equal(r.status,'rejected');assert.ok(r.blockers.length);
});
test('ambiguousPhysicsIsNotApproved: duplicate paths and dangling colliders',()=>{
  const f=fixture(),base=glb(f);f.json.nodes[3].name='Body';
  assert.equal(compareHairSources(base,glb(f)).status,'unsupported');
  const g=fixture();g.json.extensions.VRMC_springBone={specVersion:'1.0',colliderGroups:[{colliders:[99]}]};
  assert.throws(()=>compareHairSources(base,glb(g)));
});
test('node and material reference renumbering is not an appearance change',()=>{
  const f=fixture(),base=glb(f), j=f.json;
  [j.materials[0],j.materials[1]]=[j.materials[1],j.materials[0]];
  for(const m of j.meshes)for(const p of m.primitives)if(p.material<2)p.material=1-p.material;
  [j.nodes[2],j.nodes[3]]=[j.nodes[3],j.nodes[2]];
  j.scenes[0].nodes=[0,3,2,4];
  assert.equal(compareHairSources(base,glb(f)).status,'identical');
});
test('unsupported input cannot be marked identical even when bytes match',()=>{
  const f=fixture();f.json.extensionsUsed.push('EXT_unknown');const bytes=glb(f);
  assert.equal(compareHairSources(bytes,bytes).status,'unsupported');
});
test('missingVariantPreservesInputs and CLI reports a real candidate',()=>{
  const dir=mkdtempSync(join(tmpdir(),'hair-compare-'));
  try {
    const cli=fileURLToPath(new URL('./compare-hair-source.mjs',import.meta.url)),f=fixture(),base=glb(f),a=join(dir,'base.vrm'),b=join(dir,'variant.vrm');
    writeFileSync(a,base);setFloat(f,f.position,9,.4);writeFileSync(b,glb(f));
    const run=spawnSync(process.execPath,[cli,a,b],{encoding:'utf8'});
    assert.equal(run.status,0,run.stderr);assert.equal(JSON.parse(run.stdout).status,'candidate');
    assert.equal(spawnSync(process.execPath,[cli,a,join(dir,'missing.vrm')]).status,1);
    assert.deepEqual(readFileSync(a),base);assert.deepEqual(readdirSync(dir),['base.vrm','variant.vrm']);
  } finally {rmSync(dir,{recursive:true,force:true});}
});

test('prototype-like root names cannot hide protected rig movement',()=>{
  const f=fixture();f.json.nodes.push({name:'__proto__',children:[0]});f.json.scenes[0].nodes[0]=5;
  const base=glb(f);f.json.nodes[5].translation=[1,0,0];setFloat(f,f.position,9,.4);
  const r=compareHairSources(base,glb(f));assert.equal(r.status,'rejected');assert.ok(r.blockers.length);
});

test('embedded motion cannot silently bypass protected geometry checks',()=>{
  const f=fixture(),offset=f.bin.length,bytes=Buffer.alloc(16);bytes.writeFloatLE(1,8);
  f.bin=Buffer.concat([f.bin,bytes]);f.json.buffers[0].byteLength=f.bin.length;
  const view=f.json.bufferViews.length,index=f.json.accessors.length;
  f.json.bufferViews.push({buffer:0,byteOffset:offset,byteLength:4},{buffer:0,byteOffset:offset+4,byteLength:12});
  f.json.accessors.push({bufferView:view,componentType:5126,type:'SCALAR',count:1},
    {bufferView:view+1,componentType:5126,type:'VEC3',count:1});
  f.json.animations=[{samplers:[{input:index,output:index+1}],channels:[{sampler:0,target:{node:1,path:'translation'}}]}];
  const base=glb(f);f.bin.writeFloatLE(99,offset+8);setFloat(f,f.position,9,.4);
  const r=compareHairSources(base,glb(f));assert.equal(r.status,'unsupported');
  assert.ok(r.blockers.some(s=>s.includes('animations')));
});

for(const repeatedPrimitives of [false,true]) test(`bounded comparison with repeated indices: shared=${repeatedPrimitives}`,()=>{
  const f=fixture(),count=300000,offset=f.bin.length,indices=Buffer.alloc(count*2);
  for(let i=0;i<count;i++)indices.writeUInt16LE(i%3,i*2);
  f.bin=Buffer.concat([f.bin,indices]);f.json.buffers[0].byteLength=f.bin.length;
  const view=f.json.bufferViews.length,index=f.json.accessors.length;
  f.json.bufferViews.push({buffer:0,byteOffset:offset,byteLength:indices.length});
  f.json.accessors.push({bufferView:view,componentType:5123,type:'SCALAR',count});
  for(const mesh of f.json.meshes)for(const p of mesh.primitives)
    if(repeatedPrimitives&&p.indices===5)p.indices=index;
  f.json.meshes[0].primitives[0].indices=index;
  const dir=mkdtempSync(join(tmpdir(),'hair-budget-'));
  try {
    const a=join(dir,'a.vrm'),b=join(dir,'b.vrm'),cli=fileURLToPath(new URL('./compare-hair-source.mjs',import.meta.url));
    writeFileSync(a,glb(f));setFloat(f,f.position,9,.4);writeFileSync(b,glb(f));
    const run=spawnSync(process.execPath,['--max-old-space-size=128',cli,a,b],{encoding:'utf8',timeout:20000});
    assert.equal(run.status,repeatedPrimitives?2:0,run.stderr);
    const r=JSON.parse(run.stdout);assert.equal(r.status,repeatedPrimitives?'unsupported':'candidate');
    if(repeatedPrimitives)assert.ok(r.blockers.some(s=>s.includes('Comparison work limit')));
  } finally {rmSync(dir,{recursive:true,force:true});}
});
