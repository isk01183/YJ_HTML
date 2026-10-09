import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,readFileSync,writeFileSync,existsSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join,resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {hairRigFixture,glb,setFloat} from './hair-source.fixture.mjs';
import {readHairSource} from './inspect-hair-source.mjs';
import {signatures} from './compare-hair-source.mjs';
import {assembleHair} from './assemble-hair.mjs';

function pair() {
  const a=hairRigFixture(),b=hairRigFixture();
  for(const f of [a,b]) {
    f.json.nodes.push({name:'ear-tip',translation:[0,.3,0]});f.json.nodes[1].children.push(7);
    f.json.extensions.VRMC_springBone.colliders=[{node:1,shape:{sphere:{offset:[0,0,0],radius:.1}}}];
    f.json.extensions.VRMC_springBone.colliderGroups=[{name:'head',colliders:[0]}];
    f.json.extensions.VRMC_springBone.springs.push({name:'ear',center:1,joints:[{node:7,stiffness:2}],colliderGroups:[0]});
    f.json.extensions.VRMC_vrm.firstPerson={meshAnnotations:[{node:2,type:'both'},{node:3,type:'both'}]};
    f.json.extensions.VRMC_vrm.expressions.preset.blink.materialColorBinds=[{material:0,type:'color',targetValue:[1,1,1,1]}];
  }
  b.json.meshes[0].primitives.splice(1,1);
  b.json.nodes[5].translation=[0,.12,0];
  b.json.extensions.VRMC_springBone.springs[0].joints[0].stiffness=3;
  setFloat(b,b.position,9,.25);
  return [a,b];
}
const signature=f=>signatures(readHairSource(f));
const sha=b=>createHash('sha256').update(b).digest('hex');

test('assemblyUsesBaseBodyAndOnlyDonorHair',()=>{
  const [a,b]=pair(),base=glb(a),donor=glb(b),result=assembleHair(base,donor),out=signature(result.bytes),left=signature(base),right=signature(donor);
  assert.notEqual(sha(result.bytes),sha(donor));
  assert.equal(out.world,left.world);
  for(const [key,value] of left.shapes)if(!left.hair.has(key))assert.equal(out.shapes.get(key),value,key);
  assert.deepEqual([...out.hair],[...right.hair]);
  for(const key of right.hair)assert.equal(out.shapes.get(key),right.shapes.get(key));
  assert.equal(readHairSource(result.bytes).report.hair.length,1);
  assert.equal(result.provenance.protectedUnchanged,true);
  assert.equal(result.provenance.outputSha256,sha(result.bytes));
});

test('rewritesAllSkinSpringExpressionReferences',()=>{
  const [a,b]=pair(),output=assembleHair(glb(a),glb(b)).bytes,source=readHairSource(output),j=source.json;
  assert.equal(source.report.status,'inspected');
  assert.equal(signature(output).privateRig,signature(glb(b)).privateRig);
  assert.equal(j.nodes[j.extensions.VRMC_vrm.expressions.preset.blink.morphTargetBinds[0].node].name,'Face');
  assert.equal(j.materials[j.extensions.VRMC_vrm.expressions.preset.blink.materialColorBinds[0].material].name,a.json.materials[0].name);
  assert.equal(j.nodes[j.extensions.VRMC_springBone.springs.at(-1).joints[0].node].name,'hair-root');
});

test('preservesProtectedAccessoryPhysics',()=>{
  const [a,b]=pair(),j=readHairSource(assembleHair(glb(a),glb(b)).bytes).json;
  const ear=j.extensions.VRMC_springBone.springs.find(s=>s.name==='ear');
  assert.equal(j.nodes[ear.joints[0].node].name,'ear-tip');assert.equal(ear.joints[0].stiffness,2);
  assert.deepEqual(ear.colliderGroups,[0]);
  assert.equal(j.nodes[j.extensions.VRMC_springBone.colliders[0].node].name,'head');
  assert.ok(j.materials.some(m=>m.name.includes('RabbitTail')));
});

test('texturesKeepOriginalBytes',()=>{
  const [a,b]=pair();
  for(const f of [a,b]) {
    const image=Buffer.from('89504e470d0a1a0a0000000d494844520000000100000001','hex');
    f.json.bufferViews.push({buffer:0,byteOffset:f.bin.length,byteLength:image.length});
    f.bin=Buffer.concat([f.bin,image]);f.json.buffers[0].byteLength=f.bin.length;
    f.json.images=[{bufferView:f.json.bufferViews.length-1,mimeType:'image/png'}];
    f.json.textures=[{source:0}];
    f.json.materials[0].pbrMetallicRoughness.baseColorTexture={index:0};
    f.json.materials[2].pbrMetallicRoughness.baseColorTexture={index:0};
    f.json.extensions.VRMC_vrm.meta.thumbnailImage=0;
  }
  const source=readHairSource(assembleHair(glb(a),glb(b)).bytes),image=source.json.images[0],v=source.json.bufferViews[image.bufferView];
  assert.equal(source.json.images.length,1,'shared encoded image must not be duplicated');
  assert.equal(source.bin.subarray(v.byteOffset,v.byteOffset+v.byteLength).toString('hex'),'89504e470d0a1a0a0000000d494844520000000100000001');
  assert.equal(source.json.extensions.VRMC_vrm.meta.thumbnailImage,0);
});

test('refusesOverwriteAndLeavesInputsUntouched',()=>{
  const [a,b]=pair(),dir=mkdtempSync(join(tmpdir(),'vrm-assembly-')),base=join(dir,'base.vrm'),donor=join(dir,'donor.vrm'),out=join(dir,'out.vrm');
  writeFileSync(base,glb(a));writeFileSync(donor,glb(b));writeFileSync(out,'sentinel');
  const hashes=[sha(readFileSync(base)),sha(readFileSync(donor))];
  const run=target=>spawnSync(process.execPath,[resolve('tools/assemble-hair.mjs'),base,donor,target],{encoding:'utf8'});
  assert.equal(run(out).status,1);assert.equal(readFileSync(out,'utf8'),'sentinel');
  const fresh=join(dir,'new.vrm');writeFileSync(fresh+'.json','report sentinel');
  assert.equal(run(fresh).status,1);assert.equal(existsSync(fresh),false);
  assert.equal(readFileSync(fresh+'.json','utf8'),'report sentinel');
  const success=join(dir,'valid.vrm');assert.equal(run(success).status,0);
  assert.equal(JSON.parse(readFileSync(success+'.json')).protectedUnchanged,true);
  assert.deepEqual([sha(readFileSync(base)),sha(readFileSync(donor))],hashes);
});

test('invalidOrOversizedOutputIsNotPublished',()=>{
  const [a,b]=pair();setFloat(b,b.position,0,7);
  assert.throws(()=>assembleHair(glb(a),glb(b)),/candidate|Protected/);
  assert.throws(()=>assembleHair(glb(a),Buffer.alloc(64*1024*1024+4)),/size/);
  const dir=mkdtempSync(join(tmpdir(),'vrm-invalid-')),base=join(dir,'base.vrm'),donor=join(dir,'donor.vrm'),out=join(dir,'out.vrm');
  writeFileSync(base,glb(a));writeFileSync(donor,glb(b));
  assert.equal(spawnSync(process.execPath,[resolve('tools/assemble-hair.mjs'),base,donor,out]).status,1);
  assert.equal(existsSync(out),false);assert.equal(existsSync(out+'.json'),false);
});
