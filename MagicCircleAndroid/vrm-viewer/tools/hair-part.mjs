import {createHash} from 'node:crypto';
import {readHairSource} from './inspect-hair-source.mjs';
import {compareHairSources,hairOwnership,signatures,stable} from './compare-hair-source.mjs';

const MAX=64*1024*1024, WIDTH={SCALAR:1,VEC2:2,VEC3:3,VEC4:4,MAT4:16};
const sha=b=>createHash('sha256').update(b).digest('hex');
const clone=x=>structuredClone(x);
const check=(ok,message)=>{if(!ok)throw new Error(message);};
const digest=x=>sha(stable(x));
const hex=x=>typeof x==='string'&&/^[0-9a-f]{64}$/.test(x);
const hairNames=new Set(['N00_000_00_HairBack_00_HAIR (Instance)','N00_000_Hair_00_HAIR (Instance)',...['01','02','03'].map(n=>`N00_000_Hair_00_HAIR_${n} (Instance)`)]);
const tables=['accessors','bufferViews','materials','textures','images','samplers'];
const at=(a,i)=>{check(Number.isSafeInteger(i)&&i>=0&&i<a.length,'Part reference out of range');return a[i];};

function fingerprints(source) {
  const s=signatures(source);check(!s.issues.length,s.issues.join('; '));
  const shapes=[...s.shapes].sort(([a],[b])=>a<b?-1:a>b?1:0);
  return {protectedDigest:digest({world:s.world,shapes:shapes.filter(([k])=>!s.hair.has(k))}),
    hairDigest:digest({rig:s.privateRig,shapes:shapes.filter(([k])=>s.hair.has(k))})};
}

/** Compact resource copier: retain encoded images and original float values. */
function writer(out) {
  for(const key of tables)out[key]=[];
  const chunks=[],resources=new Map(),imageHashes=new Map();let size=0;
  function view(bytes) {
    check(bytes.length>0&&size+bytes.length<=MAX,'Part BIN size limit');
    const result=out.bufferViews.length;out.bufferViews.push({buffer:0,byteOffset:size,byteLength:bytes.length});
    chunks.push(Buffer.from(bytes));size+=bytes.length;
    const pad=(4-size%4)%4;if(pad){chunks.push(Buffer.alloc(pad));size+=pad;}
    return result;
  }
  function accessor(values,type,componentType=5126) {
    const width=componentType===5123?2:4,bytes=Buffer.alloc(values.length*width);
    check(WIDTH[type]&&values.length%WIDTH[type]===0,'Invalid output accessor');
    for(let k=0;k<values.length;k++) {
      check(Number.isFinite(values[k]),'Nonfinite output');
      if(componentType===5126)bytes.writeFloatLE(values[k],k*width);
      else if(componentType===5123)bytes.writeUInt16LE(values[k],k*width);else bytes.writeUInt32LE(values[k],k*width);
    }
    const result=out.accessors.length;out.accessors.push({bufferView:view(bytes),type,componentType,count:values.length/WIDTH[type]});return result;
  }
  function resource(source,kind,index) {
    if(!resources.has(source))resources.set(source,new Map());
    const cache=resources.get(source),key=`${kind}:${index}`;if(cache.has(key))return cache.get(key);
    const value=clone(at(source.json[kind],index));
    if(kind==='images') {
      const v=source.json.bufferViews[value.bufferView],bytes=source.bin.subarray(v.byteOffset??0,(v.byteOffset??0)+v.byteLength),hash=value.mimeType+sha(bytes);
      if(imageHashes.has(hash)){cache.set(key,imageHashes.get(hash));return cache.get(key);}
      value.bufferView=view(bytes);imageHashes.set(hash,out.images.length);
    }
    if(kind==='textures') {
      value.source=resource(source,'images',value.source);
      if(value.sampler!==undefined)value.sampler=resource(source,'samplers',value.sampler);
    }
    if(kind==='materials') {
      const walk=obj=>{for(const [k,v] of Object.entries(obj))if(v&&typeof v==='object') {
        if(k.endsWith('Texture')&&'index' in v)v.index=resource(source,'textures',v.index);else walk(v);
      }};walk(value);
    }
    const result=out[kind].length;out[kind].push(value);cache.set(key,result);return result;
  }
  function primitive(source,p,jointMap) {
    const indices=source.accessor(p.indices),vertices=[...new Set(indices)],map=new Map(vertices.map((v,i)=>[v,i])),weights=source.accessor(p.attributes.WEIGHTS_0);
    const subset=(index,key)=>{
      const a=source.json.accessors[index],data=source.accessor(index),n=WIDTH[a.type],values=new Float64Array(vertices.length*n);
      for(let i=0;i<vertices.length;i++)for(let k=0;k<n;k++) {
        let value=data[vertices[i]*n+k];if(key==='JOINTS_0')value=weights[vertices[i]*4+k]===0?0:jointMap(value);values[i*n+k]=value;
      }
      return accessor(values,a.type,a.componentType);
    };
    return {...clone(p),material:resource(source,'materials',p.material),
      attributes:Object.fromEntries(Object.entries(p.attributes).map(([key,index])=>[key,subset(index,key)])),
      indices:accessor(Array.from(indices,n=>map.get(n)),'SCALAR',vertices.length>65535?5125:5123),
      ...(p.targets?{targets:p.targets.map(t=>Object.fromEntries(Object.entries(t).map(([k,i])=>[k,subset(i,k)])))}:{})};
  }
  return {accessor,resource,primitive,finish:()=>Buffer.concat(chunks,size)};
}

