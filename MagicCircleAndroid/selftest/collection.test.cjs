const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const catalogPath=path.join(__dirname,'../app/src/main/assets/collection-catalog.js');
assert.ok(fs.existsSync(catalogPath),'118 reference designs must ship in an actual app catalog');
const refs=require(catalogPath),old=require('../app/src/main/assets/circle-designs.js');
const expected=['C30','W05','F05','A30','B30','G05','E08','U04','R01'].flatMap(s=>Array.from({length:+s.slice(1)},(_,i)=>'ref-'+s[0]+String(i+1).padStart(2,'0')));
assert.deepEqual(refs.list.map(x=>x.id),expected);
assert.equal(new Set([...refs.list,...old.list].map(x=>x.id)).size,173);
for(const design of refs.list){
  for(const lang of ['ko','ja','en'])assert.ok(refs.get(design.id).names[lang]?.length>1,design.id+' '+lang);
  assert.equal(design.group,'reference');
  assert.match(design.thumb,/^collection\/thumbs\/[A-Z]\d{2}\.png$/);
}
for(const bad of ['ref-C00','ref-C31','ref-W06','ref-A01.svg','../C03','constructor'])assert.equal(refs.get(bad),null);
console.log('COLLECTION_CATALOG_OK: 118 references + 55 preserved designs, all 3 languages');
