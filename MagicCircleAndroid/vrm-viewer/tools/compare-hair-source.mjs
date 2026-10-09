import {createHash} from 'node:crypto';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {readHairSource,readLocalModel} from './inspect-hair-source.mjs';

function stable(value) {
  if(Array.isArray(value))return '['+value.map(stable).join(',')+']';
  if(value && typeof value==='object')return '{'+Object.keys(value).sort().map(k=>JSON.stringify(k)+':'+stable(value[k])).join(',')+'}';
  return JSON.stringify(value);
}
const hash=value=>createHash('sha256').update(stable(value)).digest('hex');

export function hairOwnership(source) {
  const {json:j,accessor,parents}=source, issues=[], protectedBones=new Set(),hairBones=new Set(),meshNodes=new Set();
  const refs=new Set(source.report.hair.map(p=>`${p.mesh}:${p.primitive}`));
  for(let ni=0;ni<j.nodes.length;ni++) {
    const node=j.nodes[ni];if(node.mesh===undefined)continue;
    const mesh=j.meshes[node.mesh],skin=j.skins[node.skin];
    if(!skin){issues.push('Unskinned mesh requires review');continue;}
    if(mesh.primitives.every((_,pi)=>refs.has(`${node.mesh}:${pi}`))) {
      meshNodes.add(ni);if(node.children?.length)issues.push('Hair mesh has child nodes');
    }
    mesh.primitives.forEach((p,pi)=>{
      const used=refs.has(`${node.mesh}:${pi}`)?hairBones:protectedBones;
      const joints=accessor(p.attributes.JOINTS_0),weights=accessor(p.attributes.WEIGHTS_0);
      for(const vertex of new Set(accessor(p.indices)))for(let k=0;k<4;k++)
        if(weights[vertex*4+k]>0)used.add(skin.joints[joints[vertex*4+k]]);
    });
  }
  for(const b of Object.values(j.extensions.VRMC_vrm.humanoid.humanBones))protectedBones.add(b.node);
  for(const node of [...protectedBones])for(let p=parents[node];p>=0;p=parents[p])protectedBones.add(p);
  const nodes=new Set();
  function include(n) {
    if(nodes.has(n))return;
    if(protectedBones.has(n)||j.nodes[n].mesh!==undefined){issues.push('Hair branch includes protected node');return;}
    nodes.add(n);for(const child of j.nodes[n].children??[])include(child);
  }
  for(const n of hairBones)if(!protectedBones.has(n)) {
    let root=n;while(parents[root]>=0&&!protectedBones.has(parents[root]))root=parents[root];include(root);
  }
  const springs=[];
  (j.extensions.VRMC_springBone?.springs??[]).forEach((spring,i)=>{
    const owned=spring.joints.map(x=>nodes.has(x.node));
    if(owned.some(Boolean)) {
      if(!owned.every(Boolean))issues.push('Mixed hair and protected spring');else springs.push(i);
    }
    if(spring.center!==undefined&&nodes.has(spring.center)&&!owned.every(Boolean))issues.push('Protected spring uses hair center');
  });
  if((j.extensions.VRMC_springBone?.colliders??[]).some(c=>nodes.has(c.node)))issues.push('Hair-owned collider requires review');
  const expressions=j.extensions.VRMC_vrm.expressions??{};
  for(const e of [...Object.values(expressions.preset??{}),...Object.values(expressions.custom??{})]) {
    if((e.morphTargetBinds??[]).some(b=>nodes.has(b.node)||meshNodes.has(b.node)))issues.push('Expression binds hair node');
    if([...(e.materialColorBinds??[]),...(e.textureTransformBinds??[])].some(b=>source.report.hair.some(h=>h.material===b.material)))issues.push('Expression binds hair material');
  }
  return {nodes,meshNodes,springs,issues};
}