function usedJoints(source,primitives) {
  const used=new Set();
  for(const p of primitives) {
    const weights=source.accessor(p.attributes.WEIGHTS_0),joints=source.accessor(p.attributes.JOINTS_0);
    for(const v of new Set(source.accessor(p.indices)))for(let k=0;k<4;k++)if(weights[v*4+k]>0)used.add(joints[v*4+k]);
  }
  check(used.size>0,'No positive skin influence');return [...used].sort((a,b)=>a-b);
}

export function extractHairPart(sourceBytes,baseBytes) {
  const comparison=compareHairSources(baseBytes,sourceBytes);
  check(['candidate','identical'].includes(comparison.status),`Incompatible source: ${comparison.blockers.join('; ')}`);
  const base=readHairSource(baseBytes),source=readHairSource(sourceBytes),ownership=hairOwnership(source);
  const local=[...new Set([...ownership.nodes,...ownership.meshNodes])].sort((a,b)=>a-b),anchors=new Set();
  function ref(i) {
    if(i<0)return null;
    const n=local.indexOf(i);if(n>=0)return {local:n};
    check(base.paths.includes(source.paths[i]),'Unknown base anchor');anchors.add(source.paths[i]);return {anchor:source.paths[i]};
  }
  const doc={schemaVersion:1,kind:'hair',styleId:comparison.status==='identical'?'e-original':'e-hair02',
    baseModelId:sha(baseBytes),sourceModelId:sha(sourceBytes),...fingerprints(source),nodes:[],geometry:[],springs:[],annotations:[]};
  const w=writer(doc);
  for(const i of local) {
    const {children,mesh,skin,...node}=clone(source.json.nodes[i]);doc.nodes.push({node,parent:ref(source.parents[i])});
  }
  const refs=new Set(source.report.hair.map(p=>`${p.mesh}:${p.primitive}`));
  source.json.nodes.forEach((node,ni)=>{
    if(node.mesh===undefined)return;
    const original=source.json.meshes[node.mesh],primitives=original.primitives.filter((_,i)=>refs.has(`${node.mesh}:${i}`));if(!primitives.length)return;
    const skin=source.json.skins[node.skin],used=usedJoints(source,primitives),matrices=source.accessor(skin.inverseBindMatrices);
    const {primitives:ignored,...settings}=clone(original);
    doc.geometry.push({target:ref(ni),mesh:{...settings,primitives:primitives.map(p=>w.primitive(source,p,i=>used.indexOf(i)))},
      skin:{joints:used.map(i=>ref(skin.joints[i])),inverseBindMatrices:w.accessor(used.flatMap(i=>Array.from(matrices.subarray(i*16,i*16+16))),'MAT4'),
        ...(skin.skeleton!==undefined?{skeleton:ref(skin.skeleton)}:{})}});
  });
  const spring=source.json.extensions.VRMC_springBone;
  doc.springs=ownership.springs.map(i=>{
    const s=clone(spring.springs[i]);s.joints=s.joints.map(j=>({...j,node:ref(j.node)}));if(s.center!==undefined)s.center=ref(s.center);return s;
  });
  doc.annotations=(source.json.extensions.VRMC_vrm.firstPerson?.meshAnnotations??[]).filter(a=>ownership.meshNodes.has(a.node)).map(a=>({...clone(a),node:ref(a.node)}));
  doc.anchors=[...anchors].sort();
  const bin=w.finish();doc.binLength=bin.length;doc.binSha256=sha(bin);
  const json=Buffer.from(JSON.stringify(doc)),info=inspectHairPart(json,bin);
  composeHairPart(baseBytes,json,bin);
  return {json,bin,partId:info.partId};
}

