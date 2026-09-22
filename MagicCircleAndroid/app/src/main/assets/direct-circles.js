/* Shared by the Android charging page and reference comparison. Directly authored paths only. */
(function (root) {
  'use strict';
  const ids = ['C03', 'R01', 'W03','C12','C13','C14','C15','C16','C18','C22','C27','C01','C02','C04','C05','C06','C07','C08','C09','C10','C11'];
  const titles = { C03:'라파엘 · 금빛 궤도', R01:'육엽 · 청록의 문장', W03:'성월의 정원 · 세계수',C12:'정령 계약진',C13:'태양의 마법진',C14:'달의 마법진',C15:'별의 인도',C16:'시간의 톱니',C18:'4속성 조화',C22:'봉인의 마법진',C27:'심판의 눈' };
  Object.assign(titles,{C01:'기본형 · 로즈 룬',C02:'심플 블루',C04:'실버 · 고정밀',C05:'바이올렛 · 회전 링',C06:'시안 · 홀로그램',C07:'골드 · 코어 발광',C08:'미니멀 실버',C09:'블루 · 궤도',C10:'리무르 스킬풍',C11:'레드 · 다중 링'});
  const n = value => +value.toFixed(3);
  ids.push('C17','C19','C20','C21','C23','C24','C25','C26','C28','C29','C30');
  const extra=root.DirectCircleExtra;
  if(extra)ids.push(...extra.ids);
  Object.assign(titles,{C17:'연금술 마법진',C19:'신성 문장',C20:'천사의 인장',C21:'악마의 계약',C23:'보호 결계진',C24:'공간 전이진',C25:'생명의 나무',C26:'운명의 수레바퀴',C28:'무한의 고리',C29:'원소 융합진',C30:'대현자 · 최종 형태'});
  const p = (d, extra='') => `<path d="${d}" ${/[zZ]\s*$/.test(d) ? 'data-closed="true"' : 'data-open-stroke="true"'} ${extra}/>`;
  const c = (x,y,r,extra='') => `<circle cx="${n(x)}" cy="${n(y)}" r="${r}" ${extra}/>`;
  const g = (body,extra='') => `<g ${extra}>${body}</g>`;
  const closed = points => 'M'+points.map(a=>a.map(n).join(' ')).join(' L')+' Z';
  const polar = (r,a,x=0,y=0) => [n(x+r*Math.cos(a*Math.PI/180)),n(y+r*Math.sin(a*Math.PI/180))];
  const diamond = (x,y,r) => p(closed([[x,y-r],[x+r,y],[x,y+r],[x-r,y]]));
  const layer = (id,name,body,{color='#ffe1a9',width=2,glow=.55,motion='',fixed=false}={}) =>
    g(g(body.replace(/\sdata-[\w-]+="[^"]*"/g,''),`class="halo" aria-hidden="true" opacity="${glow}" filter="url(#${id}-soft)" data-motion="glow"`)+
      g(body,`data-outline="${name}"${motion ? ` data-motion="${motion}"` : ''}`),
      `data-layer="${name}" fill="none" stroke="${color}" stroke-width="${width}" stroke-linecap="round" stroke-linejoin="round"${fixed ? ' data-fixed="true"' : ''}`);
  function ticks(x,y,r,count,length=5) {
    return Array.from({length:count},(_,i)=>p(`M${polar(r,i*360/count,x,y).join(' ')} L${polar(r+(i%5===0?length*1.7:length),i*360/count,x,y).join(' ')}`)).join('');
  }
  function inscription(x,y,r,count,size,rounded=false) {
    const marks=rounded?[
      'M-5-6Q-9-4-6 1L-6 5Q0 9 5 5L5-4Q1-8-5-6Z',
      'M-5-6L3-6Q7-3 4 0Q8 6 1 6L-5 4L-3 0Z',
      'M-4-6Q3-8 5-3L5 5Q0 8-5 4L-6-2Z',
      'M-5-5L-1-7L5-4L4 5L-3 6Q-7 3-4 0Z',
      'M-5-5Q-1-8 4-5L5 0L2 1L5 4Q1 8-5 4Z',
      'M-5-5L4-6L6-1L3 5L-4 6L-6 0Z',
      'M-6-4L-2-6L4-5Q7-2 3 1L5 5L-3 6L-6 2Z',
      'M-5-6L2-6L2-2L5-1L4 5Q0 8-4 4L-4 0L-6-1Z',
      'M-4-6Q1-8 5-3L3 0L5 4Q1 8-3 5L-5 1Z',
      'M-6-3Q-4-7 1-6L5-4L4 2L1 6L-5 4Z',
      'M-5-4L-1-7L4-4L3-1L5 1L3 5L-3 6L-5 2Z',
      'M-4-6L4-5L5-1Q1 1 4 4L1 6L-4 4L-6-1Z',
      'M-6-4L0-6L5-2L4 4L0 6L-4 4L-3 1L-6-1Z',
      'M-5-5L4-6L4-3L6 0L3 5L-3 6L-5 3L-3 0Z',
      'M-4-6Q0-7 4-4L5 2L2 5L-4 5L-6 1L-3-1Z',
      'M-6-4L-2-6L1-3L5-4L6 2L2 6L-3 5L-5 1Z'
    ]:[
      'M-5 3L-2-3L0 2L4-1M-2-3L-1-7M0 2L1 5',
      'M-5 0Q-2 5 0-1L2-5L4 1M-1 1L5 3',
      'M-5 3L-2 0L-2-5M-2 0L1 2L4-3M2 1V6',
      'M-5 2L-2-2L0 2L2-2L5 0M0 2V5',
      'M-4-3Q0-6 2-2L-1 2L4 3M-2 2L-2 6',
      'M-5 1L-1 1L1-5L1 4L5-1M-1 1L-3-3',
      'M-4 4L-1-4L1 1L5-2M1 1L2 5',
      'M-5-1L-2 2L1-3L4 1M1-3V-6M-2 2V5',
      'M-5 2Q-1-5 1 0L4-2M-3 2L2 4M1 0L3-6',
      'M-5 0L-2-4L0 3L4-1M-2-4L-3-7M0 3L4 5',
      'M-5 1L-3-3Q1 3 2-3L4 1M0 1L-1 5',
      'M-5 3L-2 2L-1-5L2-2L4-4M-1 1L3 4',
      'M-5 0Q-2-4 0 1L3-1L5 2M-1-1L-2-6M3-1V-5',
      'M-5 2L-3-1L0 3L3-5L4 1M0 3L-1 6',
      'M-5-1L-3 3L1-3L4 0M1-3L0-7M-2 2L3 5',
      'M-5 2Q-1 4-1-2L0-5L3-1L5-2M1 1L3 4',
      'M-5 3L-3-3L0-1L2-5M0-1L0 4L5 2',
      'M-5-1L-2 2L0-4L2 2L5 0M0-4L-1-7M-2 2L-3 5',
      'M-5 0L-2-3L0 1Q3 4 5-1M0 1V5M2 1L3-5',
      'M-5 2L-2 0L0-5L3-1L4 4M-2 0L0 4M3-1L5-4'
    ];
    return Array.from({length:count},(_,i)=>{
      if(!rounded && Math.min(i%(count/4),count/4-i%(count/4))<2) return '';
      const a=i*360/count-90;
      return g(p(marks[(i*3+Math.floor(i/7))%marks.length]),`transform="translate(${polar(r,a,x,y).join(' ')}) rotate(${a+90}) scale(${size})"`);
    }).join('');
  }
  function star(x,y,r,color='#fff3d1') {
    return p(`M${x} ${y-r} Q${x+1.3} ${y-1.3} ${x+r*.62} ${y} Q${x+1.3} ${y+1.3} ${x} ${y+r} Q${x-1.3} ${y+1.3} ${x-r*.62} ${y} Q${x-1.3} ${y-1.3} ${x} ${y-r} Z`,`fill="${color}" stroke-width=".6"`);
  }
  function particles(id,w,h,count) {
    return layer(id,'particles',Array.from({length:count},(_,i)=>{
      const x=24+(i*167.39)%(w-48),y=24+(i*257.71)%(h-48);
      return c(x,y,i%19===0?1.65:i%4===0?1:.5,`fill="${i%3===0?'#e5faff':'#7bdcfa'}" stroke="none" opacity="${.2+(i%7)*.095}" data-motion="particle" data-phase="${i%17}"`);
    }).join(''),{color:'#8ce1ff',width:.5,glow:.36});
  }
  function c03() {
    const id='C03';
    const orbits=[
      'M170 557 C170 491 322 438 510 438 C698 438 842 491 842 557 C842 623 698 675 510 675 C322 675 170 623 170 557 Z',
      'M265 307 C320 305 474 380 633 454 C782 595 800 746 724 791 C641 805 496 710 365 648 C234 517 207 365 265 307 Z',
      'M758 307 C809 380 757 525 634 649 C511 710 364 805 296 792 C227 736 252 583 387 443 C522 380 692 298 758 307 Z'
    ];
    const geometry=p('M510 181L632 532L510 877L385 532Z')+
      p('M341 396L520 331L705 412L731 628L566 743L357 658Z','opacity=".5"')+
      p('M337 663L510 324L710 653Z','opacity=".33"')+p('M321 435L697 429L528 777Z','opacity=".33"');
    const core=c(510,567,117)+p('M510 422L657 567L510 714L370 567Z')+
      p('M431 480H602V625H431Z','opacity=".8"')+p('M510 462L592 625H431Z')+
      p('M421 499H602L510 675Z')+p('M510 510L560 569L510 635L463 569Z','opacity=".85"')+
      p('M510 546L529 569L510 592L492 569Z','opacity=".7"');
    const nodes=[[170,557],[842,557],[265,307],[724,791],[758,307],[296,792]];
    return c(510,530,490,`fill="url(#${id}-ambient)"`)+
      layer(id,'outer-ring',c(510,530,466)+c(510,530,455,'stroke-width="1.3"')+c(510,530,365),{width:2.8})+
      layer(id,'ring-light',c(510,530,459,'stroke-dasharray="30 690"'),{width:2.2,glow:.6,motion:'ring'})+
      layer(id,'rune-ring',inscription(510,530,429,136,1.4)+[0,90,180,270].map(a=>g(p('M0-13V13M-9 0H9M-4-5L4 5M4-5L-4 5'),`transform="translate(${polar(429,a,510,530).join(' ')}) rotate(${a+90})"`)).join(''),{width:1.65,motion:'reveal',glow:.36})+
      layer(id,'geometry',geometry,{width:1.8,color:'#d3a460',glow:.4})+
      layer(id,'center-core',core,{width:3,motion:'reveal',glow:.7})+
      layer(id,'orbits',orbits.map((d,i)=>p(d,`data-motion="draw" data-phase="${i}"`)).join(''),{width:3.1,glow:.85})+
      layer(id,'stars',nodes.map(([x,y])=>c(x,y,24,`fill="url(#${id}-point)" stroke="none"`)+c(x,y,4.2,'fill="#fff9db" stroke="none"')+star(x,y,12)).join(''),{fixed:true,width:.7,glow:.8})+
      particles(id,1024,1024,38);
  }
  function r01() {
    const id='R01';
    const petal='M62 0 C126 -60 196 -100 249 -88 C297 -78 338 -34 366 0 C322 46 281 92 245 87 C182 86 111 36 62 0 Z';
    const node=(i)=> {
      const [x,y]=i%2?[246,21]:[273,-23];
      const inside=c(x,y,49)+c(x,y,42,'stroke-width="1.15"')+
        g(ticks(0,0,38,20,2),'transform="translate('+x+' '+y+')" stroke-width="2"')+
        (i%2?g([31,22,10].map(r=>diamond(0,0,r)).join('')+c(0,0,2.4,'fill="#ddfbff"'),`transform="translate(${x} ${y})"`):'');
      return g(c(255,0,80)+c(255,0,72,'stroke-width="1.2"')+g(inside,`data-core-kind="${i%2?'diamond':'empty'}"`),`transform="rotate(${i*60})"`);
    };
    // Marks are separately authored clean strokes; source photographs remain in the comparison pane.
    const outer=layer(id,'outer-ring',[440,420,390,372].map(r=>c(0,0,r)).join(''),{color:'#8aeaff',width:1.8});
    const petals=Array.from({length:6},(_,i)=>g(p(petal,`data-petal="${i}" data-motion="draw" data-phase="${i}"`),`transform="rotate(${i*60})"`)).join('');
    const bridge='M-11 -61.016L11 -61.016C9 -102 9 -143 13 -180L-13 -180C-9 -143 -9 -102 -11 -61.016Z';
    const gate='M-35 -214L35 -214Q47 -245 61 -278Q0 -291 -61 -278Q-47 -245 -35 -214Z M-29 -221L29 -221Q39 -249 51 -273Q0 -283 -51 -273Q-39 -249 -29 -221Z';
    const scaffold=p(closed([0,60,120,180,240,300].map(a=>polar(366,a))))+
      Array.from({length:6},(_,i)=>g(p(bridge),`transform="rotate(${i*60})"`)).join('')+
      [0,180].map(a=>g(p(gate),`transform="rotate(${a})"`)).join('')+
      [[1,1],[-1,1],[1,-1],[-1,-1]].map(([x,y])=>g(p('M205 -180L257 -126L199 -155Z M211 -166L241 -134L208 -152Z'),`transform="scale(${x} ${y})"`)).join('');
    const cursive=[
      'M-9 4L-10-5Q-7-8-4-3L-1 1L0-5Q3-8 6-3L9 5L5 7L2 1L0 6L-4 6L-6-1L-6 5Z',
      'M-10-4L-6-7L-3-1L0-5L4-4L7 0L10-2L10 4L5 6L1 1L-1 5L-5 3L-6-1L-8 1Z',
      'M-9-6L-5-7L-3 1Q0-4 2-4Q7-7 9-2L8 5L4 6L4 0L1 2L0 7L-4 6L-5 2L-8 3Z',
      'M-10 3L-8-3L-5-4L-4 0L-1-6L3-5L5 1L7-2L10 0L8 6L4 6L1 1L-1 6L-5 4L-8 6Z',
      'M-9-4L-5-6L-2 0L0-4L4-5L6-1L9-3L10 3L6 5L2 2L0 6L-4 6L-5 1L-8 2Z',
      'M-10-3L-7-6L-3-3L-1-6L3-4L3 0L8-1L10 3L6 6L1 4L-1 1L-3 6L-7 4L-6 0L-9 1Z',
      'M-9 3L-8-5L-4-6L-2-1L1-5L4-4L5 1L8-2L10 2L7 6L3 5L1 1L-2 6L-5 3L-6 6Z',
      'M-10-4L-7-6L-4-1L-1-3L1-6L5-5L5 0L9 2L8 6L3 5L1 0L-2 5L-5 4L-6 0L-9 1Z'
    ];
    const innerInscription=Array.from({length:30},(_,i)=>{
      const offset=14+(i%5)*8,a=Math.floor(i/5)*60+offset;
      const radius=(317/Math.cos((offset-30)*Math.PI/180)+372)/2;
      return g(p(cursive[(i*3)%cursive.length]),`transform="translate(${polar(radius,a).join(' ')}) rotate(${a+90}) scale(1.16)"`);
    }).join('');
    return c(512,512,486,`fill="url(#${id}-ambient)"`)+g(outer+
      layer(id,'rune-ring',inscription(0,0,405,76,1.2,true)+innerInscription,{color:'#8bddea',width:1.7,glow:.33,motion:'reveal'})+
      layer(id,'geometry',scaffold,{color:'#8be4f4',width:2,glow:.38})+
      layer(id,'petals',petals,{color:'#b9f9ff',width:2.5,glow:.55,motion:'reveal'})+
      layer(id,'nodes',Array.from({length:6},(_,i)=>node(i)).join(''),{color:'#b9f9ff',width:2.05,glow:.38})+
      layer(id,'center-core',[62,44,36].map(r=>c(0,0,r)).join('')+ticks(0,0,48,24,3)+diamond(0,0,28)+diamond(0,0,13)+c(0,0,3,'fill="#ecffff"'),{color:'#d0fcff',width:2,motion:'reveal'}), 'transform="translate(512 512)"');
  }
  // A tapered leaf with a curved spine. Placements below are tied to drawn boughs.
  function leaf(x,y,angle,size=1,color='#b9f5ee',spread=1) {
    return g(p('M0 0C-4 -6 -9 -17 -1 -31C0 -23 7 -13 0 0Z',`data-leaf="true" fill="${color}" fill-opacity=".82" stroke="${color}" stroke-width=".55"`)+
      p('M0 0Q-2 -17 -1 -31C0 -23 7 -13 0 0Z','fill="#153d53" fill-opacity=".17" stroke="none"')+
      p('M0 0Q-2 -17 -1 -29M-1 -7L-4 -13M-1 -13L-5 -19M-1 -19L-4 -25M-1 -9L3 -16M-1 -15L3 -21M-1 -21L1 -26','fill="none" stroke="#f3ffff" stroke-width=".38" stroke-opacity=".8"'),`transform="translate(${x} ${y}) rotate(${angle}) scale(${n(size*spread)} ${size})"`);
  }
  function flower(x,y,scale=1) {
    return g([-65,-27,25,63,145,210].map((a,i)=>leaf(0,0,a,i<4?1:.68,i%2?'#fff0c9':'#a9f0ff')).join('')+c(0,0,2.1,'fill="#fff7df"'),`transform="translate(${x} ${y}) scale(${scale})"`);
  }
  function butterfly(x,y,scale=1,angle=0,color='#b7efff') {
    const wing=p('M0 0C-4 -18 -17 -28 -31 -28C-29 -12 -18 -3 0 0Z M-1 2C-13 -2 -26 2 -20 14C-14 24 -5 15 -1 2Z',`fill="${color}" fill-opacity=".73" stroke="#e9ffff" stroke-width=".7"`)+
      p('M-1 0L-27 -25M-2 2L-17 15M-3 0Q-19 -14 -27 -17','fill="none" stroke="#edffff" stroke-width=".8"');
    return g(wing+g(wing,'transform="scale(-1 1)"')+p('M0 10Q-2 -4 0 -9M0 -7Q-6 -17 -9 -16M0 -7Q6 -17 9 -16','stroke="#fff9df" stroke-width="1.1" fill="none"'),`transform="translate(${x} ${y}) rotate(${angle}) scale(${scale})"`);
  }
  function w03() {
    const id='W03',gold='#f3dda5',blue='#8ee9ff';
    const rings=[465,452,439,428,376,364,352,319].map((r,i)=>c(500,831,r,`stroke="${[1,3].includes(i)?blue:gold}" stroke-width="${i===1?2.8:i===3?1.8:1}" opacity="${i===7?.45:.9}"`)).join('');
    const ringGlints=Array.from({length:104},(_,i)=>{
      const a=i*3.46-151,r=439+Math.sin(i*2.7)*1.6,span=.3+(i%5)*.45;
      return p(`M${polar(r,a,500,831).join(' ')}A${n(r)} ${n(r)} 0 0 1 ${polar(r,a+span,500,831).join(' ')}`,`stroke="${i%5?'#b1f6ff':'#fff9d3'}" stroke-width="${i%7===0?3:1.2}" opacity="${.4+(i%3)*.2}"`);
    }).join('');
    const ringAura=p('M163 547A440 440 0 0 1 819 528M924 933A440 440 0 0 1 208 1160M66 899A440 440 0 0 1 76 713','stroke="#66ddff" stroke-width="8" opacity=".43" fill="none" filter="url(#W03-aura)"');
    const ringsDetail=ticks(500,831,356,180,5)+ticks(500,831,444,120,3)+inscription(500,831,397,136,.9);
    const orbits=p('M106 590C286 548 836 881 801 925C766 969 326 743 123 635C73 608 66 593 106 590Z')+
      p('M900 590C716 553 173 882 202 926C235 975 675 742 877 635C927 609 937 593 900 590Z')+
      p('M78 984C1 1076 119 1101 272 1081C501 1050 735 938 878 815','opacity=".85"')+
      p('M920 984C1001 1078 881 1101 728 1081C499 1050 264 938 122 815','opacity=".85"');
    // Distinct, hand-plotted major limbs; roots are separate and stay attached to the trunk.
    const boughs=[
      'M493 861C475 813 439 784 369 786C325 788 276 807 224 829',
      'M495 836C468 787 445 748 397 727C358 710 318 724 274 707',
      'M490 800C465 770 465 727 439 692C413 658 374 646 354 612',
      'M494 767C475 723 486 687 466 650C444 620 416 604 408 578',
      'M498 719C489 682 496 644 482 611C469 584 451 567 455 541',
      'M494 858C455 805 405 807 361 843C339 862 297 882 257 875',
      'M448 785C410 785 390 762 362 750C332 738 299 752 273 745',
      'M397 727C371 702 372 674 342 654C324 641 307 640 298 620',
      'M439 692C424 690 395 691 377 679C356 666 338 676 320 665',
      'M369 786C350 774 346 759 327 761C302 763 287 778 263 777',
      'M361 843C343 839 331 823 306 836C292 844 279 844 269 840',
      'M466 650C451 645 441 648 428 632C413 615 397 618 386 606',
      'M507 862C533 809 570 782 626 789C679 796 721 810 777 831',
      'M506 832C538 774 562 742 609 723C646 708 688 723 732 702',
      'M511 791C539 754 540 717 565 682C585 652 615 640 638 608',
      'M506 757C522 714 519 677 544 643C565 614 584 603 596 576',
      'M502 713C514 676 508 639 522 609C536 578 548 567 542 538',
      'M507 860C548 807 594 816 634 846C660 866 694 883 738 875',
      'M558 785C597 781 615 762 642 750C672 738 701 746 727 739',
      'M609 723C637 701 632 677 657 656C678 639 691 641 704 621',
      'M565 682C590 691 607 680 624 679C648 677 664 668 678 663',
      'M626 789C644 767 660 760 680 765C700 770 717 783 739 777',
      'M634 846C657 835 671 829 690 839C707 848 721 844 733 842',
      'M544 643C560 645 572 640 583 627C594 615 609 615 620 602'
    ];
    const trunk=p('M480 913C496 883 493 856 486 828C480 806 464 790 445 779C472 785 485 795 494 812C493 781 488 756 482 735C495 748 498 759 501 775C507 749 516 729 528 715C515 749 509 776 510 813C522 792 539 785 560 783C537 796 519 817 515 844C511 872 513 897 525 920C541 944 565 961 606 970C559 966 530 952 508 931C510 969 522 991 549 1010C520 1000 506 984 500 969C492 991 476 1004 452 1013C478 993 488 973 490 933C471 952 443 964 399 972C436 958 467 940 480 913Z',`fill="url(#${id}-wood)" stroke="#fff3cc" stroke-width="1.4"`);
    const rootPaths=[
      'M495 912C472 965 426 942 382 969C349 989 311 973 282 1009',
      'M492 931C459 986 416 971 389 1003C364 1034 323 1026 306 1052',
      'M493 945C466 996 438 1004 422 1041C411 1066 389 1091 364 1108',
      'M499 955C492 1004 459 1027 458 1068C457 1103 439 1135 420 1149',
      'M490 931C458 956 411 932 382 948C355 963 330 950 307 960',
      'M481 947C443 963 415 948 387 955C367 960 354 946 334 948',
      'M459 967C419 992 390 978 361 998C344 1010 321 1000 301 1017',
      'M441 1001C413 1015 388 1012 376 1041C367 1060 347 1063 331 1074',
      'M422 1041C399 1051 388 1041 371 1052',
      'M458 1068C439 1082 418 1072 399 1089',
      'M505 912C532 967 578 945 618 967C649 984 687 978 718 1006',
      'M509 931C538 984 582 974 611 1005C638 1037 675 1024 695 1053',
      'M507 945C533 994 565 1005 580 1043C591 1069 612 1090 639 1108',
      'M501 955C508 1002 541 1029 542 1069C543 1100 560 1135 580 1148',
      'M510 931C542 955 591 934 620 949C647 963 673 950 695 961',
      'M520 947C559 962 585 947 614 955C633 961 648 945 667 948',
      'M541 967C581 994 611 977 640 998C657 1011 680 1002 700 1017',
      'M559 1001C587 1014 612 1014 624 1040C633 1062 653 1065 669 1075',
      'M580 1043C601 1052 614 1041 631 1054',
      'M542 1069C561 1082 582 1074 601 1089',
      'M500 972C487 1023 510 1053 499 1102C493 1120 499 1145 500 1160'
    ];
    // Terminal sprigs are placed against the limbs above, not a procedural fractal tree.
    const sprigs=[
      [224,829,-62,.85],[245,821,-27,.65],[263,777,-61,.75],[282,779,23,.68],[274,707,-52,.85],[294,715,-16,.78],[313,719,25,.77],[273,745,-58,.62],[297,746,-8,.66],
      [298,620,-26,.64],[310,641,-60,.72],[323,646,16,.64],[338,660,-40,.64],[342,681,36,.69],[354,612,-12,.8],[365,635,-42,.7],[379,649,31,.75],[394,661,-25,.74],[320,665,-54,.65],[347,672,-4,.64],[377,679,-35,.58],
      [408,578,-18,.76],[416,604,-43,.71],[432,621,27,.77],[443,634,-24,.57],[455,541,-5,.82],[458,566,-45,.6],[473,588,27,.58],[478,610,-31,.6],[486,631,29,.66],[482,653,-25,.72],
      [428,632,-54,.58],[398,618,-16,.63],[386,606,-35,.72],[440,692,-22,.77],[451,708,38,.8],[456,730,-36,.74],[435,744,-66,.65],[412,735,7,.68],[395,722,-28,.7],[362,750,-27,.66],
      [327,761,-18,.58],[350,779,33,.63],[369,786,-23,.7],[387,782,20,.77],[404,790,-28,.65],[421,798,24,.72],[257,875,-73,.8],[278,872,-29,.6],[300,868,12,.63],[306,836,-39,.62],[329,838,22,.72],[351,850,-12,.63],[367,841,25,.67],
      [777,831,66,.82],[754,821,24,.72],[739,777,63,.76],[718,778,-21,.7],[732,702,50,.81],[710,716,16,.7],[690,719,-25,.69],[727,739,53,.68],[701,744,9,.67],
      [704,621,25,.7],[690,641,55,.7],[674,651,-14,.7],[657,661,39,.66],[658,685,-28,.71],[638,608,12,.8],[626,632,42,.74],[611,648,-30,.78],[590,665,29,.76],[678,663,53,.6],[650,674,4,.7],[624,679,31,.62],
      [596,576,18,.75],[583,603,43,.72],[567,620,-26,.8],[551,638,26,.64],[542,538,5,.83],[540,565,43,.64],[526,587,-26,.61],[522,610,33,.65],[515,634,-29,.65],[520,657,29,.7],
      [583,627,48,.63],[608,614,17,.65],[620,602,35,.73],[565,682,22,.79],[551,707,-35,.78],[546,730,37,.71],[566,744,64,.68],[589,734,-8,.7],[610,721,28,.72],[642,750,30,.7],
      [680,765,18,.65],[652,780,-32,.6],[626,789,24,.7],[611,788,-20,.76],[595,791,29,.64],[578,801,-24,.7],[738,875,69,.81],[718,873,28,.62],[697,870,-12,.63],[690,839,39,.7],[670,840,-23,.72],[650,852,14,.61],[634,846,-25,.69],
      [502,574,0,.86],[500,598,-29,.6],[501,620,29,.67],[499,641,-32,.72],[502,665,31,.7],[501,690,-29,.6]
    ];
    const leaves=sprigs.map(([x,y,a,s],i)=>{
      const shade=i%5===0?'#fff0c8':i%3===0?'#e9fff1':'#a3eeff';
      return g(leaf(0,-14,-7,s*.96,shade)+leaf(0,-4,-49,s*.77,shade)+leaf(0,5,43,s*.8,shade)+
        leaf(0,14,-65,s*.76,shade)+leaf(0,22,60,s*.69,shade)+
        p('M0 29Q-2 2 0 -18','stroke="#fff4ce" stroke-width=".8"'),`transform="translate(${x} ${y}) rotate(${a})"`);
    }).join('');
    const twigs=sprigs.map(([x,y,a,s],i)=>p(`M${x} ${y}q${n(Math.sin(a*Math.PI/180)*5)} 10 ${i%2?3:-3} 18`,'stroke-width=".7" opacity=".8"')).join('');
    const rootlets=[
      'M382 969Q366 973 350 963L337 960M350 963Q340 968 329 965',
      'M359 981Q349 1000 334 1002L321 997M334 1002L330 1011',
      'M389 1003Q371 1004 354 1014Q344 1018 335 1015M354 1014L352 1025',
      'M416 983Q408 995 411 1007M409 998L398 1001',
      'M391 1021Q398 1036 387 1053M394 1041L406 1044',
      'M382 948Q370 939 355 942M369 940L365 931',
      'M422 956Q402 945 395 932M406 949L390 948',
      'M448 945Q435 931 419 930M432 936L431 924',
      'M457 985Q441 984 427 993M442 985L434 976',
      'M444 1018Q432 1032 419 1030M432 1032L427 1043',
      'M413 1060Q402 1067 398 1081M404 1070L390 1071',
      'M392 1088Q376 1087 367 1097M379 1089L373 1081',
      'M460 1040Q469 1054 459 1068M466 1055L477 1063',
      'M444 1107Q422 1118 417 1131M430 1115L414 1117',
      'M491 992Q480 1009 482 1025M482 1019L469 1026',
      'M489 1050Q476 1061 481 1081M479 1071L468 1081',
      'M473 963Q463 977 451 977M463 974L461 984',
      'M431 974Q419 972 413 963M418 970L404 976'
    ];
    const fineRootArcs=[
      'M488 934C466 964 439 962 415 978C396 991 369 995 352 1019C342 1033 324 1038 317 1055',
      'M484 948C470 981 449 992 433 1017C417 1041 403 1068 378 1080C364 1087 355 1100 346 1103',
      'M490 959C475 990 481 1026 460 1049C445 1063 447 1091 435 1104C424 1114 426 1124 417 1135',
      'M485 947C464 959 432 956 411 958C389 960 368 950 348 959C334 965 318 958 307 968',
      'M495 978C482 1000 469 1009 466 1034C465 1055 451 1069 452 1084C454 1102 443 1127 436 1140',
      'M472 972C450 977 431 982 415 998C401 1016 381 1011 365 1020C354 1027 339 1024 330 1034',
      'M459 995C445 1019 436 1044 414 1053C402 1058 396 1079 381 1090C370 1097 372 1110 361 1116',
      'M487 985C472 1019 489 1044 478 1061C464 1083 473 1108 461 1127C454 1137 457 1150 452 1160',
      'M442 965C428 951 400 948 387 937C375 928 357 937 345 931',
      'M413 981C395 985 384 971 368 978C355 984 344 977 332 985',
      'M445 1022C432 1024 422 1014 407 1021C395 1026 384 1022 376 1031',
      'M468 1065C489 1091 468 1105 478 1123C485 1134 475 1148 480 1158'
    ];
    const finerRoots=rootlets.map(d=>p(d,'stroke-width=".75"')).join('')+fineRootArcs.map((d,i)=>p(d,`stroke-width="${i<8?1.15:.65}"`)).join('');
    const rootFans=[[382,969,-1],[389,1003,-1],[422,1041,-1],[458,1068,-1],[334,948,-1],[361,998,-1],
      [618,967,1],[611,1005,1],[580,1043,1],[542,1069,1],[667,948,1],[640,998,1]].map(([x,y,side],i)=>
      [0,1,2,3].map(j=>{
        const ex=x+side*(38+j*8),ey=y+13+j*10;
        return p(`M${x} ${y}C${x+side*(14+j*3)} ${y+10} ${x+side*(22+j*8)} ${y+10+j*8} ${ex} ${ey}`,'stroke-width="1"')+
          p(`M${ex} ${ey}q${side*9} ${j%2?9:-7} ${side*21} ${j%2?12:-4}m${-side*11} ${j%2?-6:1}q${side*5} 9 ${side*16} 10`, 'stroke-width=".6" opacity=".9"');
      }).join('')).join('');
    // Offset only these hand-plotted cubic centerlines; this never samples an image.
    // A shared endpoint is emitted once, so branches remain continuous through joins.
    const attachedLeaves=[];
    const limbs=boughs.map((d,index)=>{
      const v=d.match(/-?\d+(?:\.\d+)?/g).map(Number),left=[],right=[];
      const segments=(v.length-2)/6,width=index<6||index>=12&&index<18?7:3;
      for(let segment=0;segment<segments;segment++) {
        const k=segment*6,pts=[[v[k],v[k+1]],[v[k+2],v[k+3]],[v[k+4],v[k+5]],[v[k+6],v[k+7]]];
        for(let j=segment?1:0;j<=24;j++) {
          const t=j/24,u=1-t,progress=(segment+t)/segments;
          const x=u*u*u*pts[0][0]+3*u*u*t*pts[1][0]+3*u*t*t*pts[2][0]+t*t*t*pts[3][0];
          const y=u*u*u*pts[0][1]+3*u*u*t*pts[1][1]+3*u*t*t*pts[2][1]+t*t*t*pts[3][1];
          const dx=3*u*u*(pts[1][0]-pts[0][0])+6*u*t*(pts[2][0]-pts[1][0])+3*t*t*(pts[3][0]-pts[2][0]);
          const dy=3*u*u*(pts[1][1]-pts[0][1])+6*u*t*(pts[2][1]-pts[1][1])+3*t*t*(pts[3][1]-pts[2][1]);
          const norm=Math.hypot(dx,dy)||1,half=(.25+width*Math.pow(1-progress,1.35))/2;
          left.push([x-dy/norm*half,y+dx/norm*half]);right.push([x+dy/norm*half,y-dx/norm*half]);
          if(width===7 && (segment===0?[8,14,20]:[10,18]).includes(j) && Math.abs(x-500)>23) {
            const a=Math.atan2(dy,dx)*180/Math.PI+90;
            attachedLeaves.push(leaf(n(x),n(y),n(a-43),.57,index%3?'#c4f5ff':'#fff3d7')+leaf(n(x),n(y),n(a+48),.49,index%3?'#e8ffff':'#e7d3a7'));
          }
        }
      }
      return p(closed(left.concat(right.reverse())),`data-bough="${index}" fill="#fff1d2" stroke="none"`);
    }).join('');
    const treeBody=trunk+p('M496 855C495 767 493 680 500 548C508 679 505 766 505 855Z','fill="#fff4d9" stroke="none"')+limbs+
      rootPaths.map((d,i)=>p(d,`stroke-width="${i%5===0?2.8:1.9}"`)).join('')+finerRoots+g(finerRoots,'transform="translate(1000 0) scale(-1 1)"')+rootFans+twigs;
    const tree=layer(id,'tree',treeBody,{color:'#fff4d6',width:1.5,glow:1,fixed:true});
    const gardenLeaves=layer(id,'leaves',leaves+attachedLeaves.join(''),{width:.7,color:blue,glow:.68,motion:'reveal'});
    const moon=p('M421 128C388 154 388 195 421 217C395 189 401 150 421 128Z',`fill="url(#${id}-wood)"`)+
      p('M579 128C612 154 612 195 579 217C605 189 599 150 579 128Z',`fill="url(#${id}-wood)"`);
    const crown=c(500,170,69)+c(500,170,58,'stroke-width=".6"')+ticks(500,170,63,48,3)+moon+
      p('M500 12V123M500 217V296M430 170H570','stroke-width=".8"')+
      p('M439 282Q414 251 368 242Q348 234 335 215M560 282Q584 252 631 242Q650 232 666 214','stroke-width="1.2"')+
      [[343,225,-30],[352,234,55],[363,238,30],[373,242,-53],[382,245,-22],[391,250,62],[401,255,32],[414,266,-34],[430,277,28],[657,225,30],[648,234,-55],[637,238,-30],[627,242,53],[618,245,22],[609,250,-62],[599,255,-32],[586,266,34],[570,277,-28]].map(([x,y,a],i)=>leaf(x,y,a,i%3===0?.74:.55,i%4===0?'#ffedd1':blue)).join('')+butterfly(500,330,1.15);
    const amulets=[[500,472,49],[125,831,53],[875,831,53],[500,1224,52],[500,1360,61]];
    const jewels=amulets.map(([x,y,r],i)=>c(x,y,r)+c(x,y,r-9,'stroke-width=".6"')+(i===1||i===2?g(leaf(0,0,0,1.15,blue)+leaf(0,10,-42,.83,blue)+leaf(0,10,42,.83,blue)+p('M0 19V-29','stroke="#e9fbff" stroke-width="1.2"'),`transform="translate(${x} ${y+12})"`):diamond(x,y,r*.63))).join('')+
      c(500,1360,105,'stroke="#9deaff" stroke-width="1.2"')+c(500,1360,135,'stroke-width=".7"')+
      p('M500 359V418M500 523V568M500 1114V1172M500 1277V1295M500 1422V1515','stroke-width="1.1"');
    const ray=(x,y,r,color)=>p(closed([[x,y-r],[x+1.2,y-2],[x+r*.7,y],[x+1.2,y+2],[x,y+r],[x-1.2,y+2],[x-r*.7,y],[x-1.2,y-2]]),`fill="${color}" stroke-width=".25"`);
    const fixedStars=[[500,45,17],[500,92,13],[500,170,43],[500,394,22],[500,472,31],[35,831,25],[965,831,25],[500,864,17],[500,1083,41],[500,1224,30],[500,1305,18],[500,1480,20],[500,1532,32],[500,1678,21]].map(([x,y,r],i)=>c(x,y,Math.min(r*1.1,x-5,995-x),`fill="url(#${id}-point)" stroke="none" opacity=".24"`)+ray(x,y,r,i===2||i===8?'#d5fcff':'#fff5d4')+(r>25?g(ray(x,y,r*.55,'#e8fbff'),`transform="rotate(45 ${x} ${y})"`):'')).join('')+
      [[500,472,32],[500,1224,32],[500,1532,35]].map(([x,y,r])=>p(`M${x} ${y-r}Q${x+4} ${y-4} ${x+r*.7} ${y}Q${x+4} ${y+4} ${x} ${y+r}Q${x-4} ${y+4} ${x-r*.7} ${y}Q${x-4} ${y-4} ${x} ${y-r}Z`,'stroke-width="1.2" fill="none"')).join('');
    const vines=p('M82 1291C21 1169 78 1131 107 1077M918 1291C979 1169 922 1131 893 1077M68 707C22 640 92 570 107 510M932 707C978 640 908 570 893 510','stroke-width="1"')+
      p('M78 34Q38 97 58 205Q87 268 54 354M922 34Q962 97 942 205Q913 268 946 354M88 1436Q126 1498 193 1544Q272 1592 332 1684M912 1436Q874 1498 807 1544Q728 1592 668 1684','stroke-width="1.3"');
    const borderLeaves=[[56,98,-40],[48,125,25],[52,160,-37],[60,190,42],[69,226,-30],[65,260,48],[59,292,-42],[57,327,28],[89,1451,-52],[112,1484,30],[143,1513,-38],[177,1538,41],[215,1560,-24],[251,1590,39],[286,1629,-20],[314,1659,38],[81,1150,-46],[75,1185,21],[78,1231,-35],[87,1270,36],[57,641,-36],[68,602,33],[93,561,-23]].flatMap(([x,y,a],i)=>[leaf(x,y,a,1.25,i%3?blue:gold),leaf(1000-x,y,-a,1.25,i%3?blue:gold)]).join('');
    const chains=[165,835].map(x=>p(`M${x} 12V283`,'stroke-width=".7"')+c(x,74,11)+c(x,165,12)+diamond(x,211,3)+star(x,264,10)).join('');
    const veils=[
      'M24 12C48 125 225 164 285 312C320 415 407 429 423 506',
      'M121 15C76 133 174 225 242 294C292 344 310 439 358 507',
      'M976 12C952 125 775 164 715 312C680 415 593 429 577 506',
      'M879 15C924 133 826 225 758 294C708 344 690 439 642 507',
      'M-14 1300C142 1304 178 1435 222 1559C265 1670 362 1732 391 1786',
      'M13 1390C116 1424 105 1505 191 1603C241 1659 268 1734 295 1788',
      'M1014 1300C858 1304 822 1435 778 1559C735 1670 638 1732 609 1786',
      'M987 1390C884 1424 895 1505 809 1603C759 1659 732 1734 705 1788'
    ];
    const cloudDefs=`<defs>
      <filter id="W03-cloud" x="0" y="0" width="1000" height="1778" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB">
        <feTurbulence type="fractalNoise" baseFrequency=".014 .021" numOctaves="4" seed="27" result="noise"/>
        <feDisplacementMap in="SourceGraphic" in2="noise" scale="65" xChannelSelector="R" yChannelSelector="G" result="shape"/>
        <feGaussianBlur in="shape" stdDeviation="9" result="softshape"/>
        <feTurbulence type="fractalNoise" baseFrequency=".06 .09" numOctaves="3" seed="19" result="grain"/>
        <feComposite in="noise" in2="grain" operator="arithmetic" k1="1.5" k2="0" k3="0" k4="0"/>
        <feColorMatrix type="matrix" values="2 0 0 0 -.35 2.4 0 0 0 -.28 2.8 0 0 0 -.2 4.3 0 0 0 -.8"/>
        <feComponentTransfer result="colored"><feFuncA type="gamma" amplitude="1.5" exponent="1.4" offset="0"/></feComponentTransfer>
        <feComposite in="colored" in2="softshape" operator="in"/>
      </filter>
      <radialGradient id="W03-core-bloom"><stop stop-color="#fff9dc" stop-opacity=".65"/><stop offset=".08" stop-color="#fff5ba" stop-opacity=".5"/><stop offset=".35" stop-color="#6ee2fa" stop-opacity=".18"/><stop offset="1" stop-color="#36b9ff" stop-opacity="0"/></radialGradient>
      <radialGradient id="W03-glass" cx=".34" cy=".25" r=".75"><stop stop-color="#dfffff" stop-opacity=".24"/><stop offset=".26" stop-color="#3ccfff" stop-opacity=".1"/><stop offset=".72" stop-color="#092b40" stop-opacity=".08"/><stop offset=".95" stop-color="#77e3ff" stop-opacity=".6"/><stop offset="1" stop-color="#e8ffff" stop-opacity=".9"/></radialGradient>
      <radialGradient id="W03-cyan-bloom"><stop stop-color="#eeffff" stop-opacity=".85"/><stop offset=".06" stop-color="#b2f4ff" stop-opacity=".8"/><stop offset=".24" stop-color="#22bbff" stop-opacity=".55"/><stop offset=".55" stop-color="#1685e7" stop-opacity=".16"/><stop offset="1" stop-color="#1664c1" stop-opacity="0"/></radialGradient>
      <filter id="W03-aura" x="-100%" y="-100%" width="300%" height="300%"><feGaussianBlur stdDeviation="9"/></filter>
    </defs>`;
    const mist=g(veils.map((d,i)=>p(d,`stroke="#b5f4ff" stroke-width="${i%2?50:i>=4?150:115}" opacity="${i>=4?1:i%2?.25:.5}"`)).join('')+
      p('M302 1060C181 933 266 639 469 567M689 1087C831 951 747 644 545 589','stroke="#a6edff" stroke-width="75" opacity=".5"'),`fill="none" filter="url(#${id}-cloud)"`)+
      g(veils.map((d,i)=>p(d,`stroke="${i%2?'#81e7ff':'#b2eaff'}" stroke-width="${i%2?.8:2.4}" opacity=".3"`)).join(''),'fill="none"');
    const ribbon=p('M238 -15C237 62 146 111 95 163C38 221 60 273 11 322C45 244 17 218 63 154C108 92 202 56 238 -15Z M-4 1440C37 1501 112 1513 148 1593C181 1668 180 1732 251 1785C191 1757 156 1690 126 1620C93 1544 41 1542 -4 1440Z', 'fill="#75e6f2" fill-opacity=".13" stroke="#9bedff" stroke-opacity=".42" stroke-width=".8"')+
      p('M-11 1461C89 1596 101 1746 288 1770M-20 1402C57 1581 77 1722 198 1777M251 -10C216 61 108 111 63 213M203 -7C173 81 82 130 46 226','fill="none" stroke="#b1f7ff" stroke-width="1.2" opacity=".58"');
    // Hand-placed botanical sprays follow the reference's corner silhouettes.
    const spray=(x,y,a,s,shade)=>g(p('M0 45C7 15 -7 -24 1 -76','stroke="#8ea99d" stroke-width=".65" fill="none"')+
      [[1,-67],[-2,-47],[-2,-26],[1,-4],[3,18]].map(([px,py],i)=>leaf(px,py,-43-i*6,.56+i*.045,shade,1.55)+leaf(px,py+9,39+i*8,.49+i*.038,shade,1.45)).join('')+
      leaf(1,-73,4,.64,shade,1.3),`transform="translate(${x} ${y}) rotate(${a}) scale(${s})"`);
    const frameAnchors=[
      [32,87,44,1.1],[67,36,-28,1.15],[104,94,53,1.05],[21,190,-20,1.3],[60,241,-42,1.15],[115,179,23,.92],
      [42,303,-25,1.05],[11,363,-55,1.2],[166,39,44,.8],[8,23,24,1.4],[175,175,-18,.6],[202,221,24,.57],
      [4,1394,-30,1.1],[46,1420,-55,1.3],[24,1494,22,1.55],[91,1454,-22,1.2],[143,1504,-48,1.25],
      [44,1590,16,1.7],[112,1581,-44,1.34],[167,1625,17,1.2],[226,1645,-35,1.15],[75,1683,-31,1.55],
      [8,1716,20,1.7],[195,1742,-27,1.35],[269,1714,18,.92],[20,1314,-48,1.05],[65,1360,-32,1.05]
    ];
    const frameBotany=frameAnchors.map(([x,y,a,s],i)=>{
      const shade=['#659eab','#9fc6b4','#1e4b61','#b3dcce','#387287'][i%5];
      return spray(x,y,a,s,shade)+spray(1000-x,y,-a,s*.96,shade);
    }).join('');
    const ringBotany=[-152,-128,-111,-69,-50,-29,24,48,71,108,132,156].map((a,i)=>{
      const [x,y]=polar(444,a,500,831);
      return spray(x,y,a,.52,i%3===0?'#f5e3b4':'#b8e7d5');
    }).join('');
    const random=i=>{const v=Math.sin(i*127.1+19)*43758.5453;return v-Math.floor(v);};
    const dust=Array.from({length:3200},(_,i)=>{
      const lower=i%3!==0,t=random(i+1),side=i%2===0?1:-1;
      const y=lower?1290+t*488:t*500;
      const center=lower?80+t*225:75+t*205;
      const x=500+side*(500-center)+(random(i+11)-.5)*(lower?170:125);
      return c(x,y,.35+random(i+31)*(lower?1.8:1.1),`fill="${i%6===0?'#fff7d8':'#8fdffc'}" opacity="${.15+random(i+23)*.65}"`);
    }).join('');
    const celestial=c(500,831,244,'stroke-width=".7"')+c(500,831,213,'stroke-width=".5"')+
      p('M500 570L713 709L744 875L631 1066L369 1066L256 875L287 709Z M500 570L631 1066L287 709L744 875L369 1066L713 709L256 875Z','stroke-width=".6" opacity=".6"')+
      [[500,570],[713,709],[744,875],[631,1066],[369,1066],[256,875],[287,709]].map(([x,y])=>c(x,y,3,'fill="#c2faff"')).join('');
    const globe=g(c(500,1360,61,'fill="url(#W03-glass)"')+c(500,1360,59,'fill="#b7f8ff" filter="url(#W03-cloud)"')+
      p('M457 1340C465 1319 481 1310 498 1307M453 1350L455 1346M546 1387C537 1401 524 1409 511 1412','stroke="#edffff" stroke-width="2" opacity=".8"')+
      p('M452 1345C503 1371 463 1393 519 1405M472 1312C506 1347 540 1329 550 1361M473 1407C482 1373 531 1381 546 1334M447 1364C470 1387 508 1327 535 1317','stroke-width=".5" opacity=".75"')+
      p('M476 1311L479 1328L464 1345L483 1349L479 1365L491 1376L488 1392L504 1408M514 1313L521 1331L507 1343L515 1359L534 1371L523 1387L528 1405','stroke="#bdfaff" stroke-width="1.7"')+
      [[476,1311],[464,1345],[491,1376],[504,1408],[521,1331],[515,1359],[523,1387]].map(([x,y])=>c(x,y,2.2,'fill="#b5f8ff"')).join('')+
      c(500,1360,133,'fill="none" stroke="#96efff" stroke-width="3" stroke-dasharray="126 706" transform="rotate(32 500 1360)"')+
      p('M392 1438Q415 1474 457 1486M470 1490Q512 1495 547 1484M572 1473L578 1469','stroke="#d1fbff" stroke-width="1.2"')+
      p('M389 1429C418 1486 506 1518 581 1459M412 1458C447 1487 504 1500 543 1487','fill="none" stroke="#7deeff" stroke-width="15" filter="url(#W03-cloud)"'),'fill="none" stroke="#90eaff"');
    const rootLight=c(500,1083,94,'fill="url(#W03-cyan-bloom)" stroke="none"')+
      p('M500 1011L518 1062L552 1083L518 1102L500 1160L483 1101L448 1083L483 1063Z M500 1038L509 1071L527 1083L509 1095L500 1128L491 1095L473 1083L491 1071Z','fill="none" stroke="#c6faff" stroke-width="1.1"')+
      p('M483 969C469 1018 486 1033 455 1089C447 1104 441 1119 443 1130M517 969C531 1018 514 1033 545 1089C553 1104 559 1119 557 1130','fill="none" stroke="#b5f6ff" stroke-width="1"');
    const glints=[[185,725],[815,725],[280,613],[720,613],[200,1035],[800,1035],[245,746],[755,746],[301,916],[699,916]].map(([x,y],i)=>c(x,y,i%2?29:23,'fill="url(#W03-cyan-bloom)" stroke="none"')+c(x,y,i<4?14:8,'fill="none" stroke="#d9ffff" stroke-width="1.2"')+c(x,y,2.8,'fill="#e7fcff" stroke="none"')+ray(x,y,12,'#bcf8ff')).join('');
    return cloudDefs+c(500,860,490,`fill="url(#${id}-ambient)"`)+g(mist+dust+ribbon+g(ribbon,'transform="translate(1000 0) scale(-1 1)"'),'data-layer="light-veils"')+particles(id,1000,1778,520)+
      layer(id,'constellation',celestial,{color:'#9be9f3',width:.7,glow:.5})+
      layer(id,'outer-ring',rings+ringGlints,{color:gold,width:1,glow:.62})+g(ringAura,'data-layer="ring-atmosphere"')+
      layer(id,'ring-light',c(500,831,433,'stroke-dasharray="46 600"'),{color:blue,width:2,glow:.65,motion:'ring'})+
      layer(id,'rune-ring',ringsDetail,{color:gold,width:.8,glow:.28,motion:'reveal'})+
      layer(id,'orbits',orbits,{color:'#faeccb',width:1.7,glow:.66,motion:'reveal'})+
      layer(id,'moon',crown,{color:gold,width:1.15,glow:.5,fixed:true})+
      layer(id,'garden',vines+borderLeaves+chains,{color:'#b3e6d3',width:1,glow:.25,motion:'reveal'})+
      c(500,864,265,'fill="url(#W03-core-bloom)"')+tree+gardenLeaves+
      layer(id,'center-core',jewels+globe,{color:gold,width:1.25,glow:.46})+
      layer(id,'orbit-lights',glints+rootLight+c(500,170,63,'fill="url(#W03-cyan-bloom)" stroke="none"'),{color:blue,width:.7,glow:.8})+
      layer(id,'stars',fixedStars,{color:'#fff7dc',width:.7,glow:1,fixed:true})+
      layer(id,'tree-medallion',c(500,864,20,'fill="#e7c974"')+c(500,864,16,'stroke-width="1.4"')+
        Array.from({length:8},(_,i)=>p(`M${polar(3,i*45,500,864).join(' ')}L${polar(15,i*45,500,864).join(' ')}`,'stroke-width="1.1"')).join('')+c(500,864,3.2,'fill="#ffffee"'),{color:'#fffdeb',width:1.4,glow:.75,fixed:true})+
      g(frameBotany+ringBotany,'data-layer="foreground-foliage" data-motion="reveal"')+
      layer(id,'flowers',[[166,414,.8,-14],[834,414,.8,14],[113,1191,.7,28],[887,1191,.7,-28],[122,338,.66,12],[878,338,.66,-12]].map(([x,y,s,a])=>butterfly(x,y,s,a)).join(''),{color:blue,width:1,glow:.6});
  }
  // Coordinates below are authored against the native ~180px reference crops.
  // Each motif has its own construction, not a recolored shared star template.
  function collectionC(id) {
    const colors={C01:'#ff879a',C02:'#7ad2ff',C04:'#cbdcff',C05:'#d49bff',C06:'#56e9ff',C07:'#ffe2a2',C08:'#b4b4bc',C09:'#91bdff',C10:'#81b6ff',C11:'#ff3b57',C12:'#a8ffd0',C13:'#ffce7f',C14:'#c4d8ff',C15:'#d7a7ff',C16:'#f5d497',C18:'#dfdeb9',C22:'#badbff',C27:'#e5e9f7'};
    Object.assign(colors,{C17:'#141319',C19:'#712b0d',C20:'#dfc3ff',C21:'#ff354b',C23:'#87deff',C24:'#b88aff',C25:'#abffbc',C26:'#ffdf9e',C28:'#73d8ff',C29:'#cee9dc',C30:'#b2d7ff'});
    const color=colors[id];
    const ring=r=>c(90,90,r),turn=(body,a,x=90,y=90)=>g(body,`transform="rotate(${a} ${x} ${y})"`);
    const polygon=(r,count,step=1,a=-90,x=90,y=90)=>p(closed(Array.from({length:count},(_,i)=>polar(r,a+i*step*360/count,x,y))));
    const bloom={C01:.22,C02:.65,C04:.48,C05:.78,C06:.85,C07:1.1,C08:.035,C09:.85,C10:.9,C11:.55,C12:.55,C13:.8,C14:.72,C15:.68,C16:.32,C18:.62,C22:.65,C27:.48,C17:0,C19:0,C20:.8,C21:.55,C23:1,C24:.8,C25:.55,C26:.65,C28:1,C29:.55,C30:.85}[id];
    const stroke=(name,body,width=.45,glow=.55,fixed=false)=>{
      const far=g(body.replace(/\sdata-[\w-]+="[^"]*"/g,''),`aria-hidden="true" data-light-role="far" opacity="${n(glow*bloom)}" filter="url(#${id}-far)"`);
      return layer(id,name,body,{color,width,glow,fixed}).replace('<g class="halo"',far+'<g class="halo"');
    };
    // The source glyphs are partly unresolved at 180px. These small handwritten
    // strokes preserve the script scale and placement; they are not a transcription.
    const script=(r,count=96,offset=0)=>g(Array.from({length:count},(_,i)=>{
      const a=-90+360*(i+.5)/count+offset;
      const forms=['M-1 1L-.4-.9L.3.1L1-.8M-.4-.9V-1.8','M-1 0Q-.4 1.4 0-.1L.6-1L1 .5','M-1 .7L-.7-.8L.2-.4L.7-1.1M.2-.4V1.4','M-1-.3L-.5.8L.2-.8L.6.6L1.3.2','M-.9.8Q-.2-1.5 .4-.6L.1.5L1.1.7','M-1 0L-.2 0L.2-1.4L.4 1.1L1.2-.2'];
      return g(p(forms[(i*5+Math.floor(i/8))%6]),`transform="translate(${polar(r,a,90,90).join(' ')}) rotate(${a+90})"`);
    }).join(''),'stroke-width=".3"');
    const cardinal=(r,size=2)=>[0,90,180,270].map(a=>{
      const [x,y]=polar(r,a,90,90);return c(x,y,1.7)+p(`M${x-size} ${y}H${x+size}M${x} ${y-size}V${y+size}`);
    }).join('');
    let outer='',runes='',geometry='',core='',ornament='',lights='';
    const octagram=p('M90 28L135 135L27 90L135 45L90 153L45 45L153 90L45 135Z','stroke-width=".42"');
    const crystal=ring(20)+polygon(21,6)+polygon(19,3)+polygon(19,3,1,90)+polygon(16,6)+polygon(10,6,1,0)+ring(5);
    const ellipse=(rx,ry,x=90,y=90)=>p(`M${x-rx} ${y}C${x-rx} ${n(y-ry*.5523)} ${n(x-rx*.5523)} ${y-ry} ${x} ${y-ry}C${n(x+rx*.5523)} ${y-ry} ${x+rx} ${n(y-ry*.5523)} ${x+rx} ${y}C${x+rx} ${n(y+ry*.5523)} ${n(x+rx*.5523)} ${y+ry} ${x} ${y+ry}C${n(x-rx*.5523)} ${y+ry} ${x-rx} ${n(y+ry*.5523)} ${x-rx} ${y}Z`,'data-orbital-path="true"');
    if(['C01','C02','C04','C08'].includes(id)) {
      outer=[84,80,72,67,64].map(ring).join('');runes=script(77,id==='C08'?84:117)+cardinal(78,2.5);
      geometry=octagram;core=crystal;ornament=ring(22);
      if(id==='C04'){
        geometry+=polygon(60,8)+ring(57)+polygon(27,8,3);
        ornament+=[0,90,180,270].map(a=>turn(p('M90 7L93 13L90 18L87 13Z M90 16V28','stroke-width=".6"'),a)).join('')+ticks(90,90,58,64,1.2);
      }
      lights=id==='C08'?'':ring(83)+ring(66); // Minimal reference intentionally has almost no bloom.
    }
    if(id==='C05') {
      outer=[84,80,69,65].map(ring).join('');runes=script(76,68)+cardinal(80,3);
      geometry=p('M90 24L105 75L156 90L105 105L90 156L75 105L24 90L75 75Z','opacity=".4"')+ring(53);
      ornament=[0,52,-52].map(a=>turn(ellipse(65,28),a)).join('');
      core=crystal+polygon(28,4)+polygon(27,4,1,0);
      lights=[[90,56],[55,71],[55,112],[121,73],[129,112]].map(([x,y])=>star(x,y,3.2,'#f4ccff')+c(x,y,3,`fill="url(#${id}-spark)" stroke="none"`)).join('')+ring(82);
    }
    if(id==='C06') {
      outer=[85,81,73,69,66].map(ring).join('');runes=script(77,128)+script(64,128);
      geometry=g(octagram,'opacity=".4"')+polygon(49,8)+p('M90 28V152M28 90H152','stroke-width=".4"');
      ornament=[43,44,47,49].map(ring).join('')+ticks(90,90,45,144,1.8)+
        Array.from({length:4},(_,i)=>turn(p('M90 43Q63 48 63 69M43 90Q49 73 63 69','stroke-width=".2" opacity=".35"'),i*90)).join('');
      core=ring(22)+polygon(17,3)+polygon(17,3,1,90)+ring(15)+ring(8.5);
      lights=ring(45)+p('M46 66A49 49 0 0 1 90 41M42 91A48 48 0 0 0 64 133M102 137A49 49 0 0 0 138 85','stroke-width="2" opacity=".65"')+
        [0,90,180,270].map(a=>turn(p('M90 16V39M87 25H93','stroke-width=".6"'),a)).join('');
    }
    if(id==='C07') {
      outer=[84,80,72,68].map(ring).join('');runes=script(77,111)+cardinal(80,3);
      geometry=octagram+polygon(31,8,3)+polygon(43,4)+polygon(43,4,1,0);
      ornament=ellipse(62,15)+[45,-45].map(a=>turn(ellipse(65,23),a)).join('');
      core=crystal+ring(14)+c(90,90,26,`fill="url(#${id}-spark)" stroke="none"`)+
        p('M90 49V131M64 90H117','stroke="#fffdf3" stroke-width=".55"')+c(90,90,3.7,'fill="#fffdeb" stroke="none"');
      lights=[[90,28],[137,49],[152,90],[138,132],[90,152],[42,132],[28,90],[42,49]].map(([x,y])=>
        c(x,y,6,`fill="url(#${id}-spark)" stroke="none"`)+c(x,y,2.2,'fill="#fffef4" stroke="#fffef4"')+star(x,y,4,'#fffef4')).join('');
    }
    if(id==='C09') {
      outer=[84,80,71,67,61].map(ring).join('');runes=script(77,83)+cardinal(80,3);
      geometry=polygon(61,5,2)+polygon(32,3)+polygon(32,3,1,90)+ring(33)+ring(36);
      ornament=[0,60,120].map(a=>turn(ellipse(28,60),a)).join('');core=crystal;
      lights=[[90,34],[146,75],[126,137],[52,137],[33,75]].map(([x,y],i)=>g(
        c(x,y,8,`fill="#155dcc"`)+c(x,y,6.2,'stroke="#effaff" stroke-width=".85"')+c(x,y,4,'fill="#dcf6ff"')+c(x,y,13,`fill="url(#${id}-spark)" stroke="none" opacity=".45"`),`data-satellite="${i}"`)).join('');
    }
    if(id==='C10') {
      outer=[83,79,72,67,58,48].map(ring).join('');runes=script(76,132)+script(63,100)+script(53,84);
      geometry=polygon(62,6)+polygon(61,6,5)+polygon(44,6,1,0)+polygon(42,3)+polygon(42,3,1,90)+polygon(29,6,1,0)+
        p('M67 64L90 51L114 65V116L90 129L67 116Z M67 64L90 78L114 65M90 78V129M67 116L90 101L114 116','stroke-width=".35"');
      core=crystal+ring(28)+ring(25);
      const nodes=[[63,33,15],[22,86,15],[48,153,14],[92,153,18],[147,130,15],[150,80,18]];
      const sigils=[
        'M0-9L8 5H-8Z M-8-5H8L0 9Z M-3 0H3M0-3V3',
        'M0-10V10M-9-5L0 1L9-5M-9-5Q-5 4 0 1Q5 4 9-5M-5 5L0 10L5 5',
        'M-6-5H6V5H-6Z M-2-8H2V8H-2Z M-9 0H9M0-10V10',
        'M0-1C-10-13-14 5-3 5C-9 14 9 14 3 5C14 5 10-13 0-1Z M-3-2L3-2L0 3Z',
        'M8-7C-1-15-14-4-8 5C-4 12 11 9 8 1C6-6-5-7-5 0C-5 5 3 5 3 0C3-3-1-3-1 0',
        'M0-12L4-3L12 0L4 3L0 12L-4 3L-12 0L-4-3Z M-6-6L6 6M-6 6L6-6'
      ];
      ornament=nodes.map(([x,y,r],i)=>g(c(x,y,r,`fill="#104580" fill-opacity=".8"`)+c(x,y,r-2)+c(x,y,r-5)+
        g(p(sigils[i],'data-sigil="true" stroke-width=".65"'),`transform="translate(${x} ${y})"`)+
        g(ticks(x,y,r-1,24,1),'stroke-width=".25"'),`data-satellite="${i}"`)).join('');
      lights=nodes.map(([x,y])=>c(x,y,16,`fill="url(#${id}-spark)" stroke="none" opacity=".28"`)).join('')+
        p('M56 39L84 81M136 86L99 91M92 137V115M40 98L67 110','stroke-width=".85"');
    }
    if(id==='C11') {
      outer=[85,82,78,62,58,51,35,33].map(ring).join('');runes=script(80,133);
      geometry=Array.from({length:4},(_,i)=>turn(p('M90 30L95 75L90 87L85 75Z M52 52L80 72L86 86L73 80Z','stroke-width=".55"')+p('M90 35V80M57 57L80 80','stroke-width=".3" opacity=".5"'),i*90)).join('')+
        Array.from({length:8},(_,i)=>turn(p('M90 20Q113 17 139 42Q136 59 146 76Q163 52 139 42Q124 40 109 26','stroke-width=".4"'),i*45)).join('');
      core=Array.from({length:8},(_,i)=>turn(p('M83 64C82 58 98 58 97 64L96 71Q90 69 84 71Z',`data-inner-arch="${i}" stroke-width=".5"`)+
        p('M85 67Q90 60 95 67M86 72Q82 78 86 82M94 72Q98 78 94 82','stroke-width=".28" opacity=".55"'),i*45+22.5)).join('')+
        ring(14)+polygon(15,8,3)+p('M72 90Q90 74 108 90Q90 106 72 90Z','stroke-width=".8"')+ring(7)+c(90,90,4,'stroke="#ffb2b4" stroke-width="1.1"')+c(90,90,2.2,'fill="#a81c3b" stroke="none"');
      ornament=Array.from({length:8},(_,i)=>turn(p('M90 14C81 17 82 26 90 30C98 26 99 17 90 14Z M90 18C85 17 83 22 87 25C92 29 99 22 94 21C89 20 85 26 91 26M86 29Q77 33 78 25M94 29Q103 33 102 25','stroke-width=".45"'),i*45)).join('')+
        [0,90,180,270].map(a=>turn(p('M90 5L95 11L90 18L85 11Z M90 7V15'),a)).join('');
      lights=ring(83)+ring(10)+c(90,90,11,`fill="url(#${id}-spark)" stroke="none" opacity=".7"`);
    }
    if(id==='C12') {
      outer=[84,80,70,67,63].map(ring).join('');runes=script(76,103)+cardinal(79,4);
      geometry=Array.from({length:8},(_,i)=>turn(p('M90 90C73 76 74 49 90 27C106 49 107 76 90 90Z',`data-petal="${i}"`),i*45)).join('');
      core=Array.from({length:8},(_,i)=>turn(p('M90 90C83 86 81 79 90 65C99 79 97 86 90 90Z M90 65C85 62 85 58 90 53C95 58 95 62 90 65Z','stroke-width=".55"'),i*45)).join('')+
        p(closed(Array.from({length:8},(_,i)=>polar(i%2?61:26,-90+i*45,90,90))))+polygon(8,8,3)+ring(3);
      ornament=turn(p('M90 4L93 16L90 12L87 16Z M90 11V23'),0)+turn(p('M90 4L93 16L90 12L87 16Z M90 11V23'),180);
      lights=ring(68)+ring(80);
    }
    if(id==='C13') {
      outer=[83,68,66,64,27,25,23].map(ring).join('');
      runes=Array.from({length:16},(_,i)=>turn(p('M90 8V14M88.5 11H91.5'),i*22.5+11.25)).join('');
      geometry=Array.from({length:16},(_,i)=>{
        const long=i%4===0,tip=long?87:i%2?43:57;
        return turn(p(closed([[90,90-tip],[95,64],[90,67],[85,64]]),`data-solar-ray="${long?'long':'short'}" fill="${color}" fill-opacity=".1"`),i*22.5);
      }).join('')+Array.from({length:8},(_,i)=>turn(p('M90 60L93 64L90 66L87 64Z','stroke-width=".25"'),i*45+11.25)).join('');
      core=Array.from({length:8},(_,i)=>turn(p('M90 70Q90 82 98 82Q90 82 90 90Q90 82 82 82Q90 82 90 70Z','stroke-width=".3"'),i*45)).join('')+ring(16)+ring(8.5)+ring(2.4);
      ornament=Array.from({length:8},(_,i)=>turn(p('M90 22V28M87.5 24H92.5'),i*45)).join('');
      lights=ring(64)+ring(27)+c(90,90,32,`fill="url(#${id}-point)" opacity=".19" stroke="none"`);
    }
    if(id==='C14') {
      outer=[84,80,73,69].map(ring).join('');runes=script(77,116)+cardinal(80,4);
      const disc=(x,y,r,d)=>g(c(x,y,r,'stroke-width=".35"')+p(d,`fill="${color}" fill-opacity=".86"`),'data-lunar-disc="true"');
      geometry=disc(98,61,28,'M106 34C77 24 65 66 88 81C99 89 113 85 121 75C105 87 86 79 86 60C86 46 95 37 106 34Z')+
        disc(43,78,17,'M50 67C36 60 24 78 35 87C41 92 50 85 48 79C41 86 34 78 39 72C43 68 47 69 50 67Z')+
        disc(87,122,29,'M75 109C65 111 59 125 67 132C73 138 82 132 82 125C75 134 63 120 75 109Z')+
        disc(136,99,18,'M132 90C146 84 153 103 142 109C136 112 133 108 131 104C141 109 144 99 139 95C137 93 134 94 132 90Z');
      core=p('M104 35C82 37 73 64 91 78M101 35C77 39 71 61 85 77M98 35C72 43 73 67 83 75','stroke-width=".28"')+
        star(116,54,1.3,color)+star(110,149,.7,color);
      ornament=p('M22 116C9 112 10 133 23 133C32 133 34 121 25 120C19 119 17 125 22 128C27 130 29 124 24 123M153 114C169 107 175 121 161 127C153 130 150 142 145 147M157 116L167 120L156 127L158 117Z M89 159Q96 159 94 166L90 170L86 166Q83 159 89 159Z','stroke-width=".45"')+
        p('M31 116L37 111L44 108L49 105M124 126L128 124L134 128L140 134M31 96L35 103L31 113M53 90L61 86L67 89M119 81L125 85L131 84M50 50L57 45L59 40M81 35L84 27M89 20V31M133 60L139 68L145 70M112 152L112 146M96 151L94 146','stroke-width=".27"');
      lights=p('M106 34C77 24 65 66 88 81C99 89 113 85 121 75','stroke-width=".75"')+c(90,160,2.5)+c(90,160,1);
    }
    if(id==='C15') {
      outer=[83,80,77,68,66].map(ring).join('');runes=script(74,104);
      geometry=p('M90 22L105 75L158 90L105 105L90 158L75 105L22 90L75 75Z','stroke-width=".65"')+
        p('M54 54L90 71L126 54L109 90L126 126L90 109L54 126L71 90Z','stroke-width=".6"')+
        c(90,90,36,'stroke-width=".24"');
      core=p('M90 70L95 83L110 90L95 96L90 110L84 96L70 90L84 83Z M78 78L90 84L102 78L96 90L102 102L90 96L78 102L84 90Z')+polygon(6,4)+c(90,90,1,'fill="#f6dcff"');
      ornament=[0,90,180,270].map(a=>turn(p('M90 1L95 17L90 22L85 17Z M90 4L92 16L90 19L88 16Z M90 12V28',`data-compass-point="${a}"`),a)).join('')+
        Array.from({length:8},(_,i)=>turn(p('M90 53Q93 55 97 55','stroke-width=".26"'),i*45+22.5)).join('');
      lights=ring(79)+ring(67);
    }
    if(id==='C16') {
      outer=[86,82,66,62,49,46,27,22,11,7].map(ring).join('');
      const roman={I:'M-1-4H1M0-4V4M-1 4H1',V:'M-2-4L0 4L2-4M-3-4H-1M1-4H3',X:'M-2-4L2 4M2-4L-2 4M-3-4H-1M1-4H3M-3 4H-1M1 4H3'};
      const values=['XII','I','II','III','IV','V','VI','VII','VIII','IX','X','XI'];
      runes=Array.from({length:24},(_,i)=>{
        const text=values[i%12],a=i*15-90;
        return g([...text].map((ch,j)=>g(p(roman[ch]),`transform="translate(${(j-(text.length-1)/2)*3.5} 0)"`)).join(''),`data-numeral="${i}" transform="translate(${polar(74,a,90,90).join(' ')}) rotate(${a+90})"`);
      }).join('')+Array.from({length:24},(_,i)=>turn(p('M90 7V22','stroke-width=".3"'),i*15+7.5)).join('');
      geometry=Array.from({length:4},(_,i)=>turn(p('M90 30L96 70L91 86L84 70Z M90 36V81'),i*90)).join('')+
        Array.from({length:4},(_,i)=>turn(p('M90 58L97 70L90 68L83 70Z M90 71V78'),i*90+45)).join('')+ring(18)+ring(14)+ring(10)+
        Array.from({length:8},(_,i)=>turn(p('M87 74H93V78H87Z','stroke-width=".3"'),i*45)).join('')+
        Array.from({length:8},(_,i)=>turn(p('M90 24V57M88 34H92','stroke-width=".3"'),i*45+25)).join('');
      const cog=(x,y,r,teeth)=>p(closed(Array.from({length:teeth*4},(_,j)=>polar(r*(j%4===0||j%4===3?.8:1),j*360/(teeth*4),x,y))), 'stroke-width=".35"')+c(x,y,r*.5)+c(x,y,r*.2)+Array.from({length:5},(_,j)=>{const a=polar(r*.2,j*72,x,y),b=polar(r*.5,j*72,x,y);return p(`M${a.join(' ')}L${b.join(' ')}`,'stroke-width=".25"');}).join('');
      core=cog(67,124,14,10)+cog(58,111,10,9)+cog(82,133,10,9)+cog(98,125,7,8)+ring(4);
      ornament=p('M90 30V52M90 127V151M30 90H50M128 90H151','stroke-width=".6"');lights=ring(84)+ring(47);
    }
    if(id==='C18') {
      outer=[85,80,74,64,58].map(ring).join('');runes=script(77,120)+script(61,100);
      geometry=turn(p('M64 64H116V116H64Z','data-element-square="1"'),0)+turn(p('M64 64H116V116H64Z','data-element-square="2"'),30)+
        p('M90 38L137 90L90 141L42 90Z M90 51L128 110L53 110Z M53 70H128L90 131Z M64 64L116 116M64 116L116 64','stroke-width=".35"')+
        polygon(24,8);
      const sigils=[
        ['fire',90,33,'#ff6256','M0-16L4-5L6-9C6-2 11-2 10 5C9 16-10 16-11 5L-10-5L-6 0C-7-8-1-9 0-16Z M0-7C4-1 7 1 5 6C3 11-4 10-5 5C-6 1-1-1 0-7Z'],
        ['water',32,90,'#64baff','M0-15C-2-8-12-1-10 7C-8 17 8 17 10 7C12-1 2-8 0-15Z M0-10C-3-3-7 1-6 6C-5 12 5 12 6 6C7 1 3-3 0-10Z'],
        ['nature',149,92,'#a0ed8c','M0 14V-3M0 8C-9 8-14 1-14-7C-5-7-1-2 0 8Z M0 5C-5-1-4-11 0-17C5-10 5-1 0 5Z M0 8C11 6 15-2 14-9C5-7 1 0 0 8Z M0 8L-10-4M0 8L11-5'],
        ['earth',90,151,'#ffdc70','M0-18Q3-5 17-1Q4 4 0 21Q-4 4-17-1Q-3-5 0-18Z M0-11L12 10H-12Z']
      ];
      core=sigils.map(([kind,x,y,col,d])=>g(c(x,y,22,`fill="#041119" stroke="${col}"`)+c(x,y,18.3,`stroke="${col}"`)+p(`M${x} ${y-26}V${y-14}M${x} ${y+14}V${y+26}M${x-26} ${y}H${x-13}M${x+13} ${y}H${x+26}`,`stroke="${col}" stroke-width=".35"`)+g(p(d),`transform="translate(${x} ${y})" stroke="${col}" stroke-width=".85"`),`data-element="${kind}"`)).join('');
      ornament=Array.from({length:4},(_,i)=>{
        const col=['#ff685f','#a3f18b','#ffe47c','#6ca9ff'][i];
        return turn(p('M90 5A85 85 0 0 1 175 90',`stroke="${col}" stroke-width=".85"`)+p('M90 10A80 80 0 0 1 170 90',`stroke="${col}" stroke-width=".35"`),i*90);
      }).join('');lights=core.replace(/\sdata-element="[^"]*"/g,'');
    }
    if(id==='C22') {
      outer=[85,78,72,59,54,48].map(ring).join('');runes=script(57,105)+script(70,120)+cardinal(79,4);
      geometry=Array.from({length:4},(_,i)=>turn(p('M90 5L95 18L90 29L85 18Z M90 10L92 18L90 23L88 18Z M90 30V46'),i*90)).join('');
      // Each diagonal is nine independently closed chain links. Alternating
      // narrow links preserve the interlocked, edge-on rhythm of the reference.
      ornament=Array.from({length:4},(_,arm)=>Array.from({length:9},(_,j)=>{
        const x=73-j*8,y=73-j*8,w=j%2?1.2:2.8;
        const d=`M${-w} -4.1C${-w} -7 ${w} -7 ${w} -4.1V4.1C${w} 7 ${-w} 7 ${-w} 4.1Z`;
        return turn(g(p(d,`data-chain-link="${arm}-${j}" fill="#061326" stroke-width=".8"`)+p(`M${-w+.55} -4Q${-w+.55} -5.4 0 -5.5M${w-.4} 1V4`,'stroke-width=".28"'),`transform="translate(${x} ${y}) rotate(-45)"`),arm*90);
      }).join('')).join('');
      core=p('M78 87V81C78 65 102 65 102 81V87H98V81C98 70 82 70 82 81V87Z','fill="#102645" stroke-width=".85"')+
        p('M76 87H104Q107 87 107 90V109Q107 112 104 112H76Q73 112 73 109V90Q73 87 76 87Z','fill="#b3d8fb" fill-opacity=".4" stroke-width="1"')+
        p('M76 90H104V109H76Z','stroke-width=".3"')+
        p('M88 101C82 97 86 91 90 92C95 91 98 98 92 101L94 107H86Z','fill="#020a18" stroke-width=".4"');
      lights=p('M77 88H104M75 90V109M79 80C79 67 100 67 101 80','stroke-width=".55"');
    }
    if(id==='C27') {
      outer=[85,82,73,68,50].map(ring).join('');runes=script(78,133)+cardinal(80,4);
      geometry=p('M90 26L144 90L90 154L36 90Z M24 90L90 39L156 90L90 143Z M90 26L117 90L90 154L63 90Z M24 90L90 67L156 90L90 113Z')+
        p('M90 9V171M9 90H171M78 51H102','stroke-width=".45"')+
        p('M90 39V69M90 112V142M78 51H102','stroke-width="1.8"')+
        c(90,90,49,'stroke-width=".32"')+Array.from({length:4},(_,i)=>turn(p('M90 48Q103 58 108 66L120 68','stroke-width=".3"'),i*90)).join('');
      core=g(p('M24 90Q90 50 156 90Q90 130 24 90Z','fill="#08101c" stroke-width=".65"')+
        c(90,90,22,'stroke-width=".6"')+c(90,90,16,'fill="#e7ebf8" stroke-width=".5"')+
        c(90,90,5,'fill="#18233c" stroke="none"'),'data-eye="true"');
      ornament=p('M90 3V18M90 162V177M3 90H18M162 90H177')+c(90,16,3)+c(90,165,3)+c(16,90,2)+c(165,90,2);
      lights=p('M24 90Q90 50 156 90Q90 130 24 90Z','stroke-width=".65"')+c(90,90,16,'stroke-width=".5"');
    }
    if(id==='C17') {
      outer=[85,81,76,68,64,59].map(ring).join('');runes=script(73,130);
      geometry=p('M90 24L151 58L130 128L50 128L29 58Z M90 25L50 128L151 58H29L130 128Z M90 26V151M29 58L130 128M151 58L50 128','stroke-width=".6"')+ring(45);
      core=ring(23)+ring(20)+c(90,90,15,'stroke-width="3.2"')+ring(8)+Array.from({length:6},(_,i)=>turn(p('M90 75V82',`data-alchemy-spoke="${i}" stroke-width="2.3"`),i*60)).join('');
      const marks=[[90,47,12,'M0-7C-9-7-10 4-4 6V10H4V6C10 4 9-7 0-7Z M-3-3H3V3H-3Z'],[48,113,10,'M-5 6L5-6M-6-1L1 6M-1-6L6 1M-3 0L0-3L3 0L0 3Z'],[132,113,10,'M-5 3C-8-2-2-7 2-5C7-2 3 4-1 1M0 0L6 7M3 7H8'],[90,152,14,'M-8 8V-1C-8-14 8-14 8-1V8H3V-1C3-7-3-7-3-1V8Z']];
      ornament=marks.map(([x,y,r,d])=>g(c(x,y,r,'fill="#faf8ef" stroke-width="1"')+c(x,y,r-2)+g(p(d,'stroke-width="1.5"'),`transform="translate(${x} ${y})"`),'data-alchemy-seal="true"')).join('')+p('M90 31V11M86 12Q85 18 90 18Q95 18 94 12M87 11V15M93 11V15M15 87H24M19 83V93M158 90H170M163 85V95','stroke-width="1"');
      lights=ring(84);
    }
    if(id==='C19') {
      outer=[85,80,70,63,60,44,36].map(ring).join('');runes=script(76,137)+script(66,117);
      geometry=polygon(62,5,2)+polygon(42,5,2,90)+ring(25)+p('M35 63L50 136L145 63L90 151Z M24 81H156M90 10V167');
      core=p('M86 20L90 3L95 20L94 36C104 38 110 50 110 66C127 69 133 68 143 75L139 86L111 84C109 103 100 111 94 117V129C109 130 112 144 101 151L96 151L95 162L90 173L86 162L85 153C70 151 69 137 77 132L84 129V111C70 110 65 101 69 93C71 88 77 85 83 83V77L43 79L40 88L31 83L31 73L40 67L45 72H84V56C65 58 54 65 52 77C45 62 56 42 84 40Z','fill="#763015" stroke-width=".9"')+p('M95 57C105 61 110 70 109 82C108 92 101 98 94 102V89C100 87 100 82 94 82Z M84 96Q77 100 84 104Z M95 137Q103 140 95 144Z','fill="#fff1bc" stroke-width=".5"');
      ornament=[[20,80,14,'M-9-1L9 6L0-1L8-6M-9-1L0-7'],[159,80,14,'M-7 5L6-6M-7-3Q1-12 6-6Q11 1 1 7Q-7 10-7-3Z M-5-2H5M0-7V7'],[39,118,9,'M-5-4Q0-9 5-4L4 4L0 8L-4 4Z'],[142,119,9,'M-5-4Q0-9 5-4L4 4L0 8L-4 4Z']].map(([x,y,r,d])=>c(x,y,r,'fill="#ffecad" stroke-width="1.2"')+c(x,y,r-2)+g(p(d,'stroke-width="1.2"'),`transform="translate(${x} ${y})"`)).join('');lights=ring(83);
    }
    if(id==='C20') {
      outer=[83,70,67,64].map(ring).join('');runes=script(76,114);
      geometry=p('M90 23V37M90 54V58M28 117L73 96M107 96L152 117M34 134L74 116M106 116L146 134M90 155V173','stroke-width=".32"');
      // Separate curved vanes follow the descending shoulder, with dark gaps between tips.
      const feathers=[
        'M57 63C43 57 25 55 11 43C17 54 34 61 53 65Q57 66 57 63Z',
        'M60 68C44 63 28 62 18 54C23 63 39 68 57 70Q61 71 60 68Z',
        'M63 73C50 69 34 68 25 64C30 71 45 75 61 75Q65 76 63 73Z',
        'M66 79C53 75 41 76 31 72C38 80 51 82 64 81Q68 82 66 79Z',
        'M68 84C57 81 49 82 38 80C44 86 56 88 67 87Q70 87 68 84Z',
        'M70 88C59 86 53 88 45 87C50 92 60 92 69 91Q72 91 70 88Z',
        'M72 92C64 91 58 94 51 94C58 98 65 96 72 94Z',
        'M74 96C67 97 63 100 57 100C63 103 70 100 74 98Z',
        'M77 99C71 102 68 105 63 106C68 108 75 104 77 101Z',
        'M79 102C75 105 73 109 69 111C74 112 79 108 80 104Z',
        'M82 104C78 108 78 112 75 114C80 113 82 110 83 106Z',
        'M85 105C83 109 83 113 81 115C85 113 87 110 87 107Z'
      ];
      const wing=feathers.map((d,i)=>p(d,`data-feather="${i}" fill="#d9c7ff" fill-opacity=".76" stroke-width=".45"`)).join('')+
        p('M12 44C29 57 52 55 62 67C71 77 66 89 83 102C70 95 65 87 63 79C60 69 52 65 44 62','fill="none" stroke-width="1.05"')+
        p('M54 66Q60 66 61 70M59 71Q64 71 65 76M63 77Q67 77 68 82M66 83L70 86M70 89L73 92M74 96L78 100','stroke-width=".8"');
      const sideSeal=(x)=>g(g(p('M-7-7H7V7H-7Z M-5-5H5V5H-5Z','fill="#100d26" stroke-width=".55"'),`transform="translate(${x} 117) rotate(22)"`)+
        c(x,117,4.3)+p(`M${x+1} 113C${x-4} 112 ${x-5} 119 ${x} 120C${x+4} 120 ${x+4} 115 ${x+1} 115L${x-1} 118M${x-2} 121L${x+2} 113`,'stroke-width=".65"'),'data-angel-medallion="true"');
      ornament=wing+g(wing,'transform="translate(180 0) scale(-1 1)"')+sideSeal(23)+sideSeal(157)+
        p('M90 6C90 10 82 12 86 18Q90 23 94 18C98 12 90 10 90 6Z M90 8V25M90 160C80 163 88 168 90 172C92 168 100 163 90 160Z M90 157V176M12 89H20M16 86V94M160 90H169M164 86V95','stroke-width=".65"');
      core=c(90,91,18,'fill="#100d26" stroke-width=".85"')+c(90,91,15.5)+
        p('M90 58L97 73L90 70L83 73Z M90 61V71M87 70L90 64L93 70 M90 76C87 80 90 82 84 84C77 85 82 90 78 95C81 95 82 93 84 96C87 100 83 102 86 104C91 105 90 99 94 100C100 103 103 96 99 94C94 91 99 86 94 85C91 85 93 78 90 76Z M89 80V88M78 91L85 89M95 88L102 87M91 97V107 M84 107L86 115L91 117L95 112L99 109M83 108L81 116L86 119M97 109L99 113L96 120L94 117', 'data-angel-seal="true" stroke-width=".7"')+
        c(88,91,3.8)+c(88,91,1.7)+c(97,94,2.1)+c(90,137,17)+c(90,137,14.4)+c(90,137,8.3)+c(90,137,5.6)+
        p('M90 121V127M90 146V153M77 136H82M98 136H103M90 126C84 124 81 129 82 133M99 136L102 133M78 136L76 133','stroke-width=".7"');
      lights=g(ellipse(24,7,90,45),'stroke-width="1.15"')+g(ellipse(21.5,5,90,45),'stroke-width=".35"');
    }
    if(id==='C21') {
      outer=[84,80,63,59].map(ring).join('');runes=script(76,108);
      geometry=polygon(79,5,2)+polygon(37,5,2)+p('M90 8V28M12 89H29M151 89H168M90 151V173');
      const horn=p('M83.7 68C69 47 53 39 34 35C20 32 19 24 18 17C9 31 17 46 34 47C53 48 64 65 72 82Z','data-horn="true" fill="#2b0817" stroke-width=".8"')+Array.from({length:17},(_,i)=>{const t=i/16,x=30+45*t,y=40+26*t*t;return p(`M${n(x-2)} ${n(y-5+t*6)}Q${n(x+1)} ${n(y-1)} ${n(x-1)} ${n(y+4)}`,'stroke-width=".3"');}).join('');
      ornament=horn+g(horn,'transform="translate(180 0) scale(-1 1)"')+p('M67.5 77L48 64L36 71L45 78L33 85L52 86L66.6 99M112.5 77L132 64L144 71L135 78L147 85L128 86L113.4 99M49 71L55 83L42 81M131 71L125 83L138 81','stroke-width=".7"');
      core=g(p('M90 61L99 66L107 64L105 81L118 93L113 110L104 122L99 141L90 153L81 141L76 122L67 110L62 93L75 81L73 64L81 66Z','fill="#190910" stroke-width=".8"')+p('M74 87L90 100L106 87L101 104L95 110L97 119L90 135L83 119L85 110L79 104Z M90 63L95 77L90 92L85 77Z M77 114L85 126L90 147L95 126L103 114M90 135V151','stroke-width=".55"')+p('M73 98Q79 95 84 104L81 107Z M107 98Q101 95 96 104L99 107Z','fill="#ffe4df" stroke-width=".5"'),'transform="translate(9 0) scale(.9 1)"');lights=ring(83)+c(90,70,4,'fill="url(#C21-spark)" stroke="none"');
    }
    if(id==='C23') {
      outer=[85,81,77,70,65].map(ring).join('');runes=script(74,118)+cardinal(80,3);
      geometry=polygon(72,3)+polygon(72,3,1,90)+polygon(68,4)+polygon(66,4,1,0)+polygon(50,3)+polygon(50,3,1,90)+ring(47)+ring(43);
      core=ring(24)+ring(21)+ring(18)+p('M90 29V67M90 114V151M29 90H66M114 90H151','opacity=".5"');
      ornament=[0,60,120,180,240,300].map(a=>turn(diamond(90,13,3)+p('M90 6V23'),a)).join('')+ticks(90,90,45,72,1);
      lights=g(p('M90 18L152.35 126H27.65Z M90 162L27.65 54H152.35Z'),'stroke-width="1.2"')+ring(82)+[0,60,120,180,240,300].map(a=>{const[x,y]=polar(72,a-90,90,90);return c(x,y,3,`fill="url(#${id}-spark)" stroke="none"`);}).join('');
    }
    if(id==='C24') {
      outer=[84,80,75].map(ring).join('');runes=script(78,65);
      // The source sweeps past neighboring arcs and turns inward near the rim.
      const sweeps=[
        'M98 91C103 77 85 72 76 83C58 104 77 137 105 141C151 147 175 99 151 57C137 34 111 16 86 13',
        'M97 95C113 75 95 52 75 55C45 58 31 94 46 120C65 153 110 148 133 125C162 98 161 52 135 31C121 20 104 13 86 11'
      ];
      geometry=Array.from({length:8},(_,i)=>turn(p(sweeps[i%2],`data-vortex-blade="${i}" stroke-width=".55"`),i*45)).join('');
      core='';ornament=Array.from({length:8},(_,i)=>turn(p('M43 23L47 25M21 59L23 62M117 20L124 24','stroke-width=".4"'),i*45)).join('');lights=ring(82)+Array.from({length:8},(_,i)=>turn(p(sweeps[i%2],'stroke-width=".8" opacity=".6"'),i*45)).join('');
    }
    if(id==='C25') {
      outer=[84,81,73,69].map(ring).join('');runes=script(77,121)+cardinal(80,3);
      geometry=p('M90 4L94 16L90 20L86 16Z M90 157L94 166L90 176L86 166Z','stroke-width=".6"');
      const boughs=[
        ['M90 113C91 100 88 90 77 83C65 75 48 85 34 82',1.1],
        ['M89 98C81 89 74 91 63 97C55 104 49 104 44 102',.8],
        ['M89 92C81 78 81 70 67 65C55 61 45 65 37 57',1],
        ['M89 83C85 64 80 58 72 51C66 43 64 39 65 32',.9],
        ['M90 72C95 57 93 42 90 26',.95],
        ['M84 72C77 69 74 61 76 52',.65],
        ['M74 67C65 62 60 57 60 49',.6],
        ['M60 64C53 57 53 52 49 45',.5],
        ['M76 83C65 79 61 74 58 69',.6],
        ['M58 81C48 76 41 75 34 76',.6],
        ['M45 81C43 88 38 90 32 90',.45],
        ['M64 96C59 90 51 93 48 90',.4],
        ['M79 59C83 49 78 46 79 40',.5],
        ['M91 50C85 43 83 36 84 29',.55],
        ['M73 51C66 50 60 45 58 41',.45]
      ];
      const branchHalf=boughs.map(([d,w])=>p(d,`data-tree-branch="true" stroke-width="${w}"`)).join('');
      const roots=[
        'M86 112C85 127 74 131 61 132C55 132 48 133 43 137C54 132 66 135 77 130C84 127 88 120 86 112Z',
        'M88 117C85 133 74 135 68 143L63 149C72 143 72 137 81 134C87 129 90 123 88 117Z',
        'M91 120C95 133 84 141 87 151L90 157C86 148 94 140 94 134C95 128 93 123 91 120Z',
        'M82 130C78 138 66 137 58 143C65 138 76 141 81 136L82 130Z',
        'M69 132C61 135 53 136 48 140C56 137 64 138 69 132Z',
        'M80 135C78 142 73 146 71 152C73 147 82 143 82 135Z',
        'M89 134C86 139 84 146 81 154C86 149 87 141 89 134Z',
        'M76 137C74 143 66 141 61 146M70 142L68 150M82 146L77 151M56 135L51 139'
      ];
      const rootHalf=roots.map(d=>p(d,'data-root-contour="true" data-tree-branch="true" stroke-width=".55"')).join('');
      const trunks=[
        'M85 126C93 115 80 104 82 96C82 87 74 82 64 82C79 80 87 89 88 98C89 108 94 116 85 126Z',
        'M90 134C98 120 88 113 88 102C88 90 92 85 94 77C96 92 92 98 93 106C94 117 98 124 90 134Z',
        'M93 126C84 117 92 108 94 101C97 89 104 83 116 82C104 83 99 92 98 101C97 112 88 119 93 126Z',
        'M90 99C90 87 81 78 81 67C81 60 77 54 72 51C81 53 84 60 84 68C85 80 93 86 90 99Z',
        'M91 86C89 72 98 66 98 55C98 47 101 44 105 41C101 48 102 57 100 63C96 73 94 78 91 86Z'
      ];
      core=g(branchHalf+g(branchHalf,'transform="translate(180 0) scale(-1 1)"')+
        rootHalf+g(rootHalf,'transform="translate(180 0) scale(-1 1)"')+
        trunks.map(d=>p(d,'data-trunk-contour="true" stroke-width=".65"')).join(''),'transform="translate(2.7 2.7) scale(.97)"');
      const twigs='M34 82L27 79M34 82L28 84M44 102L41 108M44 102L38 102M37 57L33 51M37 57L30 56M49 45L49 39M49 45L44 43M65 32L62 28M65 32L67 26M79 40L76 34M79 40L81 34M84 29L85 24M58 41L53 39M58 41L57 35M34 76L29 73M32 90L28 94M60 49L59 43M60 49L64 44M76 52L75 47M48 90L44 87';
      const buds='M43 59Q36 58 38 52Q42 54 43 59Z M51 66Q44 64 46 60Q51 62 51 66Z M60 53Q54 52 55 46Q59 48 60 53Z M65 62Q64 56 68 54Q70 59 65 62Z M75 73Q69 71 71 65Q75 67 75 73Z M48 83Q45 78 48 76Q52 80 48 83Z M59 95Q53 93 54 89Q59 90 59 95Z M42 101Q37 98 39 94Q44 97 42 101Z M81 47Q79 42 82 40Q85 44 81 47Z';
      const twigHalf=p(twigs,'stroke-width=".35"')+p(buds,'stroke-width=".4"');
      ornament=g(twigHalf+g(twigHalf,'transform="translate(180 0) scale(-1 1)"')+p('M90 36Q86 31 90 24Q94 31 90 36Z M90 61Q86 55 90 49Q94 55 90 61Z','stroke-width=".55"'),'transform="translate(2.7 2.7) scale(.97)"');
      lights=ring(81)+ring(69);
    }
    if(id==='C26') {
      outer=[85,80,55,52,48,18,14,10].map(ring).join('');runes=script(77,113);geometry=Array.from({length:8},(_,i)=>turn(p('M90 39L96 70L90 81L84 70Z',`data-wheel-spoke="${i}"`)+(i%2?p('M90 44V77'):''),i*45)).join('')+g(polygon(45,8)+polygon(39,8,3),'opacity=".5"')+ring(29)+ticks(90,90,50,64,3);
      ornament=[0,90,180,270].map(a=>turn(p('M90 3L94 17L90 28L86 17Z','data-axis-ornament="true"')+p('M90 3V28M84 20H96')+p('M90 28V39','data-axis-bridge="true"')+p('M90 39V81','data-axis-shaft="true"'),a)).join('');core=ring(5)+polygon(10,8)+polygon(14,4)+p('M90 81V99M81 90H99');lights=ring(53)+ring(16)+ring(81);
    }
    if(id==='C28') {
      outer=[84,79,73,69].map(ring).join('');runes=script(81,138);geometry=polygon(64,4)+polygon(49,4,1,0)+ring(48)+ring(25)+p('M90 13V166','stroke-width=".3"');ornament=[0,180].map(a=>turn(c(90,35,24)+p('M90 18L97 39L90 58L83 39Z M90 25L94 39L90 49L86 39Z'),a)).join('');
      core=p('M90 90C66 69 53 62 40 64C5 67 6 115 40 117C56 116 73 103 90 90C107 77 124 64 140 64C175 64 175 115 141 116C124 117 109 104 90 90Z','data-infinity="true" stroke-width="1.2"');lights=core.replace(/data-infinity="true"/,'')+ring(77)+c(90,90,10,'fill="url(#C28-spark)" stroke="none"');
    }
    if(id==='C29') {
      outer=[84,77,70,57].map(ring).join('');runes=script(73,116);geometry=polygon(68,5,2)+polygon(47,5,2,90)+ring(25)+ring(19)+polygon(18,6)+p('M90 33V64M32 68L70 81M53 144L77 105M129 144L103 105M149 68L110 81');
      const emblems=[['fire',90,27,'#ff7468','M0-14C-1-7-10-1-8 6C-6 15 8 15 9 5C9-1 4-7 0-14Z M0-7C-6 2-4 7 0 9C6 8 5 2 0-7Z'],['water',29,69,'#71b5ff','M0-12L3-4L12-4L6 3L8 12L0 7L-8 12L-6 3L-12-4H-3Z M0-7L6 7H-6Z'],['nature',151,69,'#a8ffb4','M0-12C-9-8-10 2 0 11C10 2 9-8 0-12Z M0-8V12M-9-8L-6 4L0 10L6 4L9-8'],['earth',52,144,'#ffdf86','M0-13L12 9H-12Z M0-8L-10 4H10Z M0-13V12'],['air',128,144,'#9effad','M0-12L3-5L11-7L8 0L11 7L3 5L0 12L-3 5L-11 7L-8 0L-11-7L-3-5Z M0-5L5 0L0 5L-5 0Z']];
      core=emblems.map(([kind,x,y,col,d])=>g(c(x,y,21,`fill="#06111b" stroke="${col}"`)+c(x,y,17,`stroke="${col}"`)+g(p(d,`stroke="${col}" stroke-width=".9"`),`transform="translate(${x} ${y})"`),`data-element="${kind}"`)).join('');ornament=Array.from({length:5},(_,i)=>turn(p('M90 7A83 83 0 0 1 169 64',`stroke="${['#fc7d76','#a1eaa0','#d1e0bd','#eedc87','#94c5ff'][i]}"`),i*72)).join('');lights=core.replace(/\sdata-element="[^"]*"/g,'');
    }
    if(id==='C30') {
      outer=[84,78,69,66,53,50,43].map(ring).join('');runes=script(75,114)+script(63,121);geometry=polygon(49,3)+polygon(49,3,1,90)+polygon(49,4)+g(polygon(37,6)+polygon(32,6,5)+polygon(23,8,3),'opacity=".6"')+ring(35)+ring(26);
      const marks=['M0-10V9M-5-6Q-5-11 0-11Q5-11 5-6V7H-5Z','M-8-6Q0 4 8-6L6 6H-6Z M-10-1H10M0-10V10','M0-10L3-3L10 0L3 3L0 10L-3 3L-10 0L-3-3Z','M-7-7H7V7H-7Z M-7 7L7-7M-7-7L7 7','M-6-7L0-10L6-7V5L0 10L-6 5Z M0-10V10M-6-7L6 5M6-7L-6 5','M0-10L8 5H-8Z M-8-5H8L0 10Z','M-8-4L0-10L8-4V5L0 10L-8 5Z M-8-4L0 3L8-4M0 3V10','M0-10C-8-6-8 4 0 9C8 4 8-6 0-10Z M-8-3L0 4L8-3M0-10V12'];
      ornament=marks.map((d,i)=>{const[x,y]=polar(77,-90+i*45,90,90);return g(c(x,y,10.5,'fill="#122647" stroke-width=".8"')+c(x,y,8.75)+g(p(d,'stroke-width=".55"'),`transform="translate(${x} ${y}) scale(.57)"`),`data-satellite="${i}"`);}).join('');core=crystal+g(polygon(16,4)+p('M90 48V132M48 90H132','stroke-width=".4"'),'opacity=".5"')+ring(10)+ring(3);lights=[0,90,180,270].map(a=>{const[x,y]=polar(51,a,90,90);return star(x,y,6,'#edfbff')+c(x,y,5,`fill="url(#${id}-spark)" stroke="none"`);}).join('')+c(90,90,6,`fill="url(#${id}-spark)" stroke="none"`);
    }
    const dust=Array.from({length:id==='C17'||id==='C19'?0:70},(_,i)=>{
      const [x,y]=polar(29+(i*7.31)%56,i*137.508,90,90);return c(x,y,i%7===0?.3:.12,`fill="${color}" stroke="none" opacity="${i%3===0?.6:.2}"`);
    }).join('');
    return `<defs><filter id="${id}-far" data-light="far" x="-50%" y="-50%" width="200%" height="200%" color-interpolation-filters="sRGB"><feGaussianBlur stdDeviation="${id==='C08'?.7:1.8}"/></filter><radialGradient id="${id}-spark"><stop stop-color="#fffff3"/><stop offset=".12" stop-color="#fffcec" stop-opacity=".95"/><stop offset=".3" stop-color="${color}" stop-opacity=".65"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></radialGradient><radialGradient id="${id}-wash"><stop stop-color="${color}" stop-opacity="${n(bloom*.1)}"/><stop offset=".68" stop-color="${color}" stop-opacity="${n(bloom*.09)}"/><stop offset=".86" stop-color="${color}" stop-opacity="${n(bloom*.11)}"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></radialGradient></defs>`+c(90,90,96,`fill="url(#${id}-wash)"`)+stroke('outer-ring',outer,.45,.48)+stroke('rune-ring',runes,.4,.2)+
      stroke('geometry',geometry,.5,.5,true)+stroke('ornaments',ornament,.45,.6,true)+stroke('center-core',core,.45,.6,true)+
      stroke('highlights',lights,.35,.7)+stroke('particles',dust,.1,.2);
  }
  function svg(id) {
    if (!ids.includes(id)) throw new RangeError('Unknown design: '+id);
    if(extra?.ids.includes(id))return extra.svg(id);
    const tall=id==='W03',native=id!=='C03'&&id!=='R01'&&!tall,w=native?180:tall?1000:1024,h=native?180:tall?1778:1024;
    const color=id==='C03'?'#a46b24':'#126c91';
    const defs=`<defs><radialGradient id="${id}-ambient"><stop stop-color="${color}" stop-opacity=".21"/><stop offset=".72" stop-color="${color}" stop-opacity=".045"/><stop offset="1" stop-color="${color}" stop-opacity="0"/></radialGradient><radialGradient id="${id}-point"><stop stop-color="#fffef1"/><stop offset=".15" stop-color="#ffe2a3" stop-opacity=".8"/><stop offset="1" stop-color="#edaa40" stop-opacity="0"/></radialGradient><linearGradient id="${id}-wood"><stop stop-color="#77d8ef"/><stop offset=".35" stop-color="#ffeec4"/><stop offset=".52" stop-color="#fffdef"/><stop offset=".72" stop-color="#ffdfa2"/><stop offset="1" stop-color="#81d8ef"/></linearGradient><filter id="${id}-soft" x="-30%" y="-30%" width="160%" height="160%" color-interpolation-filters="sRGB"><feGaussianBlur stdDeviation="${tall?3:3.3}"/></filter></defs>`;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid meet" role="img" aria-label="${titles[id]} — 직접 작성 기하 검토안" data-direct-circle="${id}"><title>${titles[id]}</title>${native?defs.replace('stdDeviation="3.3"','stdDeviation=".55"').replace(`id="${id}-soft"`,`id="${id}-soft" data-light="near"`):defs}<defs><filter id="${id}-mist" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="17"/></filter></defs>${p(`M0 0H${w}V${h}H0Z`,'fill="#02090e"')}${id==='C17'?c(90,90,85,'fill="#faf8ef"'):id==='C19'?c(90,90,85,'fill="#ffebac"'):''}${native?collectionC(id):id==='C03'?c03():id==='R01'?r01():w03()}</svg>`;
  }
  const mounted = new WeakMap();
  function mount(element,id) {
    const markup=svg(id);
    if (!element || element.nodeType!==1) throw new TypeError('A host element is required');
    mounted.get(element)?.stop();
    element.innerHTML=markup;
    element.dataset.state='static';
    const scene=element.querySelector('svg'), items=[...scene.querySelectorAll('[data-motion]')];
    let frame=0,deadline=0;
    const clean=state=> { cancelAnimationFrame(frame); clearTimeout(deadline); frame=0; deadline=0; items.forEach(e=>e.removeAttribute('style')); element.dataset.state=state; };
    const control={
      stop() { clean('static'); },
      play() {
        clean('static');
        if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
        element.dataset.state='playing';
        const start=performance.now();
        const animate=now=> {
          const elapsed=Math.min(7000,now-start), t=elapsed/7000;
          if (elapsed>=7000) { clean('complete'); return; }
          items.forEach(e=>{
            const phase=Number(e.dataset.phase||0), kind=e.dataset.motion;
            if(kind==='draw') { const length=e.getTotalLength(); e.style.strokeDasharray=String(length); e.style.strokeDashoffset=String(length*(1-Math.min(1,Math.max(0,(elapsed-phase*160)/2400)))); }
            if(kind==='reveal') e.style.opacity=String(.32+.68*Math.min(1,elapsed/2100));
            if(kind==='glow') e.style.opacity=String((Number(e.getAttribute('opacity'))||.5)*(.8+.2*Math.sin(Math.PI*t)));
            if(kind==='ring') { e.style.transformOrigin=id==='W03'?'500px 831px':'510px 530px'; e.style.transform=`rotate(${n(3*Math.sin(Math.PI*t))}deg)`; }
            if(kind==='particle') { e.style.opacity=String(.22+.65*(.5+.5*Math.sin(t*Math.PI*2+phase))); e.style.transform=`translate(0, ${n(-Math.sin(Math.PI*t)*((phase%4)+1)*1.3)}px)`; }
          });
          frame=requestAnimationFrame(animate);
        };
        animate(start);
        // The wall-clock deadline also cleans a hidden tab whose RAF is throttled.
        deadline=setTimeout(()=>clean('complete'),7000);
      }
    };
    mounted.set(element,control);
    return control;
  }
  root.DirectCircles=Object.freeze({ids:Object.freeze(ids),svg,mount});
})(globalThis);
