import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,writeFileSync,readFileSync,readdirSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fixture,glb,setFloat} from './hair-source.fixture.mjs';
import {compareHairSources} from './compare-hair-source.mjs';

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