function readPart(jsonBytes,binBytes) {
  check(jsonBytes instanceof Uint8Array&&binBytes instanceof Uint8Array&&jsonBytes.length>0&&jsonBytes.length<=4*1024*1024&&jsonBytes.length+binBytes.length<=MAX,'Part size limit');
  const json=Buffer.from(jsonBytes),bin=Buffer.from(binBytes),doc=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(json));
  function walk(x,depth=0) {
    check(depth<=64,'Part JSON depth');if(typeof x==='number')check(Number.isFinite(x),'Part number');
    if(!x||typeof x!=='object')return;check(!Object.hasOwn(x,'uri'),'External resources forbidden');for(const value of Object.values(x))walk(value,depth+1);
  }walk(doc);
  check(doc.schemaVersion===1&&doc.kind==='hair'&&['e-original','e-hair02'].includes(doc.styleId),'Unsupported part');
  for(const key of ['baseModelId','sourceModelId','protectedDigest','hairDigest','binSha256'])check(hex(doc[key]),'Invalid part hash');
  check(doc.binLength===bin.length&&doc.binSha256===sha(bin),'Part BIN hash mismatch');
  for(const [key,max] of Object.entries({nodes:4096,geometry:4096,accessors:8192,bufferViews:8192,materials:256,textures:256,images:64,samplers:256,springs:4096,anchors:4096,annotations:4096}))check(Array.isArray(doc[key])&&doc[key].length<=max,`Invalid part ${key}`);
  check(doc.geometry.length>0&&doc.materials.length>0&&doc.materials.every(m=>hairNames.has(m.name)),'Non-hair part material');
  check(new Set(doc.anchors).size===doc.anchors.length&&doc.anchors.every(p=>typeof p==='string'&&p.length>0&&p.length<=32768),'Invalid anchors');
  function validateRef(ref,nullable=false) {
    if(ref===null&&nullable)return;
    check(ref&&typeof ref==='object'&&Object.keys(ref).length===1,'Invalid node reference');
    if(Object.hasOwn(ref,'local'))at(doc.nodes,ref.local);else check(Object.hasOwn(ref,'anchor')&&doc.anchors.includes(ref.anchor),'Missing anchor');
  }
  for(const [i,n] of doc.nodes.entries()) {
    validateRef(n.parent,true);check(n.node&&typeof n.node.name==='string'&&n.node.name.length>0&&!n.node.name.includes('/'),'Invalid node name');
    check(!['mesh','skin','children'].some(k=>k in n.node),'Unexpected node ownership');
    let parent=n.parent,depth=0;const seen=new Set([i]);
    while(parent&&'local' in parent){check(!seen.has(parent.local)&&++depth<=128,'Part node cycle/depth');seen.add(parent.local);parent=at(doc.nodes,parent.local).parent;}
  }
  for(const g of doc.geometry) {
    validateRef(g.target);check(g.skin&&Array.isArray(g.skin.joints)&&g.skin.joints.length>0,'Missing part skin');g.skin.joints.forEach(r=>validateRef(r));if(g.skin.skeleton!==undefined)validateRef(g.skin.skeleton);
    check(new Set(g.skin.joints.map(stable)).size===g.skin.joints.length,'Duplicate part joints');
  }
  for(const s of doc.springs){check(Array.isArray(s.joints)&&s.joints.length>0,'Empty spring');s.joints.forEach(j=>validateRef(j.node));if(s.center!==undefined)validateRef(s.center);}
  doc.annotations.forEach(a=>validateRef(a.node));
  // Strict glTF validation with inert anchor placeholders, never the base's actual bones or geometry.
  const nodes=doc.nodes.map(n=>clone(n.node)),anchorIndices=new Map(doc.anchors.map((name,i)=>[name,nodes.length+i]));
  nodes.push(...doc.anchors.map((_,i)=>({name:`anchor-${i}`})));
  const resolve=r=>'local' in r?r.local:anchorIndices.get(r.anchor);
  doc.nodes.forEach((n,i)=>{if(n.parent)(nodes[resolve(n.parent)].children??=[]).push(i);});
  const projection={asset:{version:'2.0'},nodes,meshes:[],skins:[],scenes:[{nodes:[]}],scene:0,buffers:[{byteLength:bin.length}],...Object.fromEntries(tables.map(k=>[k,clone(doc[k])])),
    extensions:{VRMC_vrm:{specVersion:'1.0',humanoid:{humanBones:{head:{node:0},hips:{node:0}}}}}};
  for(const g of doc.geometry) {
    const node=nodes[resolve(g.target)];check(node.mesh===undefined,'Duplicate geometry target');node.mesh=projection.meshes.length;node.skin=projection.skins.length;projection.meshes.push(clone(g.mesh));
    projection.skins.push({...clone(g.skin),joints:g.skin.joints.map(resolve),...(g.skin.skeleton!==undefined?{skeleton:resolve(g.skin.skeleton)}:{})});
  }
  const validated=readHairSource(pack(projection,bin));check(validated.report.status==='inspected',validated.report.reasons.join('; '));check(validated.report.dependencies.textureBudget.status==='within','Texture budget unsupported or exceeded');
  return {doc,bin,accessor:validated.accessor,partId:sha(Buffer.concat([json,bin]))};
}

