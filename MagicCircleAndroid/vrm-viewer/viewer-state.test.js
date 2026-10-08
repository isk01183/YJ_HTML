import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shouldRender, normalizePlacement, relaxedArmAngle } from './viewer-state.js';

test('relaxed arms go down in both pre-facing humanoid coordinate systems',()=>{
  for(const version of ['0','1']) {
    const y=(version==='0'?-1:1)*Math.sin(relaxedArmAngle(version));
    assert.ok(y<-.8);
  }
});
test('document visibility cannot override the native host pause or disposal',()=>{
  assert.equal(shouldRender(false,true,true,false),false);
  assert.equal(shouldRender(true,false,true,false),false);
  assert.equal(shouldRender(true,true,false,false),false);
  assert.equal(shouldRender(true,true,true,true),false);
  assert.equal(shouldRender(true,true,true,false),true);
});
test('untrusted placement stays finite and bounded without changing blink choice',()=>{
  assert.deepEqual(normalizePlacement({x:NaN,y:Infinity,scale:NaN}),{x:0,y:0,scale:1,blink:true});
  assert.deepEqual(normalizePlacement({x:-4,y:4,scale:-3,blink:false}),{x:-.35,y:.35,scale:.5,blink:false});
  assert.equal(normalizePlacement({scale:8}).scale,1.5);
});
