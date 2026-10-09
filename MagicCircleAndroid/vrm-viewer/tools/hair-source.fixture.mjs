// Entirely synthetic triangles, bones and image bytes; no personal avatar data.
export function fixture() {
  const chunks = [], accessors = [], bufferViews = [];
  function add(values, type, componentType = 5126) {
    const width = componentType === 5123 ? 2 : 4;
    const bytes = Buffer.alloc(Math.ceil(values.length * width / 4) * 4);
    values.forEach((value, i) => componentType === 5126 ? bytes.writeFloatLE(value, i * width) :
      componentType === 5123 ? bytes.writeUInt16LE(value, i * width) : bytes.writeUInt32LE(value, i * width));
    const view = bufferViews.length;
    bufferViews.push({ buffer: 0, byteOffset: chunks.reduce((n, b) => n + b.length, 0), byteLength: bytes.length });
    chunks.push(bytes);
    accessors.push({ bufferView: view, componentType, type, count: values.length / { SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4, MAT4: 16 }[type] });
    return accessors.length - 1;
  }
  const position = add([0,0,0, 1,0,0, 0,1,0, 0,1,0, 1,1,0, 0,2,0], 'VEC3');
  const uv = add([0,0, 1,0, 0,1, 0,0, 1,0, 0,1], 'VEC2');
  const normal = add(Array.from({length: 18}, (_, i) => i % 3 === 2 ? 1 : 0), 'VEC3');
  const joints = add(Array(6).fill([0,0,0,0]).flat(), 'VEC4', 5123);
  const weights = add(Array(6).fill([1,0,0,0]).flat(), 'VEC4');
  const protectedIndices = add([0,1,2], 'SCALAR', 5123);
  const hairIndices = add([3,4,5], 'SCALAR', 5123);
  const morph = add(Array(18).fill(0), 'VEC3');
  const inverseBind = add([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1], 'MAT4');
  const attributes = { POSITION: position, NORMAL: normal, TEXCOORD_0: uv, JOINTS_0: joints, WEIGHTS_0: weights };
  const primitive = (material, indices = protectedIndices) => ({ material, indices, attributes: {...attributes}, targets: [{POSITION:morph}] });
  const names = ['N00_000_00_Body_00_SKIN (Instance)', 'N00_000_00_HairBack_00_HAIR (Instance)',
    'N00_000_Hair_00_HAIR_01 (Instance)', 'Accessory_RabbitEar_01_CLOTH (Instance)',
    'Accessory_RabbitTail_01_CLOTH (Instance)', 'N00_000_00_Face_00_SKIN (Instance)'];
  const bin = Buffer.concat(chunks);
  const json = {
    asset: {version:'2.0'}, scene:0, scenes:[{nodes:[0,2,3,4]}],
    extensionsUsed:['VRMC_vrm','VRMC_springBone'],
    extensions:{VRMC_vrm:{specVersion:'1.0', meta:{name:'Synthetic test',authors:['Test']},
      humanoid:{humanBones:{hips:{node:0}, head:{node:1}}},
      expressions:{preset:{blink:{morphTargetBinds:[{node:4,index:0,weight:1}]}}}}},
    nodes:[{name:'hips',children:[1]}, {name:'head',translation:[0,1,0]},
      {name:'Body',mesh:0,skin:0}, {name:'Hair',mesh:1,skin:0}, {name:'Face',mesh:2,skin:0}],
    meshes:[{name:'Body',primitives:[primitive(0),primitive(1,hairIndices),primitive(3),primitive(4)],extras:{targetNames:['blink']}},
      {name:'Hair',primitives:[primitive(2,hairIndices)],extras:{targetNames:['blink']}},
      {name:'Face',primitives:[primitive(5)],extras:{targetNames:['blink']}}],
    materials:names.map(name=>({name,pbrMetallicRoughness:{baseColorFactor:[1,1,1,1]}})),
    skins:[{joints:[1],inverseBindMatrices:inverseBind}], buffers:[{byteLength:bin.length}], bufferViews, accessors
  };
  return {json, bin, position, uv, normal, weights, morph, inverseBind};
}

export function glb({json, bin}) {
  const text = Buffer.from(JSON.stringify(json)), padded = Buffer.alloc(Math.ceil(text.length / 4) * 4, 32);
  text.copy(padded);
  const output = Buffer.alloc(28 + padded.length + Math.ceil(bin.length / 4) * 4);
  output.writeUInt32LE(0x46546c67); output.writeUInt32LE(2,4); output.writeUInt32LE(output.length,8);
  output.writeUInt32LE(padded.length,12); output.writeUInt32LE(0x4e4f534a,16); padded.copy(output,20);
  output.writeUInt32LE(output.length - 28 - padded.length,20+padded.length);
  output.writeUInt32LE(0x004e4942,24+padded.length); bin.copy(output,28+padded.length);
  return output;
}

export function setFloat(f, accessor, component, value) {
  const a=f.json.accessors[accessor], view=f.json.bufferViews[a.bufferView];
  f.bin.writeFloatLE(value,(view.byteOffset ?? 0)+(a.byteOffset ?? 0)+component*4);
}

export function hairRigFixture() {
  const f=fixture(),j=f.json;
  j.nodes[1].children=[5];j.nodes.push({name:'hair-root',children:[6],translation:[0,.1,0]},{name:'hair-tip',translation:[0,-.2,0]});
  const offset=f.bin.length,matrices=Buffer.alloc(3*64);
  for(let n=0;n<3;n++)for(const k of [0,5,10,15])matrices.writeFloatLE(1,n*64+k*4);
  f.bin=Buffer.concat([f.bin,matrices]);j.buffers[0].byteLength=f.bin.length;
  j.bufferViews.push({buffer:0,byteOffset:offset,byteLength:matrices.length});
  j.accessors[f.inverseBind]={bufferView:j.bufferViews.length-1,componentType:5126,type:'MAT4',count:3};
  j.skins[0].joints=[1,5,6];
  const a=j.accessors[j.meshes[0].primitives[0].attributes.JOINTS_0],v=j.bufferViews[a.bufferView];
  for(let vertex=3;vertex<6;vertex++)f.bin.writeUInt16LE(1,v.byteOffset+vertex*8);
  j.extensions.VRMC_springBone={specVersion:'1.0',springs:[{joints:[{node:5,stiffness:1},{node:6,stiffness:1}]}]};
  return f;
}