export function signatures(source) {
  const {json:j,bin,accessor,paths}=source, shapes=new Map(),hair=new Set(),issues=[],imageCache=new Map();
  // Decoded accessors may be reused by many triangles and morphs; bound that work separately.
  let comparisonWork=0;
  for(const node of j.nodes)if(node.mesh!==undefined)for(const p of j.meshes[node.mesh].primitives) {
    for(const index of [...Object.values(p.attributes),...(p.targets??[]).flatMap(Object.values)]) {
      comparisonWork+=j.accessors[p.indices].count*accessor(index).length/j.accessors[index].count;
      if(comparisonWork>16*1024*1024)return {shapes,hair,world:null,issues:['Comparison work limit exceeded']};
    }
  }
  const ownership=hairOwnership(source);issues.push(...ownership.issues);
  const removed=new Set([...ownership.nodes,...ownership.meshNodes]);
  const nodePath=n=>{if(!Number.isInteger(n)||!paths[n])throw new Error('Invalid canonical node reference');return paths[n];};
  function refs(value) {
    if(Array.isArray(value))return value.map(refs);
    if(!value||typeof value!=='object')return value;
    return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,
      ['node','center'].includes(k)&&typeof v==='number'?nodePath(v):
        k==='material'&&typeof v==='number'?j.materials[v]?.name:refs(v)]));
  }
  function texture(i) {
    const t=j.textures[i];if(!t)throw new Error('Invalid texture reference');
    if(!imageCache.has(t.source)) {
      const image=j.images[t.source],v=j.bufferViews[image.bufferView];
      imageCache.set(t.source,{mimeType:image.mimeType,sha256:createHash('sha256').update(bin.subarray(v.byteOffset??0,(v.byteOffset??0)+v.byteLength)).digest('hex')});
    }
    const {source:ignoredSource,name:ignoredDisplayName,sampler,...rest}=t;
    return {...rest,image:imageCache.get(t.source),sampler:sampler===undefined?null:j.samplers[sampler]};
  }
  function material(value) {
    if(Array.isArray(value))return value.map(material);
    if(!value||typeof value!=='object')return value;
    return Object.fromEntries(Object.entries(value).map(([k,v])=>{
      if(k.endsWith('Texture')&&v&&typeof v==='object'&&'index' in v) {
        const {index,...rest}=v;return [k,{...rest,texture:texture(index)}];
      }
      return [k,material(v)];
    }));
  }
  function values(index,vertices,skin,weights=null) {
    const a=j.accessors[index], data=accessor(index), width=data.length/a.count, result=createHash('sha256');
    for(const vertex of vertices) {
      const tuple=Array.from(data.subarray(vertex*width,(vertex+1)*width));
      result.update(JSON.stringify(weights?tuple.map((n,k)=>weights[vertex*4+k]===0?null:nodePath(skin.joints[n])):tuple));
    }
    return result.digest('hex');
  }
  const hairRefs=new Set(source.report.hair.map(p=>`${p.mesh}:${p.primitive}`));
  const rig=Object.create(null),hairRig=Object.create(null);
  for(let ni=0;ni<j.nodes.length;ni++) {
    const node=j.nodes[ni],{mesh:mi,skin:si,children,name,...rest}=node;
    const skin=si===undefined?null:j.skins[si];
    const binding=skin?Object.fromEntries(skin.joints.flatMap((joint,i)=>ownership.nodes.has(joint)?[]:[[nodePath(joint),Array.from(accessor(skin.inverseBindMatrices).subarray(i*16,(i+1)*16))]])):null;
    const settings={...rest,children:(children??[]).filter(n=>!removed.has(n)).map(nodePath),binding,skeleton:skin?.skeleton===undefined?null:nodePath(skin.skeleton)};
    if(!removed.has(ni))rig[nodePath(ni)]=settings;
    else hairRig[nodePath(ni)]={...rest,children:(children??[]).map(nodePath),parent:source.parents[ni]<0?null:nodePath(source.parents[ni]),
      binding:skin?skin.joints.map((n,i)=>[nodePath(n),Array.from(accessor(skin.inverseBindMatrices).subarray(i*16,(i+1)*16))]):null};
    if(mi===undefined)continue;
    j.meshes[mi].primitives.forEach((p,pi)=>{
      const key=`${nodePath(ni)} / ${j.materials[p.material].name}`;
      if(shapes.has(key))issues.push('Ambiguous duplicate primitive identity');
      const vertices=accessor(p.indices),attributes={};
      for(const [attribute,index] of Object.entries(p.attributes))attributes[attribute]=values(index,vertices,skin,attribute==='JOINTS_0'?accessor(p.attributes.WEIGHTS_0):null);
      const morph=(p.targets??[]).map(target=>Object.fromEntries(Object.entries(target).map(([k,v])=>[k,values(v,vertices,skin)])));
      const {primitives,...meshSettings}=j.meshes[mi];
      const {indices,attributes:ignoredAttributes,targets,material:ignoredMaterial,...primitiveSettings}=p;
      shapes.set(key,hash({attributes,morph,material:material(j.materials[p.material]),meshSettings,primitiveSettings}));
      if(hairRefs.has(`${mi}:${pi}`))hair.add(key);
    });
  }
  const {meta,...vrm}=structuredClone(j.extensions.VRMC_vrm);
  if(vrm.firstPerson?.meshAnnotations)vrm.firstPerson.meshAnnotations=vrm.firstPerson.meshAnnotations.filter(a=>!removed.has(a.node));
  const {nodes,meshes,skins,accessors,bufferViews,buffers,materials,textures,images,samplers,asset,extensions,scenes,...root}=j;
  const spring=extensions.VRMC_springBone;
  const protectedExtensions={...extensions,VRMC_vrm:vrm};
  if(spring)protectedExtensions.VRMC_springBone={...spring,springs:(spring.springs??[]).filter((_,i)=>!ownership.springs.includes(i))};
  const world=hash({root,rig,scenes:scenes.map(s=>({...s,nodes:(s.nodes??[]).filter(n=>!removed.has(n)).map(nodePath)})),extensions:refs(protectedExtensions)});
  const privateRig=hash({hairRig,springs:refs((spring?.springs??[]).filter((_,i)=>ownership.springs.includes(i)))});
  return {shapes,hair,world,privateRig,ownership,issues};
}

