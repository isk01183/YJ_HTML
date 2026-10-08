import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fitDistance, placementFrame } from './camera.js';

test('portrait and landscape framing both contain the complete model', () => {
  for (const aspect of [0.45, 1, 1.8]) {
    const distance = fitDistance(1.8, 2, 0.3, aspect, 30);
    const visibleHeight = 2 * (distance - 0.15) * Math.tan(Math.PI / 12);
    assert.ok(visibleHeight > 2, 'head and feet must stay in frame');
    assert.ok(visibleHeight * aspect > 1.8, 'T-pose hands must stay in frame');
  }
});
test('placement offsets remain viewport-relative in portrait and landscape',()=>{
  for(const aspect of [.45,1.8])for(const scale of [.5,1,1.5]){
    const frame=placementFrame(1,2,.2,aspect,30,{x:.35,y:-.35,scale});
    const visibleHeight=2*frame.distance*Math.tan(Math.PI/12);
    assert.ok(Math.abs(frame.offsetX/(visibleHeight*aspect)-.35)<1e-9);
    assert.ok(Math.abs(frame.offsetY/visibleHeight-.35)<1e-9);
    assert.ok(Math.abs(frame.distance*scale-fitDistance(1,2,.2,aspect,30))<1e-9);
  }
});
test('invalid or empty dimensions cannot poison the camera with NaN', () => {
  for (const args of [[0,0,0,1,30],[1,1,1,0,30],[1,1,1,1,0],[NaN,1,1,1,30]]) {
    assert.throws(() => fitDistance(...args));
  }
});
