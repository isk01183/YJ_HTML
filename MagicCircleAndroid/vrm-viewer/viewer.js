import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { VRMLoaderPlugin, VRMUtils } from '@pixiv/three-vrm';
import { fitDistance } from './camera.js';

const params = new URLSearchParams(location.search);
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
$('details').textContent = w('파일은 이 기기에만 저장됩니다. 배경화면은 변경하지 않습니다.','ファイルはこの端末だけに保存。壁紙は変更しません。','Stored on this device only. Your wallpaper is unchanged.');

let renderer, controls, vrm, camera, scene;
let disposed = false, paused = false, frame = 0, last = 0, elapsed = 0;
let tPose = false, blinking = false, view = 'full';
let bounds, bodyHeight = 1.6;
const info = {state:'empty', triangles:0, materials:0, frames:0};
function fail(message) {
  info.state = 'error'; status.textContent = message;
  document.querySelectorAll('button').forEach(button => button.disabled = true);
  pause();
}
function pause() { paused = true; cancelAnimationFrame(frame); frame = 0; last = 0; }
function resume() {
  if (disposed || info.state === 'error') return;
  paused = false; last = 0;
  if (renderer && !frame) frame = requestAnimationFrame(tick);
}
function dispose() {
  if (disposed) return;
  disposed = true; pause(); controls?.dispose();
  if (scene) VRMUtils.deepDispose(scene);
  renderer?.dispose(); renderer?.forceContextLoss();
  vrm = null;
}
window.vrmPreview = {pause, resume, dispose, info};
addEventListener('pagehide', dispose);
document.addEventListener('visibilitychange', () => document.hidden ? pause() : resume());

function resize() {
  if (!renderer || disposed) return;
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
    distance = fitDistance(size.x, size.y, size.z, camera.aspect, camera.fov);
  }
  camera.position.copy(center).add(new THREE.Vector3(0,0,distance));
  controls.target.copy(center); controls.minDistance = bodyHeight * .22; controls.maxDistance = bodyHeight * 7;
  controls.update();
}
function pose() {
  vrm.humanoid.resetNormalizedPose();
  if (!tPose) {
    vrm.humanoid.setNormalizedPose({
      leftUpperArm: {rotation: new THREE.Quaternion().setFromEuler(new THREE.Euler(0,0,-1.15)).toArray()},
      rightUpperArm: {rotation: new THREE.Quaternion().setFromEuler(new THREE.Euler(0,0,1.15)).toArray()},
    });
  }
  vrm.update(0); vrm.scene.updateMatrixWorld(true);
  bounds = new THREE.Box3().setFromObject(vrm.scene, true);
  bodyHeight = bounds.max.y - bounds.min.y;
  frameView();
}
function tick(time) {
  frame = 0;
  if (paused || disposed) return;
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
  controls.update(); renderer.render(scene, camera); info.frames++;
}

async function init() {
  if (params.get('model') !== '1') {
    status.textContent = w('위의 「VRM 불러오기」에서 test.vrm 파일을 선택하세요.','上の「VRMを開く」からVRMファイルを選択してください。','Use Import VRM above to choose your .vrm file.');
    return;
  }
  status.textContent = w('모델과 MToon 재질을 불러오는 중…','モデルとMToon材質を読み込み中…','Loading model and MToon materials…');
  info.state = 'loading';
  try {
    renderer = new THREE.WebGLRenderer({antialias:true, alpha:true, powerPreference:'low-power'});
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.NoToneMapping;
    renderer.setClearColor(0x000000,0);
    stage.appendChild(renderer.domElement);
    renderer.domElement.addEventListener('webglcontextlost', event => {
      event.preventDefault();
      if (!disposed) fail(w('그래픽 메모리가 부족합니다. 이 화면을 닫고 다시 열어주세요.','描画が中断されました。画面を開き直してください。','Graphics interrupted. Close and reopen this preview.'));
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
    resize(); addEventListener('resize',resize);
    const manager = new THREE.LoadingManager();
    const modelUrl = new URL('model.vrm',location.href).href;
    manager.setURLModifier(url => {
      if (url === modelUrl || url.startsWith('blob:')) return url;
      throw new Error('External model resource blocked');
    });
    const loader = new GLTFLoader(manager);
    loader.register(parser => new VRMLoaderPlugin(parser));
    const gltf = await loader.loadAsync(modelUrl);
    if (disposed) { VRMUtils.deepDispose(gltf.scene); return; }
    vrm = gltf.userData.vrm;
    if (!vrm || vrm.meta.metaVersion !== '1') throw new Error('VRM 1.0 required');
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
    scene.add(vrm.scene); pose();
    const floor = new THREE.Mesh(new THREE.CircleGeometry(.42,64),new THREE.MeshBasicMaterial({color:0x707c8e,transparent:true,opacity:.08,depthWrite:false}));
    floor.rotation.x = -Math.PI/2; floor.position.y = bounds.min.y - .006; scene.add(floor);
    renderer.render(scene,camera);
    info.state = 'ready'; status.textContent = '';
    document.querySelectorAll('button').forEach(button => button.disabled = false);
    $('full').onclick = () => {view = 'full'; frameView();};
    $('face').onclick = () => {view = 'face'; frameView();};
    $('pose').onclick = () => {tPose = !tPose; $('pose').setAttribute('aria-pressed',String(tPose)); pose();};
    $('motion').onclick = () => {blinking = !blinking; $('motion').setAttribute('aria-pressed',String(blinking));};
    if (!paused && !document.hidden) resume();
  } catch (error) {
    console.error('VRM preview failed', error.message);
    fail(w('이 모델을 표시하지 못했습니다. VRM 1.0 파일과 최신 Android System WebView가 필요합니다.','表示できません。VRM 1.0と最新のAndroid System WebViewをご確認ください。','Could not display model. Use VRM 1.0 and an up-to-date Android System WebView.'));
  }
}
init();
