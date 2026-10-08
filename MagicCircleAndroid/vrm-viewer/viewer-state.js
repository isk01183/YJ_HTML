export const shouldRender = (hostActive, documentVisible, ready, disposed) =>
  hostActive && documentVisible && ready && !disposed;
// Normalized bones retain the model's pre-rotateVRM0 coordinate axes.
export const relaxedArmAngle = metaVersion => metaVersion==='0' ? 1.15 : -1.15;
export function normalizePlacement(value = {}) {
  const bounded=(v,min,max,fallback)=>Number.isFinite(v)?Math.min(max,Math.max(min,v)):fallback;
  return {x:bounded(value.x,-.35,.35,0),y:bounded(value.y,-.35,.35,0),
    scale:bounded(value.scale,.5,1.5,1),blink:value.blink!==false};
}
