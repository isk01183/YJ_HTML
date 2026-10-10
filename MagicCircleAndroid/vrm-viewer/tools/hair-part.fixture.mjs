import {hairRigFixture,setFloat} from './hair-source.fixture.mjs';

export function partFixturePair() {
  const base=hairRigFixture(),donor=hairRigFixture();
  for(const f of [base,donor]) {
    f.json.nodes.push({name:'ear-tip',translation:[0,.3,0]});f.json.nodes[1].children.push(7);
    const spring=f.json.extensions.VRMC_springBone;
    spring.colliders=[{node:1,shape:{sphere:{offset:[0,0,0],radius:.1}}}];
    spring.colliderGroups=[{name:'head',colliders:[0]}];
    spring.springs[0].colliderGroups=[0];
    spring.springs.push({name:'ear',center:1,joints:[{node:7,stiffness:2}],colliderGroups:[0]});
    const image=Buffer.from('89504e470d0a1a0a0000000d494844520000000100000001','hex');
    f.json.bufferViews.push({buffer:0,byteOffset:f.bin.length,byteLength:image.length});
    f.bin=Buffer.concat([f.bin,image]);f.json.buffers[0].byteLength=f.bin.length;
    f.json.images=[{bufferView:f.json.bufferViews.length-1,mimeType:'image/png'}];
    f.json.textures=[{source:0}];
    for(const i of [0,1,2])f.json.materials[i].pbrMetallicRoughness.baseColorTexture={index:0};
  }
  donor.json.meshes[0].primitives.splice(1,1);
  donor.json.nodes[5].translation=[0,.12,0];
  setFloat(donor,donor.position,9,.25);
  return [base,donor];
}
