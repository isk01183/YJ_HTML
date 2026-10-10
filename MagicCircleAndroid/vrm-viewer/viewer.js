import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';
import { fitDistance, placementFrame, reviewCamera } from './camera.js';
import { shouldRender, normalizePlacement, relaxedArmAngle } from './viewer-state.js';
import { ModelMemory, renderFrame } from './model-memory.js';
import {bindAvatarDye,avatarProfile,normalizeAppearance} from './avatar-dye.js';

const params = new URLSearchParams(location.search);
const wallpaper=params.get('wallpaper')==='1';
const composition=params.get('composition')==='1';
document.documentElement.classList.toggle('composition',composition);
document.body.classList.toggle('wallpaper',wallpaper);
document.body.classList.toggle('avatar-editor',params.get('editor')==='1');
const lang = ['ko','ja','en'].includes(params.get('lang')) ? params.get('lang') : 'ko';
const w = (ko, ja, en) => ({ko,ja,en})[lang];
document.documentElement.lang = lang;
const $ = id => document.getElementById(id);
const stage = $('stage'), status = $('status');
stage.setAttribute('aria-label', w('3D 캐릭터 미리보기','3Dキャラクタープレビュー','3D character preview'));
$('hint').textContent = w('한 손가락: 회전 · 두 손가락: 확대/이동','1本指：回転・2本指：拡大/移動','Drag to rotate · Pinch to zoom / pan');
$('full').textContent = w('전신','全身','Full body');
$('face').textContent = w('얼굴','顔','Face');
$('pose').textContent = w('T 포즈','Tポーズ','T pose');
$('motion').textContent = w('눈 깜박임','まばたき','Blink');
$('details').textContent = w('파일은 이 기기에만 저장됩니다. 적용은 시스템 화면에서 확인하세요.','端末内だけに保存。壁紙の適用はシステム画面で確認してください。','Stored only on this device. Confirm wallpaper in the system preview.');

