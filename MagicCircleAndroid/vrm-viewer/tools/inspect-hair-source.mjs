import {createHash} from 'node:crypto';
import {openSync,fstatSync,readSync,closeSync} from 'node:fs';
import {isAbsolute,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const MAX_BYTES=64*1024*1024;
const hairNames=new Set(['N00_000_00_HairBack_00_HAIR (Instance)',...['01','02','03'].map(n=>`N00_000_Hair_00_HAIR_${n} (Instance)`)]);
const protectedNames=new Set([
  'N00_000_00_FaceMouth_00_FACE','N00_000_00_EyeIris_00_EYE','N00_000_00_EyeHighlight_00_EYE',
  'N00_000_00_Face_00_SKIN','N00_000_00_EyeWhite_00_EYE','N00_000_00_FaceBrow_00_FACE',
  'N00_000_00_FaceEyelash_00_FACE','N00_000_00_FaceEyeline_00_FACE','N00_000_00_Body_00_SKIN',
  'N00_007_01_Tops_01_CLOTH_01','N00_002_03_Tops_01_CLOTH_01','N00_002_03_Tops_01_CLOTH_02',
  'N00_007_01_Tops_01_CLOTH_02','N00_002_03_Tops_01_CLOTH_03','N00_008_01_Shoes_01_CLOTH_01',
  'N00_008_01_Shoes_01_CLOTH_02','N00_010_01_Onepiece_00_CLOTH_01','N00_010_01_Onepiece_00_CLOTH_02',
  'N00_010_01_Onepiece_00_CLOTH_03','Accessory_RabbitEar_01_CLOTH','Accessory_RabbitTail_01_CLOTH'
].map(n=>`${n} (Instance)`));
const extensions=new Set(['VRMC_vrm','VRMC_springBone','VRMC_materials_mtoon','KHR_texture_transform','KHR_materials_unlit']);
const components={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16};
function requireValue(ok,message) {if(!ok)throw new Error(message);}
function integer(n,max=Number.MAX_SAFE_INTEGER) {return Number.isSafeInteger(n)&&n>=0&&n<=max;}
function at(array,index,label) {requireValue(integer(index,array.length-1),`Invalid ${label} reference`);return array[index];}
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');

/** Intentionally a read-only, narrow E-export inspector, not a general glTF loader. */
export function readHairSource(input) {
  requireValue(input instanceof Uint8Array && input.length>=28 && input.length<=MAX_BYTES && input.length%4===0,'Invalid GLB size');
  const bytes=Buffer.from(input.buffer,input.byteOffset,input.byteLength);
  requireValue(bytes.readUInt32LE(0)===0x46546c67 && bytes.readUInt32LE(4)===2 && bytes.readUInt32LE(8)===bytes.length,'Invalid GLB header');
  const jsonLength=bytes.readUInt32LE(12), binHeader=20+jsonLength;
  requireValue(jsonLength>0 && jsonLength<=4*1024*1024 && jsonLength%4===0 && binHeader+8<=bytes.length && bytes.readUInt32LE(16)===0x4e4f534a,'Invalid JSON chunk');
  const json=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(bytes.subarray(20,binHeader)));
  requireValue(json && typeof json==='object' && !Array.isArray(json),'Missing glTF object');
  requireValue(bytes.readUInt32LE(binHeader+4)===0x004e4942 && bytes.readUInt32LE(binHeader)===bytes.length-binHeader-8,'Invalid BIN chunk');
  const bin=bytes.subarray(binHeader+8), reasons=[];
  const unsupported=message=>{if(!reasons.includes(message))reasons.push(message);};
  function walk(value,depth=0) {
    requireValue(depth<=64,'JSON nesting limit');
    if(typeof value==='number')requireValue(Number.isFinite(value),'Non-finite JSON number');
    if(!value || typeof value!=='object')return;
    if(value.extensions)for(const name of Object.keys(value.extensions))if(!extensions.has(name))unsupported(`Unsupported extension: ${name}`);
    for(const child of Object.values(value))walk(child,depth+1);
  }
  walk(json);
  requireValue(json.asset?.version==='2.0','Unsupported glTF version');
  if(json.extensions?.VRMC_vrm?.specVersion!=='1.0'||json.extensions?.VRM)unsupported('Expected only VRM 1.0');
  for(const name of [...(json.extensionsUsed??[]),...(json.extensionsRequired??[])])if(!extensions.has(name))unsupported(`Unsupported extension: ${name}`);
  const arrays={nodes:4096,meshes:4096,skins:256,accessors:8192,bufferViews:8192,materials:256,images:64,textures:256,samplers:256,scenes:32};
  for(const [key,max] of Object.entries(arrays)) {
    json[key]??=[];requireValue(Array.isArray(json[key])&&json[key].length<=max,`Invalid ${key} count`);
  }
  requireValue(json.buffers?.length===1 && !('uri' in json.buffers[0]),'External or missing buffer');
  const size=json.buffers[0].byteLength;
  requireValue(integer(size,bin.length)&&size>0&&bin.length-size<=3,'Invalid buffer length');
  for(const view of json.bufferViews) {
    requireValue(view.buffer===0 && integer(view.byteOffset??0) && integer(view.byteLength) && view.byteLength>0 && (view.byteOffset??0)+view.byteLength<=size,'Buffer view outside BIN');
    if(view.byteStride!==undefined)unsupported('Interleaved accessors require review');
  }
  for(const image of json.images) {
    requireValue(!('uri' in image),'External images are forbidden');at(json.bufferViews,image.bufferView,'image bufferView');
    if(!['image/png','image/jpeg'].includes(image.mimeType))unsupported('Unsupported image encoding');
  }
  for(const texture of json.textures) {at(json.images,texture.source,'texture image');if(texture.sampler!==undefined)at(json.samplers,texture.sampler,'sampler');}
  function textureRefs(value) {
    if(!value||typeof value!=='object')return;
    for(const [key,v] of Object.entries(value)) {
      if(key.endsWith('Texture')&&v && typeof v==='object' && 'index' in v)at(json.textures,v.index,'material texture');
      textureRefs(v);
    }
  }
  const materialNames=new Set();
  for(const material of json.materials) {
    if(!hairNames.has(material.name)&&!protectedNames.has(material.name))unsupported('Unknown material identity');
    if(materialNames.has(material.name))unsupported('Duplicate material identity');
    materialNames.add(material.name);textureRefs(material);
  }
  const parents=new Array(json.nodes.length).fill(-1), paths=new Array(json.nodes.length), visiting=new Set();
  json.nodes.forEach((node,i)=>{
    requireValue(Array.isArray(node.children??[]),'Invalid children');
    for(const child of node.children??[]) {at(json.nodes,child,'child');requireValue(parents[child]===-1,'Multiple node parents');parents[child]=i;}
    if(node.mesh!==undefined)at(json.meshes,node.mesh,'mesh');
    if(node.skin!==undefined)at(json.skins,node.skin,'skin');
    for(const [key,n] of [['translation',3],['rotation',4],['scale',3],['matrix',16]])if(node[key]!==undefined)requireValue(Array.isArray(node[key])&&node[key].length===n&&node[key].every(Number.isFinite),'Invalid node transform');
  });
  function nodePath(i,depth=0) {
    if(paths[i]!==undefined)return paths[i];
    requireValue(!visiting.has(i)&&depth<=128,'Cyclic or deeply nested nodes');visiting.add(i);
    const name=json.nodes[i].name;
    if(typeof name!=='string'||!name||name.includes('/'))unsupported('Ambiguous node identity');
    paths[i]=(parents[i]<0?'':nodePath(parents[i],depth+1)+'/')+(name??'');visiting.delete(i);return paths[i];
  }
  json.nodes.forEach((_,i)=>nodePath(i));
  if(new Set(paths).size!==paths.length)unsupported('Duplicate node path');
  for(const scene of json.scenes)for(const node of scene.nodes??[]) {at(json.nodes,node,'scene node');requireValue(parents[node]===-1,'Scene node is not root');}
  if(json.scene!==undefined)at(json.scenes,json.scene,'scene');
  const humanoid=json.extensions?.VRMC_vrm?.humanoid?.humanBones??{};
  for(const bone of Object.values(humanoid))at(json.nodes,bone.node,'humanoid bone');
  requireValue(humanoid.head&&humanoid.hips,'Missing head or hips');
  let decodedBytes=0;
  for(const a of json.accessors) {
    if(a.sparse)unsupported('Sparse accessors require review');
    if(!components[a.type]||![5123,5125,5126].includes(a.componentType)||a.normalized)unsupported('Unsupported accessor encoding');
    requireValue(integer(a.count)&&a.count>0,'Invalid accessor count');
    if(a.bufferView===undefined) {unsupported('Unbacked accessor');continue;}
    const view=at(json.bufferViews,a.bufferView,'accessor bufferView'), width=a.componentType===5123?2:4, n=components[a.type]??1;
    const offset=a.byteOffset??0,stride=view.byteStride??width*n;
    requireValue(integer(offset)&&integer(stride)&&stride>=width*n&&offset%width===0&&((view.byteOffset??0)+offset)%width===0,'Invalid accessor alignment');
    requireValue(offset+(a.count-1)*stride+width*n<=view.byteLength,'Accessor outside bufferView');
    decodedBytes+=a.count*n*8;requireValue(decodedBytes<=128*1024*1024,'Decoded geometry limit');
  }
  const cache=new Map();
  function accessor(i) {
    if(cache.has(i))return cache.get(i);
    const a=at(json.accessors,i,'accessor'), view=at(json.bufferViews,a.bufferView,'accessor bufferView');
    const n=components[a.type],width=a.componentType===5123?2:4,offset=(view.byteOffset??0)+(a.byteOffset??0);
    requireValue(n && [5123,5125,5126].includes(a.componentType)&&!a.sparse&&!view.byteStride&&!a.normalized,'Unsupported accessor');
    const data=new Float64Array(a.count*n);
    for(let k=0;k<data.length;k++) {data[k]=a.componentType===5126?bin.readFloatLE(offset+k*width):a.componentType===5123?bin.readUInt16LE(offset+k*width):bin.readUInt32LE(offset+k*width);requireValue(Number.isFinite(data[k]),'Non-finite geometry');}
    cache.set(i,data);return data;
  }
  const hair=[], protectedParts=[];
  json.meshes.forEach((mesh,mi)=>{
    requireValue(Array.isArray(mesh.primitives)&&mesh.primitives.length>0&&mesh.primitives.length<=256,'Invalid primitives');
    mesh.primitives.forEach((p,pi)=>{
      const material=at(json.materials,p.material,'material');
      const ref={mesh:mi,primitive:pi,material:p.material,materialName:material.name};
      if(hairNames.has(material.name))hair.push(ref);else protectedParts.push(ref);
      if((p.mode??4)!==4)unsupported('Only triangles are supported');
      const position=at(json.accessors,p.attributes?.POSITION,'position');
      requireValue(position.type==='VEC3','Invalid position type');
      for(const [key,index] of Object.entries(p.attributes)) {
        const a=at(json.accessors,index,'attribute');requireValue(a.count===position.count,'Mismatched attribute counts');
        if(!['POSITION','NORMAL','TEXCOORD_0','JOINTS_0','WEIGHTS_0'].includes(key))unsupported(`Unsupported attribute: ${key}`);
      }
      for(const target of p.targets??[])for(const index of Object.values(target))requireValue(at(json.accessors,index,'morph').count===position.count,'Mismatched morph count');
      const indices=at(json.accessors,p.indices,'indices');requireValue(indices.type==='SCALAR'&&[5123,5125].includes(indices.componentType)&&indices.count%3===0,'Invalid triangle indices');
    });
  });
  if(!hair.length)unsupported('No known hair primitives');
  for(const skin of json.skins) {
    requireValue(Array.isArray(skin.joints)&&skin.joints.length>0&&new Set(skin.joints).size===skin.joints.length,'Invalid skin joints');
    for(const joint of skin.joints)at(json.nodes,joint,'skin joint');
    if(skin.skeleton!==undefined)at(json.nodes,skin.skeleton,'skeleton');
    const matrix=at(json.accessors,skin.inverseBindMatrices,'inverse bind');
    requireValue(matrix.type==='MAT4'&&matrix.count===skin.joints.length&&matrix.componentType===5126,'Invalid inverse bind matrices');
  }
  const spring=json.extensions?.VRMC_springBone??{};
  for(const collider of spring.colliders??[])at(json.nodes,collider.node,'collider node');
  for(const group of spring.colliderGroups??[])for(const c of group.colliders??[])at(spring.colliders??[],c,'collider');
  for(const chain of spring.springs??[]) {
    if(chain.center!==undefined)at(json.nodes,chain.center,'spring center');
    for(const joint of chain.joints??[])at(json.nodes,joint.node,'spring joint');
    for(const group of chain.colliderGroups??[])at(spring.colliderGroups??[],group,'collider group');
  }
  const expressions=json.extensions?.VRMC_vrm?.expressions??{};
  for(const expression of [...Object.values(expressions.preset??{}),...Object.values(expressions.custom??{})]) {
    for(const bind of expression.morphTargetBinds??[]) {
      const node=at(json.nodes,bind.node,'expression node'),mesh=at(json.meshes,node.mesh,'expression mesh');
      requireValue(integer(bind.index)&&Number.isFinite(bind.weight)&&mesh.primitives.every(p=>bind.index<(p.targets??[]).length),'Invalid expression bind');
    }
    for(const bind of [...(expression.materialColorBinds??[]),...(expression.textureTransformBinds??[])])at(json.materials,bind.material,'expression material');
  }
  if(!reasons.length) {
    json.accessors.forEach((_,i)=>accessor(i));
    for(const mesh of json.meshes)for(const p of mesh.primitives)for(const index of accessor(p.indices))requireValue(index<json.accessors[p.attributes.POSITION].count,'Vertex index outside geometry');
    for(const node of json.nodes)if(node.mesh!==undefined&&node.skin!==undefined) {
      const skin=json.skins[node.skin];
      for(const p of json.meshes[node.mesh].primitives) {
        const joints=accessor(p.attributes.JOINTS_0),weights=accessor(p.attributes.WEIGHTS_0);
        requireValue(joints.length===weights.length&&joints.length===json.accessors[p.attributes.POSITION].count*4,'Invalid skin attributes');
        for(let i=0;i<joints.length;i++)requireValue(integer(joints[i],skin.joints.length-1)&&weights[i]>=0&&weights[i]<=1,'Invalid skin influence');
      }
    }
  }
  const report={schemaVersion:1,sha256:digest(bytes),status:reasons.length?'unsupported':'inspected',reasons,hair,protected:protectedParts,
    dependencies:{nodes:json.nodes.length,humanoidBones:Object.keys(humanoid).length,skins:json.skins.map(s=>({joints:s.joints.map(n=>paths[n])})),springs:(spring.springs??[]).length,colliders:(spring.colliders??[]).length}};
  return {report,json,bin,accessor,paths,parents};
}

export function inspectHairSource(bytes) {return readHairSource(bytes).report;}

export function readLocalModel(path) {
  requireValue(typeof path==='string'&&isAbsolute(path),'Expected an absolute VRM path');
  const fd=openSync(path,'r');
  try {
    const stat=fstatSync(fd);requireValue(stat.isFile()&&stat.size>=28&&stat.size<=MAX_BYTES,'Invalid model file size');
    const bytes=Buffer.alloc(stat.size+1);let count=0,n;
    while(count<bytes.length&&(n=readSync(fd,bytes,count,bytes.length-count,null))>0)count+=n;
    requireValue(count===stat.size,'Model file changed during read');return bytes.subarray(0,count);
  } finally {closeSync(fd);}
}

if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    requireValue(process.argv.length===3,'Usage: inspect-hair-source.mjs <absolute-vrm-path>');
    const report=inspectHairSource(readLocalModel(process.argv[2]));
    process.stdout.write(JSON.stringify(report,null,2)+'\n');process.exitCode=report.status==='inspected'?0:2;
  } catch(error) {process.stderr.write(`VRM inspection failed: ${error.code??error.message}\n`);process.exitCode=1;}
}
