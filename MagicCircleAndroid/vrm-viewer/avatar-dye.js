import {Color} from 'three';
const BASE='ef6513de66aee3ab78b105e53b2e72c5d92834fc2a49c542221c08ec9f0811d0';
const HAIR02='c1853aa3c22b5c3b58ba8f819c4b2c4e7bd318aeeefaa9739f7c9f3bb205a708';
const IRIS='N00_000_00_EyeIris_00_EYE (Instance)';
const names=['N00_000_00_HairBack_00_HAIR (Instance)',...['01','02','03'].map(n=>`N00_000_Hair_00_HAIR_${n} (Instance)`)];
export function avatarProfile(modelId){
  if(modelId!==BASE&&modelId!==HAIR02)throw new Error('Unverified avatar profile');
  return {modelId,hair:modelId===BASE?names:['N00_000_Hair_00_HAIR (Instance)'],iris:IRIS};
}
export function normalizeDye(value){
  if(!value||typeof value!=='object')throw new Error('Invalid dye');
  const color=v=>{if(v===null)return null;if(typeof v!=='string'||!/^#[\da-f]{6}$/i.test(v))throw new Error('Invalid dye color');return v.toUpperCase();};
  return {hair:color(value.hair),iris:color(value.iris)};
}
export function normalizeAppearance(value){
  if(value===null)return null;
  if(value?.profileVersion!==1||value.baseModelId!==BASE||value.modelId!==({'e-original':BASE,'e-hair02':HAIR02})[value.hairId])throw new Error('Invalid avatar appearance');
  avatarProfile(value.modelId);return {...value,...normalizeDye(value)};
}
const marker='material.shadingShift = shadingShiftFactor;';
// shortcut: pupil UV protection is specific to the two hash-verified E exports; validate masks before adding another model.
const protection=`
float avatarIrisMask(vec2 uv, vec3 color) {
  vec2 pixel = uv * vec2(1024.0,512.0);
  float pupil = min(length((pixel-vec2(264.0,280.0))/vec2(35.0,34.0)), length((pixel-vec2(760.0,280.0))/vec2(35.0,34.0)));
  float luma = dot(color,vec3(0.2126,0.7152,0.0722));
  float saturation = 1.0-min(min(color.r,color.g),color.b)/max(max(max(color.r,color.g),color.b),0.0001);
  return max(1.0-smoothstep(1.0,1.18,pupil),step(0.90,luma)*(1.0-step(0.36,saturation)));
}`;
export function irisProtection(uv,color){
  const pixel=[uv[0]*1024,uv[1]*512];const d=Math.min(...[264,760].map(x=>Math.hypot((pixel[0]-x)/35,(pixel[1]-280)/34)));
  const t=Math.max(0,Math.min(1,(d-1)/.18)),pupil=1-t*t*(3-2*t);
  const luma=color[0]*.2126+color[1]*.7152+color[2]*.0722,s=1-Math.min(...color)/Math.max(.0001,...color);
  return Math.max(pupil,luma>=.9&&s<.36?1:0);
}
const helpers=`uniform vec3 avatarTarget;
${protection}
vec3 avatarRecolor(vec3 source, float protectedMask) {
  float light = dot(source,vec3(0.2126,0.7152,0.0722));
  // Relative texture shading keeps strands visible even at black and lifts dark source hair for platinum.
  float detail = pow(clamp(light,0.0001,1.0),0.7)*0.72;
  vec3 dyed = max(avatarTarget,vec3(0.012))*detail + vec3(0.012)*smoothstep(0.55,0.95,light);
  return mix(dyed,source,protectedMask);
}
`;
export function bindAvatarDye(vrm,profile){
  const verified=avatarProfile(profile?.modelId),materials=new Set();
  vrm.scene.traverse(object=>{if(object.isMesh)for(const m of Array.isArray(object.material)?object.material:[object.material])materials.add(m);});
  const entries=[];
  for(const name of [...verified.hair,verified.iris]){
    const matches=[...materials].filter(m=>m.name===name&&!m.isOutline);
    if(matches.length!==1)throw new Error(`Avatar material mismatch: ${name}`);
    const material=matches[0],source=material.fragmentShader;
    const map=material.map,shade=material.shadeMultiplyTexture;
    if(!material.isMToonMaterial||!map||!shade||source?.split(marker).length!==2||!source.includes('vec2 mapUv ='))throw new Error('Unsupported MToon dye shader');
    map.updateMatrix();shade.updateMatrix();
    if(map.flipY||shade.flipY||!map.matrix.equals(shade.matrix)||map.image!==shade.image)throw new Error('Avatar texture mismatch');
    if(name===IRIS&&(map.image.width!==1024||map.image.height!==512))throw new Error('Avatar iris mask mismatch');
    entries.push({material,source,part:name===IRIS?'iris':'hair',active:false});
  }
  let disposed=false;
  return {
    set(value){
      if(disposed)throw new Error('Disposed avatar dye');
      const dye=normalizeDye(value);
      for(const e of entries){
        const color=dye[e.part],active=color!==null,m=e.material;
        if(active){
          if(!m.uniforms.avatarTarget)m.uniforms.avatarTarget={value:new Color()};
          m.uniforms.avatarTarget.value.set(color);m.uniformsNeedUpdate=true;
        }
        if(e.active===active)continue;
        const mask=e.part==='iris'?'avatarIrisMask(mapUv,material.diffuseColor)':'0.0';
        m.fragmentShader=active?helpers+e.source.replace(marker,`float avatarProtected = ${mask};\nmaterial.diffuseColor = avatarRecolor(material.diffuseColor,avatarProtected);\nmaterial.shadeColor = avatarRecolor(material.shadeColor,avatarProtected);\n${marker}`):e.source;
        m.needsUpdate=true;e.active=active;
      }
    },
    dispose(){if(disposed)return;for(const e of entries){if(e.active){e.material.fragmentShader=e.source;e.material.needsUpdate=true;}delete e.material.uniforms.avatarTarget;}disposed=true;}
  };
}