let renderer, controls, vrm, camera, scene, memory;
let modelScenes=[];
let avatar=null,avatarEpoch=0,dyeBinding=null,dyeModelId=null;
let partReceipt=null,proofReady=false,pendingAppearance=null;
let disposed = false, released = false, hostActive = false, frame = 0, last = 0, elapsed = 0;
let placement=normalizePlacement();
let tPose = false, blinking = true, view = params.get('editor')==='1'?'face':'full';
let bounds, bodyHeight = 1.6;
const info = {state:'empty', triangles:0, materials:0, frames:0,metaVersion:null,failure:null};
Object.defineProperty(info,'gpuTextures',{enumerable:true,get:()=>renderer?.info.memory.textures || 0});
function fail(message,kind='MODEL') {
  info.state = 'error'; info.failure=kind; status.textContent = message;
  document.querySelectorAll('button').forEach(button => button.disabled = true);
  pause(); release();
}
function reportError(error) {
  console.error('VRM preview failed',error.message);
  if(disposed || released)return;
  if(error.kind==='MEMORY')fail(w('원본 텍스처를 표시할 메모리가 부족합니다. 다른 앱을 닫고 다시 시도하세요. 화질은 변경하지 않았습니다.','元のテクスチャを表示するメモリが不足しています。他のアプリを閉じて再試行してください。画質は変更していません。','Not enough memory for the original textures. Close other apps and retry. Texture quality has not been changed.'),'MEMORY');
  else if(error.kind==='CONTEXT')fail(w('그래픽 보기가 중단되었습니다. 다시 시도하세요.','描画が中断されました。再試行してください。','Graphics interrupted. Please retry.'),'CONTEXT');
  else fail(w('이 모델을 표시하지 못했습니다. VRM 파일과 Android System WebView를 확인하세요.','表示できません。VRMファイルとAndroid System WebViewをご確認ください。','Could not display model. Check the VRM file and Android System WebView.'));
}
function syncLoop() {
  if(shouldRender(hostActive,!document.hidden,info.state==='ready',disposed)) {
    if(renderer&&!frame)frame=requestAnimationFrame(tick);
  } else {cancelAnimationFrame(frame);frame=0;last=0;}
}
function pause() {hostActive=false;syncLoop();}
function resume() {hostActive=true;syncLoop();}
function configure(value) {
  placement=normalizePlacement(value);blinking=placement.blink;view='full';
  $('motion').setAttribute('aria-pressed',String(blinking));
  if(vrm)frameView();
}
function appearance(value) {
  if(!proofReady){pendingAppearance=value;avatarEpoch++;return;}
  const next=normalizeAppearance(value,partReceipt);
  if(vrm&&next&&dyeModelId&&dyeModelId!==next.modelId)throw new Error('Avatar model switch requires reload');
  avatar=next;avatarEpoch++;
  if(vrm)try {applyAppearance();}catch(error){reportError(error);throw error;}
}
function applyAppearance() {
  if(avatar&&!dyeBinding){dyeBinding=bindAvatarDye(vrm,avatarProfile(avatar.modelId,avatar.profileVersion===2?partReceipt:null));dyeModelId=avatar.modelId;}
  dyeBinding?.set(avatar??{hair:null,iris:null});
  info.appearance=avatar;
}
function dispose() {
  if (disposed) return;
  disposed = true; pause(); release();
}
function release() {
  if(released)return;
  released=true;controls?.dispose();
  dyeBinding?.dispose();dyeBinding=null;
  for(const modelScene of modelScenes) {modelScene.removeFromParent();VRMUtils.deepDispose(modelScene);}
  modelScenes=[];
  if (scene) VRMUtils.deepDispose(scene);
  memory?.dispose();
  renderer?.dispose(); renderer?.forceContextLoss();
  vrm = null;
}
window.vrmPreview = {pause, resume, dispose, configure, appearance, info};
window.vrmPreview.thumbnail=()=>{
  if(info.state!=='ready'||disposed)return null;
  const position=camera.position.clone(),target=controls.target.clone(),oldView=view;
  try {
    dyeBinding?.set({hair:null,iris:null});view='face';frameView();renderFrame(renderer,scene,camera);
    const canvas=document.createElement('canvas');canvas.width=192;canvas.height=224;
    const source=renderer.domElement,h=Math.min(source.height,source.width*224/192),width=h*192/224;
    canvas.getContext('2d').drawImage(source,(source.width-width)/2,(source.height-h)/2,width,h,0,0,192,224);
    return canvas.toDataURL('image/png');
  } finally {dyeBinding?.set(avatar??{hair:null,iris:null});view=oldView;camera.position.copy(position);controls.target.copy(target);controls.update();renderFrame(renderer,scene,camera);}
};
// Only the instrumented local test host supplies this marker; normal preview/wallpaper exposes no review controls.
if('VrmReview' in window) {
  window.vrmPreview.reviewAnchor=()=>{
    if(info.state!=='ready')throw new Error('Review model not ready');
    const center=bounds.getCenter(new THREE.Vector3()),size=bounds.getSize(new THREE.Vector3());
    const head=vrm.humanoid.getRawBoneNode('head').getWorldPosition(new THREE.Vector3());
    return {body:{target:center.toArray(),distance:fitDistance(size.x,size.y,size.z,camera.aspect,camera.fov)},
      face:{target:head.toArray(),distance:fitDistance(bodyHeight*.37,bodyHeight*.42,.1,camera.aspect,camera.fov)}};
  };
  window.vrmPreview.reviewView=value=>{
    const fixed=reviewCamera(value);
    if(info.state!=='ready'||disposed)throw new Error('Review model not ready');
    pause();controls.enabled=false;controls.enableDamping=false;controls.minDistance=.1;controls.maxDistance=20;
    camera.position.fromArray(fixed.position);controls.target.fromArray(fixed.target);controls.update();
    vrm.expressionManager?.setValue('blink',fixed.blink);vrm.update(0);
    renderFrame(renderer,scene,camera);info.frames++;
    return {position:camera.position.toArray(),target:controls.target.toArray(),blink:vrm.expressionManager?.getValue('blink')};
  };
  window.vrmPreview.reviewMotion=()=>{
    if(info.state!=='ready'||disposed)throw new Error('Review model not ready');
    pause();const joints=[...(vrm.springBoneManager?.joints??[])];
    const before=joints.map(j=>j.bone.quaternion.clone()),head=vrm.humanoid.getNormalizedBoneNode('head'),rotation=head.quaternion.clone();
    let finite=true;const moved=new Set();
    try {
      head.quaternion.setFromEuler(new THREE.Euler(.16,.1,.08));
      for(let step=0;step<30;step++) {
        vrm.update(1/30);
        joints.forEach((j,i)=>{
          if(!j.bone.matrixWorld.elements.every(Number.isFinite)||!j.bone.quaternion.toArray().every(Number.isFinite))finite=false;
          if(j.bone.quaternion.angleTo(before[i])>1e-5)moved.add(j.bone.name);
        });
      }
    } finally {head.quaternion.copy(rotation);vrm.update(0);vrm.springBoneManager?.reset();}
    return {jointCount:joints.length,uniqueBones:new Set(joints.map(j=>j.bone)).size,finite,moved:[...moved]};
  };
}
addEventListener('pagehide', dispose);
document.addEventListener('visibilitychange',syncLoop);

