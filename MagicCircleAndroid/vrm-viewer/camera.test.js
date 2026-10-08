import { test } from 'node:test';
import assert from 'node:assert/strict';
import { fitDistance } from './camera.js';

test('portrait and landscape framing both contain the complete model', () => {
  for (const aspect of [0.45, 1, 1.8]) {
    const distance = fitDistance(1.8, 2, 0.3, aspect, 30);
    const visibleHeight = 2 * (distance - 0.15) * Math.tan(Math.PI / 12);
    assert.ok(visibleHeight > 2, 'head and feet must stay in frame');
    assert.ok(visibleHeight * aspect > 1.8, 'T-pose hands must stay in frame');
  }
});
test('invalid or empty dimensions cannot poison the camera with NaN', () => {
  for (const args of [[0,0,0,1,30],[1,1,1,0,30],[1,1,1,1,0],[NaN,1,1,1,30]]) {
    assert.throws(() => fitDistance(...args));
  }
});
