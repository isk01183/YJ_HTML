/* Directly authored vectors from native references. No image tracing or raster assets. */
(function(root){
  'use strict';
  const ids=['A','B'].flatMap(g=>Array.from({length:30},(_,i)=>g+String(i+1).padStart(2,'0')))
    .concat(['E01','E02','E03','E04','E05','E06','E07','E08','F01','F02','F03','F04','F05','G01','G02','G03','G04','G05','U01','U02','U03','U04','W01','W02','W04','W05']);
  const n=x=>+x.toFixed(3), p=(d,a='')=>`<path d="${d}" ${/Z\s*$/i.test(d)?'data-closed="true"':'data-open-stroke="true"'} ${a}/>`,
    c=(x,y,r,a='')=>`<circle cx="${n(x)}" cy="${n(y)}" r="${n(r)}" ${a}/>`,
    g=(s,a='')=>`<g ${a}>${s}</g>`,r=(radius,a='')=>c(100,100,radius,a),
    at=(s,x,y,scale=1)=>g(s,`transform="translate(${n(x)} ${n(y)}) scale(${scale})"${scale!==1?` stroke-width="${n(.65/scale)}"`:''}`),
    turn=(s,a)=>g(s,`transform="rotate(${a} 100 100)"`),
    polar=(radius,a,x=100,y=100)=>[n(x+radius*Math.cos(a*Math.PI/180)),n(y+radius*Math.sin(a*Math.PI/180))],
    close=pts=>'M'+pts.map(v=>v.map(n).join(' ')).join('L')+'Z';
  function polygon(radius,count=6,step=1,angle=-90){return p(close(Array.from({length:count},(_,i)=>polar(radius,angle+i*step*360/count))));}
  function star(radius,inner,count=8,angle=-90){return p(close(Array.from({length:count*2},(_,i)=>polar(i%2?inner:radius,angle+i*180/count))));}
  const diamond=(x,y,size,a='')=>p(`M${x} ${y-size}L${x+size*.5} ${y}L${x} ${y+size}L${x-size*.5} ${y}Z`,a);
  function tick(radius,count,length=2){return Array.from({length:count},(_,i)=>p('M'+polar(radius,i*360/count).join(' ')+'L'+polar(radius+(i%5?length:length*1.7),i*360/count).join(' '))).join('');}
  // Low-resolution inscriptions are ornamental approximations, not deciphered text.
  const marks=['M-1.4-2V2M-1.4-2L1.4 0L-1.4 1','M-1.5 1L0-2L1.5 1M-1 0H1M0-2V2','M-1.5-1Q0-3 1.5-1L0 1L1.5 2M-1 1H1','M-1.5-2V1L0 2L1.5 1V-2','M-1.5 2L0-2L1.5 2M-1-1H1','M-1.5-1L0 1L1.5-1M0-2V2','M-1.5-2H1.5L-1.5 2H1.5','M-1.5 1V-1L0-2L1.5-1V1L0 2Z'];
  function script(radius,count=100,scale=.55,block=false){return Array.from({length:count},(_,i)=>{const a=i*360/count,[x,y]=polar(radius,a);return g(p(block?['M-1.5-1.5H1V1.5H-1.5Z','M-1-2H1.5V1H0V2H-1Z','M-1.5-1Q0-2.5 1.5-1V1Q0 2.5-1.5 1Z'][i%3]:marks[(i*7+Math.floor(i/8))%marks.length]),`transform="translate(${x} ${y}) rotate(${a+90}) scale(${scale})"`);}).join('');}
  function spark(x,y,size=3,a=''){return at(p('M0-1L.17-.17L1 0L.17.17L0 1L-.17.17L-1 0L-.17-.17Z',a),x,y,size);}
  const hex=radius=>polygon(radius,3)+polygon(radius,3,1,90),rings=(...rs)=>rs.map(x=>r(x)).join('');
  const mesh=(radius,angle=0)=>polygon(radius,4,1,angle)+polygon(radius,4,1,angle+45);
  function orbit(rx,ry,angle=0){return turn(`<ellipse cx="100" cy="100" rx="${rx}" ry="${ry}"/>`,angle);}
  function moons(x,y,size,flip=false){return g(p(`M${x+size*.6} ${y-size*.8}A${size} ${size} 0 1 0 ${x+size*.6} ${y+size*.8}A${size*.85} ${size*.85} 0 0 1 ${x+size*.6} ${y-size*.8}Z`,'fill="currentColor" stroke-width=".3"'),flip?`transform="rotate(180 ${x} ${y})"`:'');}
  function medallions(count=8,radius=88,size=4,kind='diamond'){
    return Array.from({length:count},(_,i)=>{const a=-90+i*360/count,[x,y]=polar(radius,a);return g(c(x,y,size,'fill="#030b12"')+(kind==='star'?at(star(4,1.5,5),x-100,y-100):diamond(x,y,size*.7)),`data-ornament="satellite-${i}"`);}).join('');
  }
  function crystal(x=100,y=100,size=30){return at(p('M0-1L.52 0L0 1L-.52 0Z M0-1V1M-.52 0L0 .3L.52 0M-.52 0L0-.3L.52 0','data-fixed="crystal"'),x,y,size);}
  function sun(radius=25,rays=16){return star(radius,radius*.63,rays)+rings(radius*.55,radius*.43)+tick(radius*.8,rays,2);}
  function heart(size=30){return at(p('M0 1C-.2.72-1 .24-.94-.3C-.88-.85-.22-1 0-.4C.22-1 .88-.85.94-.3C1 .24.2.72 0 1Z'),100,100,size);}
  function flame(size=35){return at(p('M0-1C.12-.55.68-.2.65.32C.62.8.3 1 0 1C-.65.96-.9.45-.61.02C-.65.4-.33.3-.29.08C-.13-.19.12-.45 0-1Z M.05-.46C.26-.14.42.11.34.46C.26.79-.18.9-.35.54C-.47.26-.15.02.05-.46Z'),100,100,size);}
  function drop(size=29){return at(p('M0-1C-.24-.36-.65.1-.57.49C-.46 1.2.47 1.2.58.49C.66.1.24-.36 0-1Z M-.15-.34C-.35.02-.5.44-.2.65'),100,100,size);}
  function snow(size=55){return Array.from({length:6},(_,i)=>turn(p(`M100 ${100-size}V100M100 ${100-size*.76}L${100-size*.17} ${100-size*.94}M100 ${100-size*.76}L${100+size*.17} ${100-size*.94}M100 ${100-size*.5}L${100-size*.2} ${100-size*.7}M100 ${100-size*.5}L${100+size*.2} ${100-size*.7}M100 ${100-size*.27}L${100-size*.23} ${100-size*.48}M100 ${100-size*.27}L${100+size*.23} ${100-size*.48}`),i*60)).join('');}
  function flower(x,y,size=8,color='currentColor',petals=5){return at(Array.from({length:petals},(_,i)=>g(p('M0 0C-1-.4-.72-1.3 0-1C.72-1.3 1-.4 0 0Z',`fill="${color}" stroke-width=".025"`),`transform="rotate(${i*360/petals})"`)).join('')+c(0,0,.13,'fill="#fff8d4"'),x,y,size);}
  function leaf(x,y,size=5,angle=0,color='currentColor'){return g(p('M0 0Q-1-.5 0-1.8Q1-.5 0 0Z',`fill="${color}" fill-opacity=".52" stroke-width=".06"`)+p('M0 0V-1.6','stroke-width=".06"'),`transform="translate(${x} ${y}) rotate(${angle}) scale(${size})"`);}
  function sprig(x,y,size=30){return at(p('M0 .9Q-.08 0 0-1M0 .5Q-.5.3-.6-.1M0 .1Q.4-.2.5-.6M0-.3Q-.3-.5-.4-.9')+leaf(-.33,.15,.2,-45)+leaf(.3,-.3,.2,40)+leaf(-.2,-.6,.18,-35)+leaf(0,-.9,.16),x,y,size);}
  function smallTree(){let s=p('M97 141Q104 122 98 104Q81 84 66 78M100 124Q106 99 125 82M100 109Q99 87 99 65M100 112Q82 102 62 100M101 115Q121 101 140 99','stroke-width="2"');
    for(let side of [-1,1])for(let i=0;i<5;i++){let x=100+side*(14+i*5),y=78+i*7;s+=p(`M100 122Q${100+side*8} ${y+8} ${x} ${y}Q${x+side*7} ${y-4} ${x+side*7} ${y-14}`);s+=leaf(x+side*7,y-10,4,side*27)+leaf(x,y,3,-side*30);}
    s+=Array.from({length:8},(_,i)=>p(`M100 136Q${83+i*5} 148 ${73+i*8} 148`)).join('');return s;}
  function snake(wings=false){return p('M100 29L104 43H101V170H98V43H95Z','fill="currentColor"')+p('M100 60C70 59 70 92 100 94C129 96 129 113 100 120C75 126 83 143 101 147C114 153 99 161 99 166','stroke-width="6"')+p('M96 60L86 57L96 54Z','fill="currentColor"')+(wings? p('M97 53Q83 59 53 47Q63 66 89 67L96 61M103 53Q117 59 147 47Q137 66 111 67L104 61M58 52L84 58M62 58L87 62M142 52L116 58M138 58L113 62','stroke-width="2"') : p('M70 49H130M76 46H124','stroke-width="4"'));}
  function spiral(arms=6,radius=57,turns=1.2,hole=5){return Array.from({length:arms},(_,a)=>{const points=Array.from({length:48},(_,i)=>{const t=i/47;return polar(hole+(radius-hole)*t,a*360/arms+t*360*turns);});return p('M'+points.map(q=>q.join(' ')).join('L'));}).join('');}
  function clockFace(){const numbers=['XII','I','II','III','IV','V','VI','VII','VIII','IX','X','XI'];return rings(68,59,36,31)+tick(61,60,2)+numbers.map((s,i)=>{const[x,y]=polar(75,i*30-90);return `<text x="${x}" y="${y+3}" text-anchor="middle" font-size="8" font-family="serif" fill="currentColor" stroke="none">${s}</text>`;}).join('')+p('M100 100L99 54M100 100L125 116','stroke-width="1.3"')+r(3,'fill="currentColor"');}
  function cube(radius=38){return polygon(radius,6)+p(close([polar(radius,-90),[100,100],polar(radius,30)]))+p('M100 100L'+polar(radius,150).join(' '))+polygon(radius*.53,6)+hex(radius*.55);}
  function eye(){return p('M61 100Q100 63 139 100Q100 137 61 100Z')+rings(17,11)+spark(100,100,7,'fill="currentColor"');}
  function roseCore(){return polygon(21,6)+polygon(19,4,1,-25)+polygon(17,4,1,10)+hex(16);}
  const paletteA=['#79bfff','#ff555a','#d8d9d4','#15191d','#ffb676','#ffe2a4','#d7dbd9','#12171a','#d8dcdd','#76dcff','#83d3ff','#f6a0cd','#8ae4ac','#eacc8b','#b786ff','#7fddff','#ffae5d','#79edaa','#f1d4a4','#d9edff','#b3a0ff','#ffacd9','#73b9ff','#e0f1ff','#ff7177','#7de8f5','#ffd58e','#aab8ff','#fb8fc9','#e0e9ee'];
  const paletteB=['#a2bdff','#ff3e48','#171b1c','#1e2325','#954116','#ffe4ac','#10181a','#eee5cb','#a4a8a3','#8eeaff','#9bceff','#c59bff','#8cecf9','#ffe3ac','#f4a8d8','#76b8ff','#baeca0','#ff9294','#c6a0ff','#b5dcff','#ffdba1','#ffafd6','#c3eaff','#8ee9dd','#ffb265','#8ecfff','#d2d9e2','#b895ff','#ffb5d2','#93e9ff'];
  function collection(id){
    const group=id[0],index=+id.slice(1),color=(group==='A'?paletteA:paletteB)[index-1];
    let outer=rings(92,89,80,77),runes=script(85,100),geometry='',core='',ornaments='',effects='',paper=false;
    if(group==='A')switch(index){
      case 1: geometry=hex(75)+mesh(61,12)+hex(44)+roseCore()+orbit(61,29,25)+orbit(61,29,-25);outer+=rings(69,65);ornaments=medallions(8,88,8)+medallions(6,46,3);runes+=script(72,88,.4);break;
      case 2:geometry=star(75,20,8)+star(61,26,8,0)+mesh(74)+rings(55,43,20);core=roseCore()+r(6);ornaments=medallions(8,84,6);break;
      case 3:geometry=hex(74)+r(52)+polygon(54,6);core=rings(25,23)+moons(100,100,18)+p('M100 77V123');ornaments=medallions(6,86,5);break;
      case 4:paper=true;geometry=hex(79)+r(68)+p('M22 100H178M100 21V177');core=snake();ornaments=medallions(6,84,6);break;
      case 5:geometry=hex(68)+star(87,11,4)+r(27);outer=rings(91,85,77);core=snake()+p('M97 16H103V38H97Z','fill="currentColor"');ornaments=medallions(4,86,4);break;
      case 6:geometry=star(74,34,8)+hex(67)+mesh(64)+star(37,14,8);core=sun(20,12)+spark(100,100,15,'fill="#fffce8"');ornaments=medallions(10,87,5);break;
      case 7:geometry=hex(77)+polygon(71,5,2)+rings(58,26);core=moons(100,100,21);ornaments=[0,90,180,270].map((a,i)=>{const[x,y]=polar(59,a);return c(x,y,18)+moons(x,y,12,i%2>0)+spark(x,y,6);}).join('');break;
      case 8:paper=true;geometry=hex(77)+r(62)+p('M22 100H178M100 21V177');core=snake(true);ornaments=medallions(6,83,5);break;
      case 9:geometry=hex(76)+sun(52,24);core=r(33,'fill="#03090e"')+p('M76 82A30 30 0 1 0 124 82C132 106 119 117 100 117C81 117 68 106 76 82Z','data-moon="central" fill="currentColor" stroke-width=".3"');ornaments=c(42,100,17)+moons(42,100,14)+c(158,100,17)+moons(158,100,14,true)+spark(163,100,5)+medallions(6,85,4);break;
      case 10:geometry=hex(77)+mesh(77)+hex(49)+mesh(49);core=star(31,9,6)+roseCore();ornaments=medallions(8,88,4);break;
      case 11:geometry=hex(72)+orbit(75,29,0)+orbit(75,29,60)+orbit(75,29,120)+r(32);core=cube(15);ornaments=medallions(8,86,4);break;
      case 12:geometry=hex(78)+hex(59)+polygon(72,4)+polygon(62,4)+polygon(56,4,1,0)+rings(54,24);core=hex(29)+r(2,'fill="currentColor"');ornaments=medallions(8,86,4);break;
      case 13:geometry=hex(76)+mesh(66)+hex(59)+r(48);core=smallTree();ornaments=medallions(8,87,4);break;
      case 14:outer=rings(92,87,82);geometry=clockFace()+hex(32)+tick(45,48,2);core=r(2);runes+=script(48,72,.6)+script(38,55,.4);ornaments=medallions(4,88,3);break;
      case 15:geometry=hex(78)+mesh(73)+mesh(61,22);core=spiral(8,53,.85,10);ornaments=medallions(8,87,3);break;
      case 16:geometry=hex(75)+mesh(68)+polygon(62,8,3)+r(43);core=drop(29);ornaments=medallions(4,87,6)+[0,90,180,270].map(a=>turn(at(drop(9),0,-64),a)).join('');break;
      case 17:geometry=hex(76)+mesh(72)+r(57)+p('M100 28C70 55 58 87 60 112C61 139 82 155 100 157C118 155 139 139 140 112C142 87 130 55 100 28Z');core=flame(32);ornaments=medallions(8,87,4);break;
      case 18:geometry=hex(75)+polygon(71,8)+r(56)+r(51);core=[0,120,240].map(a=>turn(p('M100 100C125 124 135 83 112 80C99 78 103 96 112 92C117 89 111 86 109 88'),a)).join('');ornaments=medallions(8,87,4);break;
      case 19:geometry=hex(76)+mesh(68)+rings(57,44);core=crystal(100,100,39);ornaments=medallions(4,87,5)+spark(33,100,7)+spark(167,100,7);break;
      case 20:geometry=hex(77)+mesh(72)+polygon(69,8,3);core=sun(47,16)+spark(100,100,13,'fill="#fff"');ornaments=medallions(8,87,4);break;
      case 21:geometry=hex(75)+mesh(68)+sun(57,24);core=r(35,'fill="#030711"')+moons(100,100,31)+spark(121,100,6);ornaments=medallions(8,87,4);break;
      case 22:geometry=hex(75)+star(83,28,4)+mesh(67)+r(47);core=heart(36)+heart(29);ornaments=medallions(4,87,6)+[0,90,180,270].map(a=>turn(p('M100 40Q78 66 100 81Q122 66 100 40Z'),a)).join('');break;
      case 23:geometry=mesh(76)+hex(62)+mesh(59,20);core=cube(44)+cube(21);ornaments=medallions(4,87,4);break;
      case 24:geometry=hex(78)+star(90,21,4)+mesh(69)+hex(53);core=star(41,15,12)+hex(30)+roseCore();ornaments=medallions(8,87,5);break;
      case 25:geometry=hex(77)+mesh(69)+star(66,30,8);core=star(40,12,8)+hex(25)+spark(100,100,10,'fill="currentColor"');ornaments=medallions(8,87,4);break;
      case 26:geometry=hex(76)+mesh(71)+r(53);core=r(44)+p('M100 56C67 56 70 100 100 100C130 100 132 144 100 144C156 144 156 56 100 56Z','fill="currentColor" fill-opacity=".28"')+c(100,78,6,'fill="currentColor"')+c(100,122,6,'fill="currentColor"');ornaments=medallions(4,87,4);break;
      case 27:geometry=hex(74)+mesh(64)+orbit(72,28,0)+orbit(65,26,60)+orbit(65,26,120);core=rings(17,14)+spark(100,100,13,'fill="#fffbdc"');ornaments=medallions(4,87,6)+[0,90,180,270].map(a=>{const[x,y]=polar(53,a);return c(x,y,5)+spark(x,y,4,'fill="currentColor"');}).join('');break;
      case 28:geometry=hex(77)+mesh(69)+polygon(69,8,3);core=star(79,9,4)+star(44,7,8)+r(12);ornaments=medallions(4,87,6);break;
      case 29:geometry=hex(72)+mesh(65)+orbit(75,32)+orbit(72,22,18)+orbit(72,22,-18);core=eye()+r(9)+spark(100,100,7,'fill="currentColor"');ornaments=medallions(4,87,6);break;
      case 30:geometry=hex(76)+mesh(71)+hex(53)+polygon(53,8,3);core=roseCore()+crystal(100,105,39)+p('M81 51L77 35L88 43L94 32L101 43L108 32L114 43L125 36L120 51Z','fill="currentColor" fill-opacity=".7"');ornaments=medallions(8,88,4);break;
    }
    if(group==='B')switch(index){
      case 1:geometry=mesh(68,13)+hex(52)+rings(45,28)+roseCore();outer+=rings(71,66,61);runes+=script(70,88,.4)+script(53,76,.4);ornaments=Array.from({length:9},(_,i)=>{const[x,y]=polar(77,i*40-72);return c(x,y,10+i%3*3)+c(x,y,7)+at(hex(7),x-100,y-100);}).join('');break;
      case 2:geometry=hex(77)+mesh(74)+polygon(73,5,2)+star(46,21,12)+rings(51,32,21);core=rings(10,4);ornaments=medallions(10,75,9)+medallions(12,89,4);runes+=script(62,85,.5);break;
      case 3:paper=true;geometry=hex(77)+rings(42,35,30)+p('M100 71V130M74 85L126 115M74 115L126 85');core=r(27);runes+=script(39,72,.45);ornaments=at(r(8)+r(4),0,-48)+c(100,180,11,'fill="#fcfbf3"')+p('M94 185V176Q100 165 106 176V185H111M89 185H98V176Q100 171 102 176V185H111','stroke-width="1.5"');break;
      case 4:paper=true;geometry=polygon(77,3)+rings(63,37)+p('M100 24V100L164 139M100 100L36 139');core=rings(13,10);ornaments=c(100,27,12)+c(100,27,8)+medallions(4,86,3);break;
      case 5:paper=true;geometry=hex(75)+rings(71,65,46);core=snake()+p('M53 87H145','stroke-width="7"');ornaments=medallions(4,87,12);break;
      case 6:geometry=hex(75)+mesh(64)+star(94,19,4)+star(51,18,8);core=rings(28,23,10)+roseCore();ornaments=medallions(4,86,3);break;
      case 7:paper=true;geometry=hex(76)+r(62);core=snake(true);ornaments=medallions(6,80,4);break;
      case 8:geometry=hex(75)+mesh(67)+sun(44,16);core=rings(20,15)+spark(100,100,9);ornaments=[0,90,180,270].map((a,i)=>{const[x,y]=polar(73,a);return c(x,y,18,'fill="#11151c"')+moons(x,y,15,!!(i%2))+spark(x,y,6);}).join('');break;
      case 9:geometry=hex(76)+polygon(70,7,2);core=r(26);ornaments=[[60,45,22],[137,63,28],[150,105,22],[112,153,22],[56,137,20],[28,101,15],[153,146,15]].map(([x,y,s],i)=>c(x,y,s)+c(x+3,y+2,s-4)+moons(x,y,s-2,!!(i%2))+at(sun(s*.55,8),x-100,y-100)).join('');break;
      case 10:geometry=hex(79)+mesh(73)+hex(55)+hex(41)+polygon(44,6);core=cube(21)+r(5);ornaments=medallions(4,89,3);effects=r(80,'fill="currentColor" opacity=".12"');break;
      case 11:geometry=hex(76)+mesh(70)+hex(57)+mesh(46)+polygon(36,8,3);core=roseCore()+r(8);ornaments=medallions(12,87,5)+medallions(4,63,5);runes+=script(54,60,.3);break;
      case 12:outer=rings(87,71);runes='';geometry=hex(77)+orbit(82,30,0)+orbit(82,30,60)+orbit(82,30,120)+rings(38,23);core=r(2,'fill="currentColor"');ornaments=Array.from({length:9},(_,i)=>{const[x,y]=polar(i%3?79:92,i*40-90);return c(x,y,4,'fill="currentColor"');}).join('');break;
      case 13:geometry=hex(86)+hex(65)+hex(49)+mesh(44)+rings(66,32);core=roseCore()+spark(100,100,7,'fill="#efffff"');ornaments=[0,60,120,180,240,300].map(a=>turn(diamond(100,16,9),a)).join('');break;
      case 14:geometry=hex(76)+mesh(67)+star(85,17,4)+star(39,17,12);core=rings(27,22,16)+roseCore();ornaments=medallions(4,87,4);break;
      case 15:geometry=hex(75)+star(92,19,4)+star(61,27,8)+mesh(56)+rings(41,24);core=star(26,12,8)+r(9);ornaments=[0,90,180,270].map(a=>turn(diamond(100,11,9)+diamond(100,30,8),a)).join('');break;
      case 16:geometry=rings(71,63,42,37,28)+p('M100 20V70M100 130V180M20 100H70M130 100H180');core=cube(19)+r(9);ornaments=medallions(4,88,5)+medallions(4,65,4)+medallions(8,39,3);runes+=script(32,56,.4)+tick(58,40,4);break;
      case 17:geometry=hex(74)+mesh(64)+rings(51,46)+script(48,70,.45);core=sprig(100,102,39)+p('M100 142Q76 120 100 65Q124 120 100 142Z');ornaments=medallions(4,88,5);break;
      case 18:geometry=hex(73)+star(61,28,8)+star(37,14,8);core=r(10)+spark(100,100,5);ornaments=c(100,27,18,'fill="#120508"')+c(27,100,18,'fill="#120508"')+c(173,100,18,'fill="#120508"')+c(100,173,18,'fill="#120508"')+
        p('M86 19A16 16 0 1 0 114 19C117 33 111 38 100 38C89 38 83 33 86 19Z','data-moon="top" fill="currentColor"')+
        p('M27 84A16 16 0 0 1 27 116C34 109 34 91 27 84Z','data-moon="left" fill="currentColor"')+
        p('M173 84A16 16 0 0 0 173 116C166 108 166 92 173 84Z','data-moon="right" fill="currentColor"')+
        p('M84 173A16 16 0 1 1 116 173Q100 182 84 173Z','data-moon="bottom" fill="currentColor"');break;
      case 19:geometry=polygon(88,3)+hex(65)+mesh(57)+rings(69,42);core=eye();ornaments=medallions(3,87,6)+[0,120,240].map(a=>turn(diamond(100,11,10),a)).join('');break;
      case 20:geometry=mesh(83)+mesh(66)+mesh(49)+hex(53)+rings(67,31);core=cube(27)+cube(14);ornaments=medallions(8,87,5)+p('M100 11V189M11 100H189','opacity=".5"');break;
      case 21:geometry=spiral(8,72,.72,6)+orbit(65,56,20);core=spiral(4,39,1.1,3)+spark(100,100,5,'fill="#fff8d4"');ornaments=medallions(4,88,3);effects=spiral(5,64,.8,11);break;
      case 22:geometry=hex(76)+mesh(68)+star(76,29,8)+r(50);core=heart(35)+heart(29)+spark(100,101,4,'fill="currentColor"');ornaments=medallions(4,87,5)+spark(14,100,7)+spark(186,100,7);break;
      case 23:geometry=hex(70)+mesh(58);core=snow(62)+crystal(100,100,16);ornaments=Array.from({length:8},(_,i)=>turn(diamond(100,14,9),i*45)).join('');break;
      case 24:geometry=hex(73)+mesh(67)+r(48);core=g(
        p('M100 121C86 111 88 99 94 86C98 77 100 67 100 55C100 67 102 77 106 86C112 99 114 111 100 121Z','data-lotus-petal="center" fill="currentColor" fill-opacity=".08"')+
        p('M100 121C78 115 78 101 81 89C77 92 74 98 74 105C65 95 69 79 75 74C63 84 61 98 66 111C71 123 88 125 100 126Z M100 121C122 115 122 101 119 89C123 92 126 98 126 105C135 95 131 79 125 74C137 84 139 98 134 111C129 123 112 125 100 126Z')+
        p('M100 126C82 123 71 127 62 116C69 132 83 134 100 131C117 134 131 132 138 116C129 127 118 123 100 126Z M100 121L95 133L100 141L105 133Z')+
        p('M100 81C93 95 93 108 100 118C107 108 107 95 100 81Z M100 113V140'),
        'data-lotus="upright"');ornaments=medallions(4,87,5);break;
      case 25:geometry=hex(77)+mesh(68)+polygon(55,8,3)+rings(44,29,23);core=r(13)+p('M100 70V130M70 100H130');ornaments=[0,90,180,270].map(a=>turn(at(flame(11),0,-86),a)).join('');break;
      case 26:geometry=hex(72)+rings(69,63);core=spiral(7,61,.82,4)+spark(100,100,4,'fill="#e4ffff"');ornaments=medallions(4,87,7)+spark(15,100,8)+snow(5);break;
      case 27:geometry=rings(66,34,26,15)+hex(37)+p('M100 20V180M25 100H175');core=star(20,8,8);ornaments=Array.from({length:10},(_,i)=>{const[x,y]=polar(72,i*36-90);return c(x,y,i%5===0?19:11,'fill="#060912"')+moons(x,y,i%5===0?17:10,i>5);}).join('');break;
      case 28:geometry=rings(73,68)+spiral(9,66,1.13,21);core=r(23,'fill="#040211"')+p('M76 100A24 24 0 0 1 124 100','stroke-width="1.5"');ornaments=medallions(4,88,3);break;
      case 29:geometry=hex(70)+rings(62,37)+polygon(55,10,3);core=Array.from({length:5},(_,i)=>turn(p('M100 100C92 87 87 74 94 64L100 69L106 64C113 74 108 87 100 100Z','fill="#ffc6dd" stroke-width=".4"')+p('M100 100V79M100 100L95 83M100 100L105 83','stroke="#8a3455" stroke-width=".7"')+c(100,79,1,'fill="#8a3455"'),i*72)).join('');ornaments=[0,90,180,270].map(a=>{const[x,y]=polar(85,a);return flower(x,y,11,'#ffbed5');}).join('');break;
      case 30:geometry=hex(74)+mesh(66)+orbit(76,26)+orbit(67,30,60)+orbit(67,30,120)+rings(44,27);core=roseCore()+r(9);ornaments=medallions(8,85,5)+medallions(6,60,7);effects=r(88,`stroke="url(#${id}-spectrum)" stroke-width="3"`);break;
    }
    return {color,outer,runes,geometry,core,ornaments,effects,paper};
  }
  function early(id){
    const k=+id.slice(1),phone=id[0]==='U',colors=phone?['#d6a69d','#c5dcf1','#f1a2bf','#c3e9f6']:['#d68e9e','#7bb5d4','#e9d2a4','#bdc9d5','#b890e5','#6ed7ec','#f9dbaa','#ad9ba8'],color=colors[k-1];
    let outer=rings(94,89,82,78),runes=script(86,100,.72,true)+tick(76,100,1.3),geometry=polygon(74,8,3),core=roseCore(),ornaments='',effects='';
    if(!phone&&(k===3||k===7)){geometry=hex(70)+orbit(75,28)+orbit(75,28,60)+orbit(75,28,120);ornaments=[0,60,120,180,240,300].map(a=>{const[x,y]=polar(72,a);return c(x,y,4,'fill="currentColor"');}).join('');core=roseCore()+hex(28);}
    if(!phone&&k===5){geometry=hex(74)+orbit(74,28,30)+orbit(74,28,90)+orbit(74,28,150)+mesh(91,0);core=mesh(23)+roseCore();ornaments=medallions(4,73,3);}
    if(!phone&&k===6){geometry=hex(70)+rings(60,55,47)+orbit(63,48,30)+orbit(63,48,-30);ornaments=medallions(6,61,4);effects=r(57,'stroke-width="5" opacity=".14"');}
    if(!phone&&k===8){outer=rings(85,76,70);runes=script(80,92,.55,true)+tick(68,94,1);geometry=polygon(65,8,3);}
    if(phone){geometry=polygon(72,8,3)+orbit(70,22,k===3?45:15)+orbit(70,22,k===3?-45:75);ornaments=medallions(4,90,3);core=roseCore()+spark(100,100,9,'fill="currentColor"');}
    if(!phone&&k===7){geometry+=polygon(74,8,3);core+=c(100,100,20,`fill="url(#${id}-spark)" stroke="none"`);}
    if(phone&&k===2){outer+=rings(70,66,59);runes+=script(63,90,.45);geometry+=orbit(72,27,130);effects=orbit(73,27,10)+orbit(73,27,70);}
    if(phone&&k===4){outer+=rings(72,66);geometry=hex(64)+mesh(58)+orbit(70,25,30)+orbit(70,25,150);core=rings(25,22);effects=orbit(90,55,45)+orbit(90,55,-45);}
    return {color,outer,runes,geometry,core,ornaments,effects,paper:false};
  }
  function sixfold(id){
    const k=+id.slice(1),alt=id[0]==='G',colors=alt?['#9fe6ff','#ffdfa1','#c79aff','#caecb1','#b6caff']:['#85daff','#ffebb5','#c291ff','#b3f1cb','#92bcff'],color=colors[k-1];
    const outer=rings(84,81,73,69),runes=script(77,76,.85,true)+script(66,60,.73,true);
    let geometry='',core=r(10)+diamond(100,100,7)+diamond(100,100,3),ornaments='',effects='';
    for(let i=0;i<6;i++){
      const petal=p('M111 100C124 88 137 80 149 83C159 85 165 94 171 100C164 108 156 117 148 117C134 116 121 107 111 100Z',`fill="url(#${id}-petal)" data-petal="${i}"`)+p('M114 100C132 84 146 78 158 89M114 100C131 115 144 120 157 110','stroke-width=".5"');
      // Alternate empty cores: right, upper-left, lower-left; diamond cores: their opposite partners.
      const diamondCore=i%2===1;
      const node=c(149,100,14.6)+c(149,100,12.8)+c(diamondCore?148:152,diamondCore?102:97,9.5)+c(diamondCore?148:152,diamondCore?102:97,8.3)+
        (diamondCore?diamond(148,102,7)+diamond(148,102,4)+diamond(148,102,1.5):'');
      geometry+=turn(petal,i*60)+turn(p('M98 88H102L102.5 68H97.5Z M95 60H105L110 47Q100 44 90 47Z'),i*60);
      ornaments+=turn(g(node,`data-core-kind="${diamondCore?'diamond':'empty'}"`),i*60);
    }
    if(k===1){effects=Array.from({length:14},(_,i)=>{const a=i*137.5,[x,y]=polar(88+i%4*3,a);return alt?at(snow(6),x-100,y-100):turn(p('M100 10L103 3L101 1L107-6','stroke-width=".7"'),a);}).join('');ornaments+=Array.from({length:8},(_,i)=>{const[x,y]=polar(89+i%2*6,i*45+15);return g(crystal(x,y,9),`stroke="#aeefff"`);}).join('');}
    if(k===2){effects=orbit(98,28,20)+orbit(98,28,-20)+orbit(97,35,70);ornaments+=Array.from({length:12},(_,i)=>{const[x,y]=polar(92,i*30);return alt?spark(x,y,i%3?2:6,'fill="#fff2c5"'):leaf(x,y,5,i*30,'#fff2c5');}).join('');}
    if(k===3){effects=spiral(7,96,.44,78);ornaments+=Array.from({length:12},(_,i)=>{const[x,y]=polar(90+i%3*3,i*30+10);return crystal(x,y,7+i%3);}).join('');}
    if(k===4){effects=orbit(93,81,20)+orbit(97,87,-15);ornaments+=Array.from({length:25},(_,i)=>{const[x,y]=polar(90+i%3*3,i*137.5);return leaf(x,y,3.5,i*47,alt?'#ceefa0':'#9fffd3')+(i%3===0?flower(x+3,y,4,'#ffffd0'): '');}).join('');}
    if(k===5){effects=orbit(100,43,32)+orbit(100,42,-28)+orbit(97,63,70);ornaments+=[[17,130,7],[175,45,6],[155,165,10]].map(([x,y,s])=>c(x,y,s,`fill="url(#${id}-planet)"`)+moons(x,y,s)).join('');ornaments+=p('M9 45L36 18L78 9L129 15L174 43L190 95M15 155L45 181L97 190L159 176','opacity=".6"');}
    return {color,outer,runes,geometry,core,ornaments,effects,paper:false};
  }
  function drawLayer(id,name,body,color,width=.55,fixed=false){
    return g(g(body.replace(/\sdata-[\w-]+="[^"]*"/g,''),`aria-hidden="true" filter="url(#${id}-soft)" opacity=".6"`)+g(body,`data-outline="${name}"`),`data-layer="${name}"${fixed?' data-fixed="true"':''} fill="none" color="${color}" stroke="${color}" stroke-width="${width}" stroke-linejoin="round" stroke-linecap="round"`);
  }
  function defs(id,color){return `<defs><filter id="${id}-soft" x="-50%" y="-50%" width="200%" height="200%" color-interpolation-filters="sRGB"><feGaussianBlur stdDeviation=".65"/></filter><radialGradient id="${id}-wash"><stop stop-color="${color}" stop-opacity=".13"/><stop offset=".7" stop-color="${color}" stop-opacity=".06"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></radialGradient><radialGradient id="${id}-spark"><stop stop-color="#ffffff"/><stop offset=".12" stop-color="#f1fcff"/><stop offset=".35" stop-color="${color}" stop-opacity=".5"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></radialGradient><linearGradient id="${id}-petal" x2=".6" y2="1"><stop stop-color="${color}" stop-opacity=".07"/><stop offset=".75" stop-color="${color}" stop-opacity=".5"/><stop offset="1" stop-color="#ffffff" stop-opacity=".78"/></linearGradient><radialGradient id="${id}-planet" cx=".3" cy=".3"><stop stop-color="#e0f9ff"/><stop offset=".45" stop-color="${color}"/><stop offset="1" stop-color="#07162a"/></radialGradient><linearGradient id="${id}-spectrum"><stop stop-color="#da82f5"/><stop offset=".3" stop-color="#85acff"/><stop offset=".6" stop-color="#79e9d5"/><stop offset="1" stop-color="#efee8e"/></linearGradient></defs>`;}
  function particles(id,count=80,w=200,h=200){const fraction=x=>x-Math.floor(x);return Array.from({length:count},(_,i)=>{const x=n(fraction(Math.sin(i*12.9898+17)*43758.5453)*w),y=n(fraction(Math.sin(i*39.3467+51)*17428.322)*h),size=i%13===0?1.3:.12+(i%5)*.075;return i%13===0?spark(x,y,size,'fill="currentColor" stroke="none"'):c(x,y,size,'fill="currentColor" stroke="none" opacity=".6"');}).join('');}
  function svg(id){
    if(!ids.includes(id))throw new RangeError('Unknown direct drawing: '+id);
    if(id[0]==='W')return worldTree(id);
    const s=['A','B'].includes(id[0])?collection(id):['E','U'].includes(id[0])?early(id):sixfold(id);
    const dusty=!s.paper&&id!=='E08',title=id+' · 직접 벡터 재구성';
    if(s.paper)s.ornaments=s.ornaments.replaceAll('fill="#030b12"','fill="#fafaf1"');
    if(['A01','A06','A10','A20','A24','A25','A27','A28','A29','B01','B10','B11','B13','B14','B19','B20','B21','B22','B26','B30','E03','U02','U03'].includes(id))s.core+=c(100,100,id==='E03'?9:15,`fill="url(#${id}-spark)" stroke="none" opacity=".72"`);
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" preserveAspectRatio="xMidYMid meet" role="img" data-direct-circle="${id}" aria-label="${title}"><title>${title}</title>${defs(id,s.color)}${p('M0 0H200V200H0Z','fill="#02080f"')}${s.paper?r(94,'fill="#fafaf1"'):r(99,`fill="url(#${id}-wash)"`)}${drawLayer(id,'outer-ring',s.outer,s.color,s.paper?.65:.5)}${drawLayer(id,'rune-ring',s.runes,s.color,.35)}${drawLayer(id,'geometry',s.geometry,s.color,.5,true)}${drawLayer(id,'center-core',s.core,s.color,.8,true)}${drawLayer(id,'ornaments',s.ornaments,s.color,.7,true)}${drawLayer(id,'atmosphere',s.effects,s.color,.5)}${drawLayer(id,'particles',dusty?particles(id,id[0]==='F'||id[0]==='G'?170:70):'',s.color,.2)}</svg>`;
  }
  function butterfly(x,y,size=5,color='#e0fff4',angle=0){return g(p('M0 0C-1-1.6-1.7-.9-.8.1C-1.8.7-1 1.3 0 .2C1 1.3 1.8.7.8.1C1.7-.9 1-1.6 0 0Z',`fill="${color}" fill-opacity=".42" stroke-width=".09"`)+p('M0-.3V.6M0-.2L-.2-.55M0-.2L.2-.55','stroke-width=".07"'),`transform="translate(${x} ${y}) rotate(${angle}) scale(${size})"`);}
  function crystalLeaf(x,y,size,angle,color){return g(p('M0-1L.42-.15L.18.6L0 1L-.4.05Z',`fill="${color}" fill-opacity=".55" stroke-width=".05"`)+p('M0-1V1M-.4.05L0-.2L.42-.15M-.4.05L0 .5L.18.6','stroke-width=".04"'),`transform="translate(${x} ${y}) rotate(${angle}) scale(${size})"`);}
  function worldTree(id){
    const moon=id==='W02',season=id==='W04',gem=id==='W05',color=moon?'#c3ceff':gem?'#f5e8bd':'#c4fff0';
    const cy=moon?179:165,rad=moon?91:92;
    const local=(s)=>g(s,`transform="translate(0 ${cy-100})"`);
    const leafColor=(x,y)=>season?(x<100?(y<172?'#ffc1df':'#d4f7ab'):(y<170?'#cbeaa1':'#a9d6ff')):moon?['#c9e1ff','#e9c4ee','#b9c9ff'][Math.floor(x+y)%3]:gem?'#a4f8e9':'#caffdc';
    let outer=local(rings(rad,rad-2,rad-9,rad-12,rad-20,rad-22)+tick(rad-16,144,.6));
    let runes=local(script(rad-6,72,.62,true)),geometry='',core='',ornaments='',atmosphere='',lights='';
    // Major contours are individually authored for each native tree. The leaf sites
    // follow unequal branch tips, rather than repeated offsets along a radial fan.
    const trees={
      W01:{
        trunk:'M92 216C100 200 99 185 91 169C99 175 101 181 101 187C105 173 105 156 99 142C109 155 109 172 105 190C103 202 107 211 114 219L102 213L99 204L96 214Z',
        crown:[
          'M99 193C94 175 72 167 49 169C39 170 34 163 33 155',
          'M94 181C79 165 62 158 52 144C48 138 45 135 38 135',
          'M84 174C70 177 60 181 46 184C39 186 35 183 31 179',
          'M76 168C68 153 70 144 60 132C55 126 52 122 54 116',
          'M100 185C98 166 86 159 84 144C82 133 73 126 72 118',
          'M99 174C102 157 100 144 97 129C95 121 98 114 100 110',
          'M101 161C107 145 115 139 114 128C113 119 117 115 122 113',
          'M103 184C112 165 131 161 145 153C154 148 158 139 157 132',
          'M111 174C120 159 126 145 136 138C141 134 143 128 143 123',
          'M105 191C121 177 137 177 156 179C164 180 169 177 171 170',
          'M133 176C147 168 159 166 163 153',
          'M51 168Q45 154 37 152M65 160Q60 150 58 141M80 160Q84 150 77 139M89 158Q95 149 89 135M119 164Q115 151 122 143M145 157Q142 146 150 137M156 178Q159 186 170 187'
        ],
        roots:[
          'M98 204C90 219 72 213 57 223C51 227 42 230 33 228',
          'M96 209C83 214 78 232 61 233L47 233M79 223Q69 226 65 239',
          'M99 210C91 223 95 235 82 244M93 228Q84 234 73 235',
          'M100 210C101 226 103 236 100 247M100 230Q92 239 96 246',
          'M102 207C111 222 119 213 137 224C151 233 157 227 168 232',
          'M103 213C110 227 122 231 135 237M115 226Q112 237 121 244',
          'M104 216C108 235 108 241 114 246M137 224Q140 234 155 238'
        ],
        sites:[[33,155,-65],[38,135,-50],[46,184,-85],[31,179,-90],[52,144,-30],[54,116,12],[60,132,-45],[72,118,-20],[84,144,30],[77,139,-30],[89,135,-15],[97,129,20],[100,110,0],[114,128,-20],[122,113,30],[136,138,30],[143,123,15],[157,132,25],[150,137,50],[145,153,65],[163,153,40],[171,170,75],[156,179,75],[170,187,100],[37,152,-65],[58,141,-15],[122,143,10],[67,151,-30],[95,149,20],[130,165,50],[48,172,-70],[110,138,15]]},
      W02:{
        trunk:'M94 220C104 203 98 193 97 184C98 171 92 154 84 143C96 152 101 167 103 178C107 166 117 154 122 149C111 170 104 185 105 199C106 210 113 219 119 224L104 216L99 209L97 221Z',
        crown:[
          'M99 195C89 179 71 178 55 179C43 180 36 172 34 165',
          'M92 181C77 169 65 167 55 155C48 147 37 148 31 150',
          'M85 175C79 160 74 149 62 142C55 137 50 131 52 124',
          'M97 173C89 157 79 145 77 134C76 124 65 120 63 113',
          'M101 171C100 151 90 139 92 127C94 119 91 114 89 109',
          'M99 151C107 141 112 128 109 117M106 137C119 130 124 121 128 115',
          'M105 182C115 165 132 154 142 142C151 132 158 135 161 127',
          'M118 166C126 151 126 140 133 131',
          'M105 191C123 174 142 178 156 168C164 163 168 156 171 147',
          'M127 178C134 164 146 160 149 149',
          'M108 197C121 188 134 195 147 188C154 184 158 183 166 184',
          'M54 179Q49 166 40 163M67 170Q63 155 64 150M76 155Q67 146 69 137M89 148Q96 138 86 126M110 165Q109 151 118 146M141 161Q151 163 160 153M154 169Q166 172 171 169'
        ],
        roots:[
          'M98 210C83 222 71 217 59 225C47 234 40 230 35 222C41 224 46 222 48 217',
          'M95 217C80 224 82 239 70 241C58 242 51 232 59 228C66 225 71 232 66 235',
          'M97 216C88 235 81 242 88 251C98 258 107 243 99 238C93 236 90 244 94 247',
          'M100 213C104 227 116 230 112 242C108 252 98 251 100 241',
          'M104 215C119 220 126 239 137 238C151 237 150 222 140 223C132 223 131 234 140 233',
          'M105 213C123 224 141 211 157 222C166 229 159 236 151 232',
          'M93 223C87 227 75 227 73 218C71 210 81 206 83 214M113 225C123 214 122 206 130 203'
        ],
        sites:[[34,165,-60],[31,150,-80],[52,124,-15],[55,155,-50],[62,142,-35],[63,113,-15],[77,134,10],[89,109,-12],[92,127,18],[109,117,5],[128,115,35],[133,131,12],[142,142,35],[161,127,60],[171,147,70],[149,149,15],[160,153,65],[156,168,50],[171,169,85],[166,184,100],[147,188,45],[40,163,-50],[64,150,0],[69,137,-20],[86,126,-10],[118,146,10],[55,179,-70],[80,163,-30],[102,137,0],[133,179,65],[46,174,-65],[144,173,40]]},
      W04:{
        trunk:'M88 216C101 203 102 189 98 178C94 168 84 160 80 151C92 160 100 164 103 176C111 159 115 153 126 147C115 160 111 173 107 186C104 198 110 208 120 215L106 211L100 204L96 214Z',
        crown:[
          'M99 185C88 168 76 165 58 162C47 159 43 153 38 143',
          'M91 172C78 158 67 156 60 146C53 137 44 135 38 138',
          'M80 165C70 163 56 171 44 169C36 168 33 163 30 158',
          'M96 174C93 158 81 148 77 137C74 128 68 125 66 118',
          'M100 171C102 155 97 145 95 134C93 126 96 118 99 114',
          'M103 165C115 150 112 137 118 124C121 116 125 116 131 119',
          'M110 159C122 154 127 148 136 144C146 139 147 130 149 124',
          'M104 182C121 167 137 168 149 161C156 157 162 148 163 138',
          'M110 177C127 178 145 180 158 173C164 170 169 164 170 157',
          'M65 160Q65 147 54 143M82 150Q83 138 74 133M96 147Q89 137 87 127M116 143Q129 142 128 131M145 164Q137 151 144 144M152 175Q157 183 166 181M48 165Q43 155 35 154'
        ],
        roots:[
          'M99 199C83 217 72 212 58 210C44 208 39 199 40 194C31 205 43 223 58 219C68 216 71 209 65 204',
          'M96 207C84 218 73 221 67 231C63 239 70 244 77 239C82 235 78 229 73 231',
          'M98 207C89 223 82 225 89 237C98 249 99 254 100 261C102 248 115 243 110 232C106 221 97 224 97 233',
          'M101 202C112 215 129 213 141 205C150 198 156 190 159 184C163 199 159 214 146 216C132 219 128 229 137 233C148 237 154 222 146 221',
          'M105 209C117 215 123 224 122 233C121 245 110 245 111 237',
          'M93 214C89 228 94 234 99 239M109 217C114 223 108 230 105 238M64 216Q50 221 47 229M140 212Q158 214 166 205'
        ],
        sites:[[38,143,-60],[38,138,-70],[30,158,-85],[44,169,-65],[60,146,-30],[66,118,-25],[77,137,-20],[74,133,-40],[87,127,-15],[99,114,0],[95,134,20],[118,124,5],[131,119,60],[136,144,50],[149,124,35],[163,138,55],[170,157,65],[158,173,70],[166,181,110],[54,143,-45],[65,151,-25],[82,141,-10],[108,153,10],[128,131,25],[144,144,35],[152,163,50],[35,154,-75],[74,164,-20],[120,169,40],[140,176,75]]},
      W05:{
        trunk:'M90 216C94 201 107 190 104 177C102 168 94 162 91 150C99 158 109 164 109 177C110 192 98 205 96 216L101 224L95 224Z M108 215C105 204 96 194 98 182C100 170 112 163 115 152C116 165 105 175 103 184C101 195 115 207 113 217L106 227L103 225Z',
        crown:[
          'M105 181C94 166 79 160 70 150C66 144 65 137 62 130',
          'M101 176C94 159 97 146 89 133C84 126 82 120 84 115',
          'M103 163C105 148 108 136 103 122L101 111',
          'M105 169C115 155 122 149 124 137C126 128 130 122 137 119',
          'M100 186C86 173 72 174 59 163C51 157 47 150 42 145',
          'M105 181C121 169 138 167 149 155C155 150 159 144 159 138',
          'M101 190C88 179 69 182 57 177C49 172 40 170 35 169',
          'M106 190C121 180 139 186 153 176C158 171 166 169 170 169',
          'M73 174C66 163 67 154 61 148M86 169C82 155 85 145 77 137M99 151Q94 142 96 132M115 156Q117 140 113 133M136 166Q136 151 144 142M150 178Q145 168 151 163M58 164Q55 154 49 152'
        ],
        roots:[
          'M99 203C91 215 76 216 67 223C57 233 68 244 84 241C95 239 94 226 84 227C77 228 78 236 85 234',
          'M103 203C115 214 130 215 140 225C149 236 136 246 123 242C112 239 113 229 122 228C130 228 132 238 124 238',
          'M97 210C88 222 94 232 100 240C106 232 112 224 105 212',
          'M94 214C81 223 61 218 51 227C43 234 48 243 59 242M107 214C124 222 145 215 156 225C167 233 160 240 148 240',
          'M99 218C101 231 89 241 93 250L100 258L107 249C111 240 101 230 103 218',
          'M87 220Q82 212 76 207M119 222Q126 213 129 208M68 224Q57 222 53 217M142 226Q153 221 159 216'
        ],
        sites:[[62,130,-20],[70,150,-35],[84,115,-10],[89,133,-25],[101,111,0],[103,122,15],[124,137,20],[137,119,35],[42,145,-65],[59,163,-55],[159,138,40],[149,155,50],[35,169,-85],[57,177,-65],[170,169,80],[153,176,60],[61,148,-25],[77,137,-30],[96,132,-5],[113,133,10],[144,142,40],[151,163,25],[49,152,-50],[82,155,-20],[94,162,10],[119,166,30],[130,173,65],[72,173,-60],[163,173,75],[104,146,0]]}
    };
    const tree=trees[id];
    core=g(p(tree.trunk,`fill="url(#${id}-wood)" stroke="#f5ffed" stroke-width=".35"`),'data-tree-part="trunk"');
    core+=g(tree.crown.map((d,i)=>p(d,`stroke="url(#${id}-wood)" stroke-width="${i===tree.crown.length-1?.55:i%3===0?1.45:1.05}"`)).join(''),'data-tree-part="crown"');
    core+=g(tree.roots.map((d,i)=>p(d,`stroke-width="${i%3===0?.9:.55}"`)).join(''),'data-tree-part="roots"');
    for(let j=0;j<tree.sites.length;j++){
      const[x,y,a]=tree.sites[j],lc=leafColor(x,y);
      const twigs=[
        ['M0 4Q-1-1 0-6M0 1Q-4-2-5-4M0 2Q4 0 5-2',[[0,-6,0],[-5,-4,-52],[5,-2,48],[-1,1,-62],[2,0,50]]],
        ['M0 4Q3-1 2-7M1 2Q-3 0-4-3M2-2Q5-3 7-6',[[2,-7,12],[-4,-3,-40],[7,-6,55],[1,-2,-37],[3,1,62]]],
        ['M0 4Q-3 0-2-7M-1 1Q2-1 3-4M-2-3Q-6-3-7-6',[[-2,-7,-14],[3,-4,32],[-7,-6,-55],[0,2,45],[-2,0,-62]]],
        ['M0 4Q0-2-1-5M0 2Q5 2 7-1M-1 0Q-5 0-6-3',[[-1,-5,-8],[7,-1,65],[-6,-3,-65],[3,2,80],[-3,0,-70]]]
      ][(j*7+(moon?1:season?2:gem?3:0))%4];
      let sprout=p(twigs[0],'stroke-width=".22"');
      for(const[lx,ly,la]of twigs[1])sprout+=gem?crystalLeaf(lx,ly,2.2+(j%3)*.3,la,lc):leaf(lx,ly,1.9+(j%3)*.3,la,lc);
      ornaments+=g(sprout,`transform="translate(${x} ${y}) rotate(${a})"`);
      if(!gem&&j%3!==1)ornaments+=flower(x+(j%2?-3:4),y+(j%4)-1,moon?2.9:2.2,moon?'#eed3f4':lc);
    }
    if(gem){core+=crystal(100,218,15)+crystal(100,121,14);geometry+=local(orbit(65,13,-15)+orbit(65,13,15));}
    const centerpieceY=moon?185:163;
    lights+=spark(100,centerpieceY,gem?8:10,'fill="#f3fff4"')+c(100,centerpieceY,17,`fill="url(#${id}-spark)" stroke="none"`);
    // The top and bottom seals distinguish the four native compositions.
    if(moon){
      geometry+=c(100,62,20)+moons(100,62,17)+spark(100,62,5)+p('M100 12V42M100 82V103M100 267V298');
      ornaments+=flower(100,87,5,'#f0d0ef')+flower(100,265,4,'#f0c8ef')+moons(100,283,8)+crystal(100,297,6);
      outer+=local([0,60,120,180,240,300].map(a=>{const[x,y]=polar(83,a);return c(x,y,8)+moons(x,y,6);}).join(''));
      atmosphere+=g(orbit(100,19,-20),`transform="translate(0 -35)"`)+g(orbit(100,20,-20),`transform="translate(0 220)"`);
    }else if(season){
      geometry+=moons(100,32,11,true)+p('M100 42V65M100 264V323')+c(100,298,7,`fill="url(#${id}-planet)"`)+moons(100,341,10,true);
      ornaments+=flower(100,cy-rad+1,9,'#ffcce3')+at(snow(10),0,cy+rad-100);
      ornaments+=sprig(100-rad,cy,9)+at(p('M0-12L3-5L9-8L7-2L12 0L5 4L5 9L0 6L-5 9L-5 4L-12 0L-7-2L-9-8L-3-5Z','fill="#ffc885"'),100+rad,cy,.8);
      outer+=local(medallions(8,rad-1,10));runes=local(tick(rad-5,130,.6));
    }else if(gem){
      geometry+=p('M100 0V69M100 257V354')+c(100,29,18)+c(100,29,13)+spark(100,29,14)+crystal(100,62,12)+c(100,82,5,`fill="url(#${id}-planet)"`);
      geometry+=crystal(100,263,10)+c(100,288,19)+c(100,288,12)+spark(100,288,17)+crystal(100,341,7);
      atmosphere+=g(orbit(96,22),`transform="translate(0 -18)"`)+g(orbit(96,20,-20),`transform="translate(0 195)"`);
      outer+=local([0,90,180,270].map(a=>{const[x,y]=polar(rad-8,a);return c(x,y,6,`fill="url(#${id}-planet)"`)+spark(x,y,8);}).join(''));
    }else{
      geometry+=moons(100,23,11)+spark(100,23,5)+p('M100 0V13M100 34V56M100 263V355');
      outer+=local([0,90,180,270].map((a,i)=>{const[x,y]=polar(rad-1,a);return c(x,y,9)+(i%2?flower(x,y,5,'#edffe5'):moons(x,y,7));}).join(''));
      ornaments+=crystal(100,294,8)+sprig(100,312,7);
      atmosphere+=g(orbit(97,26,20),`transform="translate(0 -28)"`);
    }
    // Botanical wreath with intentional clear breaks at the cardinal seals.
    if(!gem)for(let i=0;i<62;i++){
      const a=i*360/62+9;if(Math.abs(a%90)<9)continue;
      const[x,y]=polar(rad+2+(i%3-1)*1.5,a,100,cy),lc=leafColor(x,y);
      ornaments+=leaf(x,y,2.4+(i%3)*.35,a+45,lc)+p('M'+polar(rad,a-3,100,cy).join(' ')+'Q'+polar(rad+3,a,100,cy).join(' ')+' '+polar(rad,a+4,100,cy).join(' '),'stroke-width=".32"');
      if(i%5===0)ornaments+=flower(x,y,moon?4:3.5,season?lc:'#efcfed');
    }
    // Moon phases sit on the inner wreath, using individually closed crescents.
    for(let i=0;i<12;i++){
      const a=i*30+15,[x,y]=polar(rad-15,a,100,cy);
      ornaments+=i%3===0?c(x,y,2.9,`fill="url(#${id}-planet)"`):moons(x,y,2.8,i%2===0);
    }
    // Fine vertical chains and hanging jewels are prominent in every native portrait.
    for(let i=0;i<13;i++){
      const x=24+i*12.6,start=cy+37+Math.sin(i*1.4)*6,end=start+8+(i*7)%27;
      if(Math.abs(x-100)<12)continue;
      ornaments+=p(`M${n(x)} ${n(start)}V${n(end)}`,'stroke-width=".18"');
      for(let y=start+2;y<end;y+=3.8)ornaments+=c(x,y,.36,'fill="currentColor"');
      ornaments+=gem?crystal(x,end+4,3.5):diamond(n(x),n(end+3),3);
    }
    for(let i=0;i<16;i++){
      const x=12+(i*43)%179,y=25+(i*61)%304;
      if(x>55&&x<145&&y>100&&y<240)continue;
      ornaments+=butterfly(x,y,2.2+i%3,leafColor(x,y),i*31)+spark(x+5,y+4,1.2,'fill="currentColor"');
    }
    // Corner floral sprays in the moon/four-season portraits.
    if(moon||season)for(const [ox,oy,sx,sy] of [[4,8,1,1],[196,8,-1,1],[3,341,1,-1],[195,342,-1,-1]]){
      let spray='';for(let j=0;j<14;j++){const x=ox+sx*(j*2.3),y=oy+sy*(4+(j*j%21));spray+=leaf(x,y,2.8,j*37,leafColor(ox,oy))+flower(x+sx*2,y+sy*2,2.5+(j%3),moon?'#cfbce8':leafColor(ox,oy));}
      ornaments+=spray;
    }
    if(gem)for(let i=0;i<13;i++){
      const x=14+i*14,start=(i*13)%42,end=45+(i*17)%26;
      ornaments+=p(`M${x} ${start}V${end}`,'stroke-width=".17"')+crystal(x,end+5,3+i%3)+spark(x,end-7,2);
    }
    // Multiple broad mist paths replace the source's photographic nebula texture.
    for(let i=0;i<12;i++){
      const side=i%2?-1:1,x=side===1?4:196,y=30+i*28;
      atmosphere+=at(p('M-16 0C-20-15-7-20-1-12C5-22 21-11 15-2C31 9 13 20 3 14C-7 24-24 10-16 0Z',`fill="${moon?'#6585d2':'#4ac6c9'}" fill-opacity=".085" stroke="none"`),x,y,1.2+(i%3)*.6);
    }
    if(!season&&!gem){
      for(let j=0;j<6;j++)atmosphere+=`<ellipse cx="100" cy="${moon?337:328}" rx="${13+j*13}" ry="${2+j*1.7}" stroke-width="${j%2?.25:.6}"/>`;
      for(let side of [-1,1])for(let j=0;j<8;j++)atmosphere+=at(p('M-10 5C-15-1-8-8-3-5C0-15 11-8 10-2C20-5 23 9 12 12H-9Z',`fill="${moon?'#8da8eb':'#a7dfe7'}" fill-opacity=".19" stroke="none"`),100+side*(76+j%3*6),313+j*5,1+j%3*.3);
    }
    const title=id+' · 직접 작성 세계수';
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 356" preserveAspectRatio="xMidYMid meet" role="img" data-direct-circle="${id}" aria-label="${title}"><title>${title}</title>${defs(id,color)}<defs><linearGradient id="${id}-wood"><stop stop-color="#a6efff"/><stop offset=".4" stop-color="#f8fff3"/><stop offset=".6" stop-color="#ffffdc"/><stop offset="1" stop-color="#b5f4e1"/></linearGradient><filter id="${id}-mist" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="2.5"/></filter></defs>${p('M0 0H200V356H0Z','fill="#020a15"')}${c(100,cy,150,`fill="url(#${id}-wash)"`)}${g(atmosphere,`data-layer="atmosphere" fill="none" stroke="${color}" filter="url(#${id}-mist)"`)}${drawLayer(id,'outer-ring',outer,color,.38)}${drawLayer(id,'rune-ring',runes,color,.25)}${drawLayer(id,'geometry',geometry,color,.45,true)}${drawLayer(id,'center-core',core,color,.65,true)}${drawLayer(id,'botanical-ornaments',ornaments,color,.28,true)}${drawLayer(id,'highlights',lights,color,.4)}${drawLayer(id,'particles',particles(id,720,200,356),color,.2)}</svg>`;
  }
  root.DirectCircleExtra=Object.freeze({ids:Object.freeze(ids),svg});
})(globalThis);