function resize() {
  if (!renderer || disposed || released) return;
  const width = stage.clientWidth, height = stage.clientHeight;
  if (!width || !height) return;
  // Render resolution and 30 fps cap keep a single preview bounded on mobile.
  renderer.setPixelRatio(Math.min(devicePixelRatio, 1.5));
  renderer.setSize(width, height);
  camera.aspect = width / height; camera.updateProjectionMatrix();
  if (vrm) frameView();
}
function frameView() {
  const center = new THREE.Vector3();
  let distance;
  if (view === 'face') {
    const head = vrm.humanoid.getRawBoneNode('head');
    head.getWorldPosition(center);
    distance = fitDistance(bodyHeight * .37, bodyHeight * .42, .1, camera.aspect, camera.fov);
  } else {
    bounds.getCenter(center);
    const size = bounds.getSize(new THREE.Vector3());
    const fit=placementFrame(size.x,size.y,size.z,camera.aspect,camera.fov,placement);
    distance=fit.distance;center.x-=fit.offsetX;center.y-=fit.offsetY;
  }
  camera.position.copy(center).add(new THREE.Vector3(0,0,distance));
  controls.target.copy(center); controls.minDistance = bodyHeight * .22; controls.maxDistance = bodyHeight * 7;
  controls.update();
}
function pose() {
  vrm.humanoid.resetNormalizedPose();
  if (!tPose) {
    const angle=relaxedArmAngle(vrm.meta.metaVersion);
    vrm.humanoid.setNormalizedPose({
      leftUpperArm: {rotation: new THREE.Quaternion().setFromEuler(new THREE.Euler(0,0,angle)).toArray()},
      rightUpperArm: {rotation: new THREE.Quaternion().setFromEuler(new THREE.Euler(0,0,-angle)).toArray()},
    });
  }
  vrm.update(0); vrm.scene.updateMatrixWorld(true);
  bounds = new THREE.Box3().setFromObject(vrm.scene, true);
  bodyHeight = bounds.max.y - bounds.min.y;
  frameView();
}
function tick(time) {
  frame = 0;
  if (!shouldRender(hostActive,!document.hidden,info.state==='ready',disposed)) return;
  frame = requestAnimationFrame(tick);
  if (last && time - last < 1000 / 30) return;
  const delta = last ? Math.min((time - last) / 1000, .05) : 0;
  last = time; elapsed += delta;
  if (vrm) {
    const phase = elapsed % 4.6;
    const blink = blinking && phase < .19 ? Math.sin(phase / .19 * Math.PI) : 0;
    vrm.expressionManager?.setValue('blink', blink);
    vrm.update(delta);
  }
  controls.update();
  try {renderFrame(renderer,scene,camera);info.frames++;}
  catch(error) {reportError(error);}
}

