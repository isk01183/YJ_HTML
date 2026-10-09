import {createHash,randomUUID} from 'node:crypto';
import {existsSync,writeFileSync,linkSync,unlinkSync} from 'node:fs';
import {isAbsolute,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readHairSource,readLocalModel} from './inspect-hair-source.mjs';
import {hairAssemblyMap,hairOwnership,signatures} from './compare-hair-source.mjs';

const MAX_BYTES=64*1024*1024, width={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16};
const sha=bytes=>createHash('sha256').update(bytes).digest('hex');
const copy=value=>structuredClone(value);
function check(ok,message){if(!ok)throw new Error(message);}
function mapped(map,key,label){check(map.has(key),`Unmapped ${label}: ${key}`);return map.get(key);}

/** Narrow, fail-closed VRoid E hair assembly. Original encoded images are never re-encoded. */
export function assembleHair(baseBytes,donorBytes) {
  const map=hairAssemblyMap(baseBytes,donorBytes);
  check(map.status==='candidate',`Not an assembly candidate: ${map.blockers.join('; ')}`);
  const base=readHairSource(baseBytes),donor=readHairSource(donorBytes),bo=hairOwnership(base),ho=hairOwnership(donor);
  const j=copy(base.json),chunks=[];let offset=0;
  for(const field of ['nodes','meshes','skins','accessors','bufferViews','materials','textures','images','samplers'])j[field]=[];
  const baseNodes=new Map(),donorNodes=new Map(),origins=[];
  base.json.nodes.forEach((node,i)=>{
    if(bo.nodes.has(i)||bo.meshNodes.has(i))return;
    baseNodes.set(i,j.nodes.length);j.nodes.push(copy(node));origins.push([base,i]);
  });
  for(const {base:b,donor:d} of map.commonNodes)donorNodes.set(d,mapped(baseNodes,b,'common node'));
  donor.json.nodes.forEach((node,i)=>{
    if(!ho.nodes.has(i)&&!ho.meshNodes.has(i))return;
    donorNodes.set(i,j.nodes.length);j.nodes.push(copy(node));origins.push([donor,i]);
  });
  for(let out=0;out<j.nodes.length;out++) {
    const [source,ni]=origins[out],node=j.nodes[out],nm=source===base?baseNodes:donorNodes;
    if(node.children)node.children=node.children.filter(n=>nm.has(n)).map(n=>nm.get(n));
    if(source===donor&&source.parents[ni]>=0&&!ho.nodes.has(source.parents[ni])) {
      const parent=j.nodes[mapped(donorNodes,source.parents[ni],'hair parent')];
      (parent.children??=[]).push(out);
    }
  }
  j.scenes=j.scenes.map((scene,si)=>({...scene,nodes:[...(scene.nodes??[]).filter(n=>baseNodes.has(n)).map(n=>baseNodes.get(n)),
    ...(donor.json.scenes[si]?.nodes??[]).filter(n=>ho.meshNodes.has(n)||ho.nodes.has(n)).map(n=>donorNodes.get(n))]}));

  function view(bytes) {
    check(offset+bytes.length<=MAX_BYTES,'Output buffer size limit');
    const i=j.bufferViews.length;
    j.bufferViews.push({buffer:0,byteOffset:offset,byteLength:bytes.length});chunks.push(bytes);offset+=bytes.length;
    const padding=(4-offset%4)%4;if(padding){chunks.push(Buffer.alloc(padding));offset+=padding;}
    return i;
  }
  function accessor(values,type,componentType=5126,bounds=false) {
    const size=componentType===5123?2:4,bytes=Buffer.alloc(values.length*size);
    for(let i=0;i<values.length;i++) {
      check(Number.isFinite(values[i]),'Non-finite output');
      if(componentType===5126)bytes.writeFloatLE(values[i],i*size);
      else if(componentType===5123)bytes.writeUInt16LE(values[i],i*size);else bytes.writeUInt32LE(values[i],i*size);
    }
    const a={bufferView:view(bytes),componentType,type,count:values.length/width[type]};
    if(bounds) {
      a.min=Array(width[type]).fill(Infinity);a.max=Array(width[type]).fill(-Infinity);
      for(let i=0;i<values.length;i++){const k=i%width[type];a.min[k]=Math.min(a.min[k],values[i]);a.max[k]=Math.max(a.max[k],values[i]);}
    }
    j.accessors.push(a);return j.accessors.length-1;
  }
  const resources=new Map([base,donor].map(s=>[s,{materials:new Map(),textures:new Map(),images:new Map(),samplers:new Map()}]));
  const encodedImages=new Map();
  function image(source,i) {
    const cache=resources.get(source).images;if(cache.has(i))return cache.get(i);
    const original=source.json.images[i];check(original,'Missing image');
    const v=source.json.bufferViews[original.bufferView],bytes=source.bin.subarray(v.byteOffset??0,(v.byteOffset??0)+v.byteLength);
    const key=original.mimeType+':'+sha(bytes);let out=encodedImages.get(key);
    if(out===undefined){out=j.images.length;j.images.push({...copy(original),bufferView:view(bytes)});encodedImages.set(key,out);}
    cache.set(i,out);return out;
  }
  function texture(source,i) {
    const cache=resources.get(source).textures;if(cache.has(i))return cache.get(i);
    const original=source.json.textures[i],t={...copy(original),source:image(source,original.source)};
    if(original.sampler!==undefined) {
      const samplers=resources.get(source).samplers;
      if(!samplers.has(original.sampler)){samplers.set(original.sampler,j.samplers.length);j.samplers.push(copy(source.json.samplers[original.sampler]));}
      t.sampler=samplers.get(original.sampler);
    }
    const out=j.textures.length;j.textures.push(t);cache.set(i,out);return out;
  }
  function material(source,i) {
    const cache=resources.get(source).materials;if(cache.has(i))return cache.get(i);
    const out=j.materials.length,value=copy(source.json.materials[i]);
    function walk(obj){for(const [k,v] of Object.entries(obj))if(v&&typeof v==='object'){
      if(k.endsWith('Texture')&&'index' in v)v.index=texture(source,v.index);else walk(v);
    }}
    walk(value);j.materials.push(value);cache.set(i,out);return out;
  }
  const donorSkinCache=new Map();
  function skin(di) {
    if(donorSkinCache.has(di))return donorSkinCache.get(di);
    const original=donor.json.skins[di];check(original,'Missing donor skin');
    const s={...copy(original),joints:original.joints.map(n=>mapped(donorNodes,n,'skin joint')),
      inverseBindMatrices:accessor(donor.accessor(original.inverseBindMatrices),'MAT4')};
    if(s.skeleton!==undefined)s.skeleton=mapped(donorNodes,s.skeleton,'skeleton');
    const out=j.skins.length;j.skins.push(s);donorSkinCache.set(di,out);return out;
  }
  function primitive(source,p,sourceSkin,outputSkin) {
    const indices=source.accessor(p.indices),vertices=[...new Set(indices)],vertexMap=new Map(vertices.map((v,i)=>[v,i]));
    const output={...copy(p),material:material(source,p.material),attributes:{}};
    const sourceNodeMap=source===base?baseNodes:donorNodes,weights=source.accessor(p.attributes.WEIGHTS_0);
    function subset(index,semantic) {
      const a=source.json.accessors[index],data=source.accessor(index),n=width[a.type],values=new Float64Array(vertices.length*n);
      for(let i=0;i<vertices.length;i++)for(let k=0;k<n;k++) {
        const v=vertices[i];let value=data[v*n+k];
        if(semantic==='JOINTS_0') {
          if(weights[v*4+k]===0)value=0;
          else {value=outputSkin.joints.indexOf(mapped(sourceNodeMap,sourceSkin.joints[value],'weighted joint'));check(value>=0,'Missing output skin influence');}
        }
        values[i*n+k]=value;
      }
      return accessor(values,a.type,a.componentType,semantic==='POSITION');
    }
    for(const [key,index] of Object.entries(p.attributes))output.attributes[key]=subset(index,key);
    output.indices=accessor(Array.from(indices,i=>vertexMap.get(i)),'SCALAR',vertices.length>65535?5125:5123);
    if(p.targets)output.targets=p.targets.map(t=>Object.fromEntries(Object.entries(t).map(([key,index])=>[key,subset(index,key)])));
    return output;
  }
  const baseRefs=new Set(map.baseHair.map(p=>`${p.mesh}:${p.primitive}`)),donorRefs=new Set(map.donorHair.map(p=>`${p.mesh}:${p.primitive}`));
  const donorByOutput=new Map([...donorNodes].map(([d,o])=>[o,d]));
  for(let oi=0;oi<j.nodes.length;oi++) {
    const node=j.nodes[oi];if(node.mesh===undefined)continue;
    const [source,ni]=origins[oi],dn=donor.json.nodes[donorByOutput.get(oi)];check(dn?.mesh!==undefined,'Missing donor mesh binding');
    node.skin=skin(dn.skin);const outputSkin=j.skins[node.skin];
    const mesh={...copy(source.json.meshes[source.json.nodes[ni].mesh]),primitives:[]};
    if(source===base) {
      const bn=base.json.nodes[ni];
      base.json.meshes[bn.mesh].primitives.forEach((p,pi)=>{if(!baseRefs.has(`${bn.mesh}:${pi}`))mesh.primitives.push(primitive(base,p,base.json.skins[bn.skin],outputSkin));});
    }
    donor.json.meshes[dn.mesh].primitives.forEach((p,pi)=>{if(donorRefs.has(`${dn.mesh}:${pi}`))mesh.primitives.push(primitive(donor,p,donor.json.skins[dn.skin],outputSkin));});
    check(mesh.primitives.length,'Empty output mesh');node.mesh=j.meshes.length;j.meshes.push(mesh);
  }
  function nodeRefs(value,nm) {
    if(Array.isArray(value))return value.map(v=>nodeRefs(v,nm));
    if(!value||typeof value!=='object')return value;
    return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,['node','center'].includes(k)&&typeof v==='number'?mapped(nm,v,k):nodeRefs(v,nm)]));
  }
  const vrm=copy(base.json.extensions.VRMC_vrm);
  const annotations=vrm.firstPerson?.meshAnnotations;
  if(annotations)vrm.firstPerson.meshAnnotations=annotations.filter(a=>baseNodes.has(a.node));
  j.extensions.VRMC_vrm=nodeRefs(vrm,baseNodes);
  if(annotations)j.extensions.VRMC_vrm.firstPerson.meshAnnotations.push(...(donor.json.extensions.VRMC_vrm.firstPerson?.meshAnnotations??[])
    .filter(a=>ho.meshNodes.has(a.node)).map(a=>nodeRefs(a,donorNodes)));
  const expr=j.extensions.VRMC_vrm.expressions??{};
  for(const e of [...Object.values(expr.preset??{}),...Object.values(expr.custom??{})])for(const bind of [...(e.materialColorBinds??[]),...(e.textureTransformBinds??[])])
    bind.material=material(base,bind.material);
  if(vrm.meta?.thumbnailImage!==undefined)j.extensions.VRMC_vrm.meta.thumbnailImage=image(base,vrm.meta.thumbnailImage);
  const bs=base.json.extensions.VRMC_springBone,ds=donor.json.extensions.VRMC_springBone;
  if(bs||ds) {
    const protectedSpring=copy(bs??{specVersion:'1.0'});
    protectedSpring.springs=(protectedSpring.springs??[]).filter((_,i)=>!bo.springs.includes(i));
    j.extensions.VRMC_springBone=nodeRefs(protectedSpring,baseNodes);
    j.extensions.VRMC_springBone.springs.push(...(ds?.springs??[]).filter((_,i)=>ho.springs.includes(i)).map(s=>nodeRefs(s,donorNodes)));
  }
  j.buffers=[{byteLength:offset}];
  const text=Buffer.from(JSON.stringify(j)),jsonLength=Math.ceil(text.length/4)*4,total=28+jsonLength+offset;
  check(jsonLength<=4*1024*1024&&total<=MAX_BYTES,'Output GLB size limit');
  const bytes=Buffer.alloc(total);bytes.writeUInt32LE(0x46546c67);bytes.writeUInt32LE(2,4);bytes.writeUInt32LE(total,8);
  bytes.writeUInt32LE(jsonLength,12);bytes.writeUInt32LE(0x4e4f534a,16);bytes.fill(32,20,20+jsonLength);text.copy(bytes,20);
  bytes.writeUInt32LE(offset,20+jsonLength);bytes.writeUInt32LE(0x004e4942,24+jsonLength);let pos=28+jsonLength;
  for(const chunk of chunks){chunk.copy(bytes,pos);pos+=chunk.length;}
  const output=readHairSource(bytes);
  check(output.report.status==='inspected','Output validation failed');
  // shortcut: this verified VRoid pipeline accepts PNG headers only; add full JPEG inspection before other exporters.
  check(output.report.dependencies.textureBudget.status==='within','Output texture budget unknown or exceeded');
  const left=signatures(base),right=signatures(donor),actual=signatures(output);
  check(!actual.issues.length&&actual.world===left.world,'Protected rig changed during assembly');
  for(const [key,value] of left.shapes)if(!left.hair.has(key))check(actual.shapes.get(key)===value,`Protected shape changed: ${key}`);
  check(actual.hair.size===right.hair.size&&actual.privateRig===right.privateRig,'Hair rig changed during assembly');
  for(const key of right.hair)check(actual.shapes.get(key)===right.shapes.get(key),`Hair shape changed: ${key}`);
  return {bytes,provenance:{schemaVersion:1,baseSha256:sha(baseBytes),donorSha256:sha(donorBytes),outputSha256:sha(bytes),protectedUnchanged:true,visualReviewRequired:true}};
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  let temp,reportTemp,reportPublished=false,outputPublished=false;
  try {
    check(process.argv.length===5,'Usage: assemble-hair.mjs <absolute-base.vrm> <absolute-donor.vrm> <absolute-new-output.vrm>');
    const [base,donor,out]=process.argv.slice(2);check(isAbsolute(out),'Expected absolute output path');
    check(!existsSync(out)&&!existsSync(out+'.json'),'Output or report already exists');
    const result=assembleHair(readLocalModel(base),readLocalModel(donor));
    check(sha(readLocalModel(base))===result.provenance.baseSha256&&sha(readLocalModel(donor))===result.provenance.donorSha256,'Input changed during assembly');
    temp=out+'.'+randomUUID()+'.tmp';reportTemp=temp+'.json';
    writeFileSync(temp,result.bytes,{flag:'wx'});writeFileSync(reportTemp,JSON.stringify(result.provenance,null,2)+'\n',{flag:'wx'});
    linkSync(reportTemp,out+'.json');reportPublished=true;linkSync(temp,out);outputPublished=true;
    process.stdout.write(JSON.stringify(result.provenance,null,2)+'\n');
  } catch(error) {
    if(reportPublished&&!outputPublished)unlinkSync(process.argv[4]+'.json');
    process.stderr.write(`Hair assembly failed: ${error.code??error.message}\n`);process.exitCode=1;
  } finally {
    for(const path of [temp,reportTemp])if(path&&existsSync(path))unlinkSync(path);
  }
}
