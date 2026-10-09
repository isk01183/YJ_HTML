import { normalizePlacement } from './viewer-state.js';
export function reviewCamera({yaw,target,distance,blink}) {
  if(!Number.isFinite(yaw)||Math.abs(yaw)>3600||!Array.isArray(target)||target.length!==3||
      !target.every(n=>Number.isFinite(n)&&Math.abs(n)<=10)||!Number.isFinite(distance)||distance<.1||distance>20||
      !Number.isFinite(blink)||blink<0||blink>1)throw new Error('Invalid review camera');
  const angle=yaw*Math.PI/180;
  return {target:[...target],position:[target[0]+Math.sin(angle)*distance,target[1],target[2]+Math.cos(angle)*distance],blink};
}
export function fitDistance(width, height, depth, aspect, fov) {
  if (![width, height, depth, aspect, fov].every(Number.isFinite) ||
      width <= 0 || height <= 0 || depth < 0 || aspect <= 0 || fov <= 0 || fov >= 170) {
    throw new Error('Invalid model bounds');
  }
  return Math.max(height, width / aspect) * 0.56 / Math.tan(fov * Math.PI / 360) + depth / 2;
}
export function placementFrame(width,height,depth,aspect,fov,value) {
  const p=normalizePlacement(value);
  const distance=fitDistance(width,height,depth,aspect,fov)/p.scale;
  const viewportHeight=2*distance*Math.tan(fov*Math.PI/360);
  return {distance,offsetX:p.x*viewportHeight*aspect,offsetY:-p.y*viewportHeight};
}