export function inspectHairPart(json,bin) {
  const p=readPart(json,bin),d=p.doc;
  return {partId:p.partId,styleId:d.styleId,baseModelId:d.baseModelId,sourceModelId:d.sourceModelId,protectedDigest:d.protectedDigest,hairDigest:d.hairDigest,
    protectedPrimitiveCount:0,anchors:d.anchors,imageHashes:d.images.map(i=>{const v=d.bufferViews[i.bufferView];return sha(p.bin.subarray(v.byteOffset??0,(v.byteOffset??0)+v.byteLength));})};
}

function pack(json,bin) {
  const text=Buffer.from(JSON.stringify(json)),length=Math.ceil(text.length/4)*4,total=28+length+bin.length;
  check(length<=4*1024*1024&&total<=MAX&&bin.length%4===0,'Output GLB size');
  const out=Buffer.alloc(total);out.writeUInt32LE(0x46546c67);out.writeUInt32LE(2,4);out.writeUInt32LE(total,8);
  out.writeUInt32LE(length,12);out.writeUInt32LE(0x4e4f534a,16);out.fill(32,20,20+length);text.copy(out,20);
  out.writeUInt32LE(bin.length,20+length);out.writeUInt32LE(0x004e4942,24+length);bin.copy(out,28+length);return out;
}

