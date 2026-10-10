import {mkdirSync,readFileSync,writeFileSync,existsSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {glb} from './hair-source.fixture.mjs';
import {partFixturePair} from './hair-part.fixture.mjs';
import {extractHairPart,composeHairPart,inspectHairPart} from './hair-part.mjs';

// Generated synthetic test data, not a user's model. --check is read-only.
export function fixtures() {
  const [a,b]=partFixturePair();
  const base=glb(a),donor=glb(b),files=new Map([['base.vrm',base],['donor.vrm',donor]]);
  for(const [name,source] of [['original',base],['hair02',donor]]) {
    const p=extractHairPart(source,base),out=composeHairPart(base,p.json,p.bin);
    files.set(`${name}.json`,p.json);files.set(`${name}.bin`,p.bin);files.set(`${name}-composed.vrm`,out.bytes);
    files.set(`${name}-expected.json`,Buffer.from(JSON.stringify(inspectHairPart(p.json,p.bin),null,2)+'\n'));
  }
  return files;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const dir=resolve('../app/src/androidTest/assets/hair-parts'),check=process.argv[2]==='--check';
  if(!check)mkdirSync(dir,{recursive:true});
  for(const [name,bytes] of fixtures()) {
    const target=join(dir,name);
    if(check){if(!existsSync(target)||!readFileSync(target).equals(bytes))throw new Error(`Fixture differs: ${name}`);}
    else writeFileSync(target,bytes);
  }
  console.log(check?'Synthetic fixtures unchanged':'Synthetic fixtures generated');
}