async function init() {
  if (params.get('model') !== '1') {
    status.textContent = w('위의 「VRM 불러오기」에서 test.vrm 파일을 선택하세요.','上の「VRMを開く」からVRMファイルを選択してください。','Use Import VRM above to choose your .vrm file.');
    return;
  }
  status.textContent = w('모델과 MToon 재질을 불러오는 중…','モデルとMToon材質を読み込み中…','Loading model and MToon materials…');
  info.state = 'loading';
  try {
    const initialEpoch=avatarEpoch;
    const initialResponse=await fetch(new URL('appearance.json',location.href),{cache:'no-store'});
    if(!initialResponse.ok)throw new Error('Missing local appearance');
    const initialAppearance=await initialResponse.json();
    const proof=await fetch(new URL('part-profile.json',location.href),{cache:'no-store'});
    if(!proof.ok)throw new Error('Missing local part proof');
    partReceipt=await proof.json();proofReady=true;
    avatar=normalizeAppearance(initialEpoch===avatarEpoch?initialAppearance:pendingAppearance,partReceipt);pendingAppearance=null;
    let budgetBytes;
    try {
      const response=await fetch(new URL('memory.json',location.href),{cache:'no-store'});
      if(response.ok)budgetBytes=(await response.json()).budgetBytes;
    } catch { /* A missing native budget must never fall back to unbounded decoding. */ }
    if(disposed)return;
    memory=new ModelMemory(budgetBytes,info);
    renderer = new THREE.WebGLRenderer({antialias:true, alpha:true, powerPreference:'low-power'});
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.NoToneMapping;
    renderer.setClearColor(0x000000,0);
    stage.appendChild(renderer.domElement);
    renderer.domElement.addEventListener('webglcontextlost', event => {
      event.preventDefault();
      if (!disposed && !released) fail(w('그래픽 보기가 중단되었습니다. 다시 시도하세요.','描画が中断されました。再試行してください。','Graphics interrupted. Please retry.'),'CONTEXT');
    });
    scene = new THREE.Scene();
    scene.add(new THREE.HemisphereLight(0xffffff,0xb3acaa,1.0));
    const key = new THREE.DirectionalLight(0xfff8f2,2.2); key.position.set(-1,2,3); scene.add(key);
    const fill = new THREE.DirectionalLight(0xe3edff,.6); fill.position.set(2,1,-2); scene.add(fill);
    camera = new THREE.PerspectiveCamera(30,1,.01,50);
    controls = new OrbitControls(camera,renderer.domElement);
    controls.enableDamping = true; controls.dampingFactor = .15;
    controls.minPolarAngle = .25; controls.maxPolarAngle = Math.PI - .25;
    controls.screenSpacePanning = true;
    controls.enabled=!wallpaper;
    resize(); addEventListener('resize',resize);
    const manager = new THREE.LoadingManager();
    const modelUrl = new URL('model.vrm',location.href).href;
    manager.setURLModifier(url => {
      if (url === modelUrl || url.startsWith('blob:')) return url;
      throw new Error('External model resource blocked');
    });
    const loader = new GLTFLoader(manager);
    loader.register(parser => memory.plugin(parser,renderer.capabilities.maxTextureSize));
    loader.register(parser => new VRMLoaderPlugin(parser));
    const gltf = await loader.loadAsync(modelUrl);
    if (disposed || released) {for(const modelScene of gltf.scenes)VRMUtils.deepDispose(modelScene);return;}
    modelScenes=gltf.scenes;
    memory.check(gltf);
    vrm = gltf.userData.vrm;
    if (!vrm || !['0','1'].includes(vrm.meta.metaVersion))throw new Error('Unsupported VRM');
    info.metaVersion=vrm.meta.metaVersion;
    VRMUtils.rotateVRM0(vrm);
    VRMUtils.removeUnnecessaryVertices(vrm.scene);
    VRMUtils.combineSkeletons(vrm.scene);
    const materials = new Set();
    vrm.scene.traverse(object => {
      object.frustumCulled = false;
      if (object.isMesh) {
        info.triangles += (object.geometry.index?.count ?? object.geometry.attributes.position.count) / 3;
        for (const material of Array.isArray(object.material) ? object.material : [object.material]) materials.add(material);
      }
    });
    info.materials = materials.size;
    applyAppearance();
    scene.add(vrm.scene); pose();
    if(!composition) {
      const floor = new THREE.Mesh(new THREE.CircleGeometry(.42,64),new THREE.MeshBasicMaterial({color:0x707c8e,transparent:true,opacity:.08,depthWrite:false}));
      floor.rotation.x = -Math.PI/2; floor.position.y = bounds.min.y - .006; scene.add(floor);
    }
    renderFrame(renderer,scene,camera,true);
    if(disposed || released)return;
    info.frames++;
    info.state = 'ready'; status.textContent = '';
    document.querySelectorAll('button').forEach(button => button.disabled = false);
    $('full').onclick = () => {view = 'full'; frameView();};
    $('face').onclick = () => {view = 'face'; frameView();};
    $('pose').onclick = () => {tPose = !tPose; $('pose').setAttribute('aria-pressed',String(tPose)); pose();};
    $('motion').onclick = () => {blinking = !blinking; $('motion').setAttribute('aria-pressed',String(blinking));};
    $('motion').setAttribute('aria-pressed',String(blinking));
    syncLoop();
  } catch (error) {
    reportError(error);
  }
}
init();