export function composeHairPart(baseBytes,json,bin) {
  const part=readPart(json,bin),d=part.doc;check(sha(baseBytes)===d.baseModelId,'Base model mismatch');
  const base=readHairSource(baseBytes),before=fingerprints(base);check(before.protectedDigest===d.protectedDigest,'Protected fingerprint mismatch');
  const ownership=hairOwnership(base),removed=new Set([...ownership.nodes,...ownership.meshNodes]),j=clone(base.json),baseMap=new Map();j.nodes=[];j.meshes=[];j.skins=[];
  base.json.nodes.forEach((node,i)=>{if(!removed.has(i)){baseMap.set(i,j.nodes.length);j.nodes.push(clone(node));}});
  const localStart=j.nodes.length;j.nodes.push(...d.nodes.map(n=>clone(n.node)));
  const ref=r=>{if('local' in r)return localStart+r.local;const i=base.paths.indexOf(r.anchor);check(baseMap.has(i),'Missing base anchor');return baseMap.get(i);};
  const bnode=i=>{check(baseMap.has(i),'Protected reference points to hair');return baseMap.get(i);};
  for(const [old,n] of baseMap)if(j.nodes[n].children)j.nodes[n].children=j.nodes[n].children.filter(x=>baseMap.has(x)).map(bnode);
  d.nodes.forEach((n,i)=>{if(n.parent)(j.nodes[ref(n.parent)].children??=[]).push(localStart+i);});
  j.scenes=j.scenes.map(s=>({...s,nodes:[...(s.nodes??[]).filter(n=>baseMap.has(n)).map(bnode),...d.nodes.flatMap((n,i)=>n.parent===null?[localStart+i]:[])]}));
  const w=writer(j),partSource={json:d,bin:part.bin,accessor:part.accessor};
  const hairRefs=new Set(base.report.hair.map(p=>`${p.mesh}:${p.primitive}`)),geometry=new Map(d.geometry.map(g=>[ref(g.target),g])),origins=new Map([...baseMap].map(([old,n])=>[n,old]));
  for(let ni=0;ni<j.nodes.length;ni++) {
    const old=origins.get(ni),bn=old===undefined?null:base.json.nodes[old],g=geometry.get(ni);if(bn?.mesh===undefined&&!g)continue;
    const bm=bn?.mesh===undefined?null:base.json.meshes[bn.mesh],ps=bm?.primitives.filter((_,i)=>!hairRefs.has(`${bn.mesh}:${i}`))??[];
    const skin=bn?.skin===undefined?null:base.json.skins[bn.skin],joints=[],matrices=[],jointIndices=new Map();
    const add=(node,matrix)=>{
      if(jointIndices.has(node)){const index=jointIndices.get(node);check(stable(matrices.slice(index*16,index*16+16))===stable(matrix),'Conflicting bind matrix');return index;}
      const index=joints.length;jointIndices.set(node,index);joints.push(node);matrices.push(...matrix);return index;
    };
    const baseJoints=new Map(),partJoints=new Map();
    if(ps.length){const data=base.accessor(skin.inverseBindMatrices);for(const i of usedJoints(base,ps))baseJoints.set(i,add(bnode(skin.joints[i]),Array.from(data.subarray(i*16,i*16+16))));}
    if(g){const data=part.accessor(g.skin.inverseBindMatrices);g.skin.joints.forEach((n,i)=>partJoints.set(i,add(ref(n),Array.from(data.subarray(i*16,i*16+16)))));}
    const skeleton=skin?.skeleton===undefined?g?.skin.skeleton===undefined?undefined:ref(g.skin.skeleton):bnode(skin.skeleton);
    if(skin?.skeleton!==undefined&&g?.skin.skeleton!==undefined)check(skeleton===ref(g.skin.skeleton),'Skeleton mismatch');
    j.nodes[ni].skin=j.skins.length;j.skins.push({joints,inverseBindMatrices:w.accessor(matrices,'MAT4'),...(skeleton!==undefined?{skeleton}:{})});
    const mesh=clone(bm??g.mesh);mesh.primitives=[...ps.map(p=>w.primitive(base,p,i=>baseJoints.get(i))),...(g?.mesh.primitives??[]).map(p=>w.primitive(partSource,p,i=>partJoints.get(i)))];
    j.nodes[ni].mesh=j.meshes.length;j.meshes.push(mesh);
  }
  const nodeRefs=(value,resolve)=>Array.isArray(value)?value.map(v=>nodeRefs(v,resolve)):value&&typeof value==='object'?Object.fromEntries(Object.entries(value).map(([k,v])=>[k,['node','center'].includes(k)?resolve(v):nodeRefs(v,resolve)])):value;
  const vrm=clone(base.json.extensions.VRMC_vrm);
  if(vrm.firstPerson?.meshAnnotations)vrm.firstPerson.meshAnnotations=vrm.firstPerson.meshAnnotations.filter(a=>baseMap.has(a.node));j.extensions.VRMC_vrm=nodeRefs(vrm,bnode);
  if(d.annotations.length)(j.extensions.VRMC_vrm.firstPerson??={meshAnnotations:[]}).meshAnnotations.push(...d.annotations.map(a=>({...a,node:ref(a.node)})));
  const expr=j.extensions.VRMC_vrm.expressions??{};
  for(const e of [...Object.values(expr.preset??{}),...Object.values(expr.custom??{})])for(const bind of [...(e.materialColorBinds??[]),...(e.textureTransformBinds??[])])bind.material=w.resource(base,'materials',bind.material);
  if(vrm.meta?.thumbnailImage!==undefined)j.extensions.VRMC_vrm.meta.thumbnailImage=w.resource(base,'images',vrm.meta.thumbnailImage);
  const bs=base.json.extensions.VRMC_springBone;
  if(bs||d.springs.length) {
    const preserved=clone(bs??{specVersion:'1.0',springs:[]});preserved.springs=(preserved.springs??[]).filter((_,i)=>!ownership.springs.includes(i));
    const out=nodeRefs(preserved,bnode);out.springs.push(...d.springs.map(s=>({...clone(s),joints:s.joints.map(j=>({...j,node:ref(j.node)})),...(s.center!==undefined?{center:ref(s.center)}:{})})));j.extensions.VRMC_springBone=out;
  }
  const outputBin=w.finish();j.buffers=[{byteLength:outputBin.length}];const bytes=pack(j,outputBin),output=readHairSource(bytes);
  check(output.report.status==='inspected','Composed model validation failed');const after=fingerprints(output);
  check(after.protectedDigest===d.protectedDigest,'Protected appearance changed');check(after.hairDigest===d.hairDigest,'Hair fingerprint changed');
  return {bytes,modelId:sha(bytes),partId:part.partId,...after};
}
