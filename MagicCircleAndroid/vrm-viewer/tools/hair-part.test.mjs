import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {hairRigFixture,glb,setFloat} from './hair-source.fixture.mjs';
import {readHairSource} from './inspect-hair-source.mjs';
import {signatures} from './compare-hair-source.mjs';
import {extractHairPart,inspectHairPart,composeHairPart} from './hair-part.mjs';

import {partFixturePair} from './hair-part.fixture.mjs';
const sha=b=>createHash('sha256').update(b).digest('hex');
const sig=b=>signatures(readHairSource(b));

test('bodyEmbeddedHairRoundTrip: part contains no protected geometry and preserves original image bytes',()=>{
  const [f]=partFixturePair(),base=glb(f),part=extractHairPart(base,base),info=inspectHairPart(part.json,part.bin);
  assert.equal(info.protectedPrimitiveCount,0);
  assert.deepEqual(info.anchors,['Body','hips/head']);
  assert.deepEqual(info.imageHashes,[sha(Buffer.from('89504e470d0a1a0a0000000d494844520000000100000001','hex'))]);
  assert.equal(info.partId,sha(Buffer.concat([part.json,part.bin])));
  const doc=JSON.parse(part.json);
  assert.equal(doc.geometry.reduce((n,m)=>n+m.mesh.primitives.length,0),2);
  assert.equal(doc.materials.some(m=>/SKIN|CLOTH/.test(m.name)),false);
  const after=sig(composeHairPart(base,part.json,part.bin).bytes),before=sig(base);
  assert.equal(after.world,before.world);
  assert.deepEqual(after.shapes,before.shapes);
  assert.equal(after.privateRig,before.privateRig);
});

test('standaloneHairPart: composition does not need donor and keeps face body clothes physics',()=>{
  const [a,b]=partFixturePair(),base=glb(a),donor=glb(b),part=extractHairPart(donor,base);
  const out=composeHairPart(base,part.json,part.bin).bytes,actual=sig(out),left=sig(base),right=sig(donor);
  assert.notEqual(sha(out),sha(donor));
  assert.equal(actual.world,left.world);
  assert.equal(actual.privateRig,right.privateRig);
  for(const [key,value] of left.shapes)if(!left.hair.has(key))assert.equal(actual.shapes.get(key),value,key);
  for(const key of right.hair)assert.equal(actual.shapes.get(key),right.shapes.get(key),key);
  assert.equal(readHairSource(out).json.extensions.VRMC_springBone.springs.find(s=>s.name==='ear').joints[0].stiffness,2);
  assert.equal(extractHairPart(donor,base).partId,part.partId);
});

test('part refuses changed protected face, mixed spring and unknown material',()=>{
  for(const mutate of [f=>setFloat(f,f.position,0,7),f=>f.json.extensions.VRMC_springBone.springs[0].joints.push({node:1}),f=>f.json.materials[2].name='unreviewed hair']) {
    const [a,b]=partFixturePair();mutate(b);
    assert.throws(()=>extractHairPart(glb(b),glb(a)));
  }
});

test('untrusted part cannot silently carry protected data, corrupt bytes or external resources',()=>{
  const [a,b]=partFixturePair(),base=glb(a),part=extractHairPart(glb(b),base);
  assert.throws(()=>inspectHairPart(part.json,part.bin.subarray(1)));
  const changed=Buffer.from(part.bin);changed[0]^=1;assert.throws(()=>inspectHairPart(part.json,changed));
  for(const mutate of [d=>d.kind='body',d=>d.materials[0].name='N00_000_00_Body_00_SKIN (Instance)',d=>d.images[0].uri='https://invalid.test/private',d=>d.bufferViews[0].byteOffset=Number.MAX_SAFE_INTEGER,d=>d.nodes[0].parent={local:99999},d=>d.hairDigest='0'.repeat(64)]) {
    const doc=JSON.parse(part.json);mutate(doc);
    assert.throws(()=>composeHairPart(base,Buffer.from(JSON.stringify(doc)),part.bin));
  }
  assert.throws(()=>inspectHairPart(Buffer.from('['.repeat(70)+'0'+']'.repeat(70)),part.bin));
  assert.throws(()=>composeHairPart(glb(b),part.json,part.bin));
});

test('body embedded hair inverse bind cannot change unnoticed when hair mesh uses another joint',()=>{
  const [a]=partFixturePair();
  // Keep only the mixed Body hair; its private bind still belongs to the visible hair shape.
  a.json.nodes=a.json.nodes.filter((_,i)=>i!==3);
  a.json.scenes[0].nodes=[0,2,3];a.json.nodes[1].children=[4,6];a.json.nodes[4].children=[5];
  a.json.skins[0].joints=[1,4,5];
  a.json.extensions.VRMC_vrm.expressions.preset.blink.morphTargetBinds[0].node=3;
  a.json.extensions.VRMC_springBone.springs[0].joints[0].node=4;
  a.json.extensions.VRMC_springBone.springs[0].joints[1].node=5;
  a.json.extensions.VRMC_springBone.springs[1].joints[0].node=6;
  const before=signatures(readHairSource(glb(a)));
  setFloat(a,a.inverseBind,16+12,.2);
  const after=signatures(readHairSource(glb(a)));
  assert.notDeepEqual([...before.hair].map(k=>before.shapes.get(k)),[...after.hair].map(k=>after.shapes.get(k)));
});
