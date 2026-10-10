import {test} from 'node:test';
import assert from 'node:assert/strict';
import {Texture} from 'three';
import {MToonMaterial} from '@pixiv/three-vrm';
import {bindAvatarDye,normalizeDye,avatarProfile,irisProtection,normalizeAppearance} from './avatar-dye.js';
const BASE='ef6513de66aee3ab78b105e53b2e72c5d92834fc2a49c542221c08ec9f0811d0';
test('v2 accepts only matching native part proof and keeps independent dye',()=>{
  const receipt={modelId:'a'.repeat(64),baseModelId:BASE,partId:'b'.repeat(64),styleId:'e-original',assemblerVersion:1,protectedDigest:'c'.repeat(64),hairDigest:'d'.repeat(64)};
  const value={profileVersion:2,baseModelId:BASE,hairId:'e-original',modelId:receipt.modelId,parts:{hair:receipt.partId},hair:'#12ABEF',iris:'#123456'};
  assert.deepEqual(normalizeAppearance(value,receipt),value);
  assert.throws(()=>normalizeAppearance(value));
  for(const changed of [{...receipt,modelId:'e'.repeat(64)},{...receipt,partId:'e'.repeat(64)},{...receipt,styleId:'e-hair02'},{...receipt,assemblerVersion:2}])assert.throws(()=>normalizeAppearance(value,changed));
  assert.throws(()=>normalizeAppearance({...value,parts:{...value.parts,eyes:receipt.partId}},receipt));
  const donor='f4df98833a830f84c6f8bcdb90701e86420bc2fe369e7936fff1cf3b0971573e';
  assert.throws(()=>normalizeAppearance({...value,modelId:donor},{...receipt,modelId:donor}));
  const profile=avatarProfile(receipt.modelId,receipt);assert.deepEqual(profile.hair,avatarProfile(BASE).hair);
  const {vrm}=fixture();bindAvatarDye(vrm,profile).set(value);
  assert.equal(normalizeAppearance(value,receipt).iris,value.iris);
  assert.equal(normalizeAppearance({profileVersion:1,baseModelId:BASE,hairId:'e-original',modelId:BASE,hair:null,iris:null}).hair,null);
});
function fixture(){
  const profile=avatarProfile(BASE);
  const all=[...profile.hair,profile.iris,'Body','EyeWhite','EyeHighlight','CatEars','Tail','Clothes'].map(name=>{
    const map=new Texture({width:name===profile.iris?1024:512,height:name===profile.iris?512:1024});map.flipY=false;
    const material=new MToonMaterial({map,shadeMultiplyTexture:map});material.name=name;return material;
  });
  return {all,profile,vrm:{scene:{traverse:fn=>all.forEach(material=>fn({isMesh:true,material}))}}};
}
test('defaultPreservesOriginalMaterials',()=>{
  const {all,profile,vrm}=fixture(),source=all.map(m=>m.fragmentShader),maps=all.map(m=>m.map);
  const dye=bindAvatarDye(vrm,profile);dye.set(normalizeDye({hair:null,iris:null}));
  assert.deepEqual(all.map(m=>m.fragmentShader),source);assert.deepEqual(all.map(m=>m.map),maps);
  dye.dispose();assert.deepEqual(all.map(m=>m.fragmentShader),source);
});
test('hairDoesNotDyeSkinClothesOrEars',()=>{
  const {all,profile,vrm}=fixture(),before=all.map(m=>m.fragmentShader),callbacks=all.map(m=>m.onBeforeCompile);
  bindAvatarDye(vrm,profile).set({hair:'#F4EBDD',iris:null});
  all.forEach((m,i)=>{assert.equal(m.fragmentShader!==before[i],profile.hair.includes(m.name));assert.equal(m.onBeforeCompile,callbacks[i]);});
  assert.match(all[0].fragmentShader,/material.shadeColor = avatarRecolor/);
});
test('irisPreservesWhitePupilHighlight',()=>{
  const {all,profile,vrm}=fixture(),before=all.map(m=>m.fragmentShader);
  bindAvatarDye(vrm,profile).set({hair:null,iris:'#FF2030'});
  all.forEach((m,i)=>assert.equal(m.fragmentShader!==before[i],m.name===profile.iris));
  for(const x of [264,760]) assert.equal(irisProtection([x/1024,280/512],[.07,.10,.29]),1);
  assert.equal(irisProtection([264/1024,358/512],[.92,1,1]),1);
  assert.equal(irisProtection([300/1024,320/512],[.07,.10,.29]),0);
});
test('restoreOnePartPreservesOther',()=>{
  const {all,profile,vrm}=fixture(),before=all.map(m=>m.fragmentShader),dye=bindAvatarDye(vrm,profile);
  dye.set({hair:'#000000',iris:'#1234FF'});const iris=all.find(m=>m.name===profile.iris),shader=iris.fragmentShader;
  dye.set({hair:null,iris:'#1234FF'});assert.equal(all[0].fragmentShader,before[0]);assert.equal(iris.fragmentShader,shader);
  const texture=iris.map;dye.set({hair:null,iris:'#FF0000'});assert.equal(iris.fragmentShader,shader);assert.equal(iris.map,texture);
  dye.dispose();assert.deepEqual(all.map(m=>m.fragmentShader),before);
});
test('unknownProfileFailsClosed',()=>{
  assert.throws(()=>avatarProfile('unknown'));assert.throws(()=>normalizeDye({hair:'#123',iris:null}));
  assert.throws(()=>normalizeDye({hair:NaN,iris:null}));assert.deepEqual(normalizeDye({hair:'#aabbcc',iris:null}),{hair:'#AABBCC',iris:null});
  const {all,profile,vrm}=fixture();all[0].fragmentShader='changed upstream';
  assert.throws(()=>bindAvatarDye(vrm,profile));assert.equal(all[1].uniforms.avatarTarget,undefined);
});
