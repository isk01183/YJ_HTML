const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const assets=path.resolve(__dirname,'../app/src/main/assets');
const designs=require(path.join(assets,'circle-designs.js'));
const references=designs.list.filter(theme=>theme.group==='reference');
assert.equal(references.length,100,'Every supplied reference must be selectable separately');
const originals=[
 '5F4AB5FF172C62279FB2C1364B379F765349E5E649EA8217C8A04351C124E9BB',
 '7ABE5DEAEF1532B7584D8223FB78234638D6C1C8A07FA648F6B071545E9B6D12',
 'AB16B703E6EAB6507DB6BF16C6769F69C6F108C60651C1134F0D83BE73D444D0',
 '7B6C95992DB52B7EB48BEB33E0CFC028F5968ACE81A28FC7A2305B1F8E1B35A1',
 '367D3550914B92D73BCB2DA06F5881EC1FB63280E6CAB87DFE3A5C9D2CBC16EC'];
originals.forEach((hash,i)=>assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(assets,'artwork/sheet-'+(i+1)+'.jpg'))).digest('hex').toUpperCase(),hash,'The supplied image must not be regenerated or re-encoded'));
for(const theme of references){
 const [x,y,w,h]=theme.rect;
 assert.ok(x>=0&&y>=0&&w>100&&h>100&&x+w<=1280&&y+h<=(theme.sheet===3?1170:853),theme.id+' stays within source');
 const svg=designs.svg(theme.id,'check');
 assert.ok(svg.includes('href="artwork/sheet-'+theme.sheet+'.jpg"'),theme.id+' renders its original source');
 assert.ok(!/filter=|<filter|<animate|<polygon|<path/.test(svg),theme.id+' is not redrawn, tinted, or rotated');
 for(const language of ['ko','ja','en']) assert.ok(theme.names[language]);
}
assert.equal(designs.get('ref-1-06').id,'classic');
console.log('REFERENCE_ART_OK: 100 original regions; unchanged source bytes; bounded crops; no reinterpretation');
