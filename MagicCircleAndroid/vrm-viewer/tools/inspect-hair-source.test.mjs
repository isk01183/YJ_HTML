import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {mkdtempSync,writeFileSync,readFileSync,readdirSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {fixture,glb,setFloat} from './hair-source.fixture.mjs';
import {inspectHairSource} from './inspect-hair-source.mjs';

test('classifiesHairWithoutTakingBody',()=>{
  const bytes=glb(fixture()), before=Buffer.from(bytes), report=inspectHairSource(bytes);
  assert.equal(report.status,'inspected');
  assert.deepEqual(report.hair.map(p=>[p.mesh,p.primitive]),[[0,1],[1,0]]);
  assert.deepEqual(report.protected.map(p=>p.material),[0,3,4,5]);
  assert.equal(report.sha256,createHash('sha256').update(bytes).digest('hex'));
  assert.deepEqual(bytes,before);
});

for(const [label,edit] of [
  ['external buffer', f=>{f.json.buffers[0].uri='https://example.invalid/a.bin';}],
  ['external image', f=>{f.json.images=[{uri:'../secret.png'}];}],
  ['cyclic nodes', f=>{f.json.nodes[1].children=[0];}],
  ['buffer view outside BIN',f=>{f.json.bufferViews[0].byteLength=1e9;}],
  ['accessor outside view',f=>{f.json.accessors[0].count=1e9;}],
  ['invalid primitive index',f=>{f.json.meshes[0].primitives[0].indices=999;}],
  ['invalid vertex index',f=>{f.bin.writeUInt16LE(999,f.json.bufferViews[5].byteOffset);} ],
  ['NaN geometry',f=>{setFloat(f,f.position,0,NaN);} ],
  ['invalid node reference',f=>{f.json.skins[0].joints=[999];}],
  ['invalid spring reference',f=>{f.json.extensions.VRMC_springBone={specVersion:'1.0',springs:[{joints:[{node:999}]}]};}]
]) test(`rejectsUnsafeInput: ${label}`,()=>{
  const f=fixture();edit(f);const bytes=glb(f), before=Buffer.from(bytes);
  assert.throws(()=>inspectHairSource(bytes));assert.deepEqual(bytes,before);
});

test('rejectsUnsafeInput: GLB and JSON limits',()=>{
  const good=glb(fixture());
  assert.throws(()=>inspectHairSource(good.subarray(0,-4)));
  const bad=Buffer.from(good);bad.writeUInt32LE(1,8);assert.throws(()=>inspectHairSource(bad));
  assert.throws(()=>inspectHairSource(new Uint8Array(64*1024*1024+1)));
  const f=fixture();f.json.extras={padding:' '.repeat(4*1024*1024)};
  assert.throws(()=>inspectHairSource(glb(f)));
});

for(const [label,edit] of [
  ['sparse',f=>{f.json.accessors[0].sparse={count:1,indices:{bufferView:5,componentType:5123},values:{bufferView:0}};}],
  ['stride',f=>{f.json.bufferViews[0].byteStride=12;}],
  ['compression',f=>{f.json.extensionsUsed.push('KHR_draco_mesh_compression');}],
  ['unknown nested extension',f=>{f.json.materials[0].extensions={EXT_unknown:{}};}],
  ['unknown material',f=>{f.json.materials[0].name='NotHairButContainsHair';}],
  ['duplicate material name',f=>{f.json.materials[0].name=f.json.materials[1].name;}],
  ['missing material name',f=>{delete f.json.materials[0].name;}]
]) test(`unsupportedLayoutsAreExplicit: ${label}`,()=>{
  const f=fixture();edit(f);const report=inspectHairSource(glb(f));
  assert.equal(report.status,'unsupported');assert.ok(report.reasons.length>0);
});

test('CLI reads only the supplied file and never prints its binary or metadata',()=>{
  const dir=mkdtempSync(join(tmpdir(),'hair-inspect-'));
  try {
    const file=join(dir,'source.vrm'), bytes=glb(fixture());writeFileSync(file,bytes);
    const cli=fileURLToPath(new URL('./inspect-hair-source.mjs',import.meta.url));
    const run=spawnSync(process.execPath,[cli,file],{encoding:'utf8'});
    assert.equal(run.status,0,run.stderr);assert.equal(JSON.parse(run.stdout).hair.length,2);
    assert.ok(!run.stdout.includes('Synthetic test'));assert.deepEqual(readFileSync(file),bytes);
    assert.deepEqual(readdirSync(dir),['source.vrm']);
    assert.equal(spawnSync(process.execPath,[cli,join(dir,'missing.vrm')]).status,1);
  } finally {rmSync(dir,{recursive:true,force:true});}
});