/** A candidate is NOT approval to transplant: renderer/physics/appearance review is still required. */
export function compareHairSources(base,variant) {
  const a=readHairSource(base),b=readHairSource(variant);
  const result={schemaVersion:1,baseSha256:a.report.sha256,variantSha256:b.report.sha256,status:'unsupported',changes:[],blockers:[],visualReviewRequired:true};
  if(a.report.status!=='inspected'||b.report.status!=='inspected') {
    result.blockers=[...new Set([...a.report.reasons,...b.report.reasons])];return result;
  }
  const left=signatures(a),right=signatures(b);
  result.blockers=[...new Set([...left.issues,...right.issues])];
  if(result.blockers.length)return result;
  if(left.world!==right.world)result.blockers.push('Rig, spring, expressions or scene configuration changed');
  if(left.privateRig!==right.privateRig)result.changes.push('hair:rig or physics');
  for(const key of new Set([...left.shapes.keys(),...right.shapes.keys()])) {
    if(left.shapes.get(key)===right.shapes.get(key))continue;
    if((!left.shapes.has(key)||left.hair.has(key))&&(!right.shapes.has(key)||right.hair.has(key)))result.changes.push(`hair:${key}`);
    else result.blockers.push(`Protected appearance changed: ${key}`);
  }
  result.status=result.blockers.length?'rejected':result.changes.length?'candidate':'identical';
  return result;
}

export function hairAssemblyMap(base,donor) {
  const result=compareHairSources(base,donor);
  const out={status:result.status,blockers:result.blockers,baseHair:[],donorHair:[],commonNodes:[],baseHairNodes:[],donorHairNodes:[],baseHairSprings:[],donorHairSprings:[]};
  if(result.status!=='candidate')return out;
  const a=readHairSource(base),b=readHairSource(donor),ao=hairOwnership(a),bo=hairOwnership(b);
  const commonNodes=b.paths.flatMap((path,donor)=>bo.nodes.has(donor)||bo.meshNodes.has(donor)?[]:[{base:a.paths.indexOf(path),donor}]);
  if(commonNodes.some(n=>n.base<0))return {...out,status:'unsupported',blockers:['Missing common node']};
  return {...out,baseHair:a.report.hair,donorHair:b.report.hair,commonNodes,
    baseHairNodes:[...ao.nodes].sort((a,b)=>a-b),donorHairNodes:[...bo.nodes].sort((a,b)=>a-b),baseHairSprings:ao.springs,donorHairSprings:bo.springs};
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {
    if(process.argv.length!==4)throw new Error('Usage: compare-hair-source.mjs <absolute-base-vrm> <absolute-variant-vrm>');
    const report=compareHairSources(readLocalModel(process.argv[2]),readLocalModel(process.argv[3]));
    process.stdout.write(JSON.stringify(report,null,2)+'\n');process.exitCode=['identical','candidate'].includes(report.status)?0:2;
  } catch(error) {process.stderr.write(`VRM comparison failed: ${error.code??error.message}\n`);process.exitCode=1;}
}
