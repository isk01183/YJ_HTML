(function (root) {
    'use strict';
    const list = [
        {id:'moon', name:'月影のルーン', group:'signature', tag:'01 / MOON', color:'#efb5b3', accent:'#ffe9cf', rings:2, orbits:2, core:'crystal', desc:'淡いローズの細線と、月を巡る光の軌道。静かな輝きで充電の始まりを告げます。'},
        {id:'raphael', name:'智慧の大賢者', group:'signature', tag:'02 / SAGE', color:'#f4dfa7', accent:'#91d8ff', rings:4, orbits:3, core:'crystal', desc:'金と蒼の二重ルーン、三つの光軌道。幾何学の結晶に智慧の光が集まります。'},
        {id:'layered', name:'重層の魔導環', group:'signature', tag:'03 / ARCANE', color:'#ff9fbd', accent:'#ffd2dd', rings:4, orbits:2, core:'crystal', turn:true, desc:'幾重ものルーン環がゆっくり交差。中央の星は静止したまま、薔薇色の魔力が巡ります。'},
        {id:'premium', name:'星詠みの聖域', group:'signature', tag:'04 / SANCTUARY', color:'#f7e8be', accent:'#a3edf5', rings:4, orbits:3, core:'battery', desc:'白金と月光の聖域。中央に残量、下部に温度・状態・接続方式を表示します。'},
        {id:'basic', name:'原初の紅', group:'variation', tag:'I / ORIGIN', color:'#d98d9f', accent:'#efb7c9', rings:2, orbits:0, core:'crystal', desc:'紅いルーンと静止した星形を組み合わせた、端正な基本術式。'},
        {id:'blue', name:'蒼穹の術式', group:'variation', tag:'II / AZURE', color:'#87bcf5', accent:'#cae9ff', rings:3, orbits:0, core:'crystal', desc:'冷たい蒼の細線が描く、澄んだ夜空のような魔法陣。'},
        {id:'gold', name:'黄金の叡智', group:'variation', tag:'III / WISDOM', color:'#f0d095', accent:'#fff1cd', rings:3, orbits:3, core:'crystal', desc:'黄金のルーンと三つの光球。ゆるやかな軌道が結晶を包みます。'},
        {id:'silver', name:'白銀の星図', group:'variation', tag:'IV / CELESTIAL', color:'#cdd7ed', accent:'#f3f7ff', rings:4, orbits:0, core:'crystal', desc:'白銀の繊細な目盛りと多重の星図。細部まで静かに輝く精密な術式。'},
        {id:'violet', name:'紫苑の輪舞', group:'variation', tag:'V / VIOLET', color:'#c593f4', accent:'#f2cbff', rings:4, orbits:3, core:'crystal', turn:true, desc:'紫の外周環だけがゆっくり巡り、固定した星を柔らかな光が包みます。'},
        {id:'cyan', name:'水鏡の結界', group:'variation', tag:'VI / AQUA', color:'#78e0e8', accent:'#c7fcff', rings:3, orbits:2, core:'crystal', halo:true, desc:'透明な水色の光層と細い軌道。水面のような奥行きを持つ結界。'},
        {id:'core', name:'星核の光', group:'variation', tag:'VII / NOVA', color:'#f7d391', accent:'#fff2c6', rings:3, orbits:3, core:'flare', desc:'中心の星核へ黄金の光が集まり、柔らかい輝きが外へ広がります。'},
        {id:'minimal', name:'静寂の刻印', group:'variation', tag:'VIII / SILENCE', color:'#be929f', accent:'#ead0d7', rings:2, orbits:0, core:'crystal', quiet:true, desc:'装飾と動きを抑えた、細い線だけの静かな魔法陣。'},
        {id:'classic', name:'月火星風', group:'classic', tag:'ORIGINAL / v1.4', color:'#f7d895', accent:'#bb9df1', rings:2, orbits:0, core:'classic', desc:'これまでの金色の八芒星と紫の六芒星。従来の7秒演出をそのまま使用します。'}
    ];
    const collection = [
        ['celestial-satellites','天球の衛星陣','천구의 위성진','Celestial Satellites','#9acbff','#e8f6ff','六つの小宇宙が、精密な星図を囲む天球儀。','여섯 개의 작은 우주가 정밀한 별 지도를 둘러싼 천구의 마법진.','Six miniature celestial seals surround a precise stellar atlas.'],
        ['crimson-abyss','深紅の深淵','심홍의 심연','Crimson Abyss','#ec4b65','#ffc1b1','絡み合う荊と深紅の尖塔が、古い封印を描きます。','얽힌 가시와 심홍의 첨탑이 고대의 봉인을 그립니다.','Interwoven thorns and crimson spires trace an ancient seal.'],
        ['ivory-alchemy','象牙の錬金術','상아빛 연금술','Ivory Alchemy','#ede8d8','#fff9ea','羊皮紙の円盤に、黒い錬金記号と三角形を刻んだ古典術式。','양피지 원판에 검은 연금 기호와 삼각형을 새긴 고전 술식.','Dark alchemical symbols and triangles engraved on an ivory disc.'],
        ['triune-seal','三位の封印','삼위의 봉인','Triune Seal','#dce2ed','#ffffff','三つの円と正三角形が、一つの静かな結界を結びます。','세 원과 정삼각형이 하나의 고요한 결계를 이룹니다.','Three interlocking circles and an equilateral triangle bind one quiet ward.'],
        ['vital-staff','生命の杖','생명의 지팡이','Staff of Life','#f0b372','#fff0ce','蛇の螺旋と生命の杖を、琥珀の聖印で包みます。','뱀의 나선과 생명의 지팡이를 호박빛 성인이 감쌉니다.','A winding serpent and a staff of life within an amber sacred seal.'],
        ['solar-crown','太陽の王冠','태양의 왕관','Solar Crown','#f6ce80','#fff6cc','長短の光芒が重なり、黄金の太陽を戴く王冠。','길고 짧은 광망이 겹쳐 황금 태양의 왕관을 이룹니다.','Layered long and short rays form the crown of a golden sun.'],
        ['healing-wings','癒しの双翼','치유의 쌍익','Wings of Healing','#d6dcff','#ffffff','羽根を広げた双蛇の杖に、白銀の癒しの光が宿ります。','날개를 펼친 쌍사의 지팡이에 은빛 치유의 빛이 깃듭니다.','Silver healing light gathers around a winged, twin-serpent staff.'],
        ['lunar-phases','月相の巡り','달의 위상','Lunar Phases','#b9c8f2','#f3f5ff','新月から満月へ。八つの月相が静かな中心を巡ります。','신월에서 보름달까지, 여덟 달의 위상이 고요한 중심을 둘러쌉니다.','Eight lunar phases surround a calm center, from new moon to full.'],
        ['frost-crystal','氷晶の六芒','빙정의 육망','Frost Crystal','#89ddff','#edfcff','透き通る六角結晶と重なる三角形を、氷の細線で描きます。','투명한 육각 결정과 겹친 삼각형을 얼음빛 세선으로 그립니다.','Translucent hexagonal crystals and overlapping triangles in icy filigree.'],
        ['sage-nexus','賢者の中枢','현자의 중추','Sage Nexus','#95b8ff','#e4eaff','多面体の核と四方の星印が、深い蒼の知識を結びます。','다면체 핵과 사방의 별 인장이 깊고 푸른 지식을 연결합니다.','A polyhedral core and four stellar seals join in deep blue wisdom.'],
        ['hex-lattice','六重の星晶','육중의 성정','Hexagonal Lattice','#6ee8e5','#c8ffff','六つの六芒星が連結し、透明な結晶格子を編みます。','여섯 육망성이 이어져 투명한 결정 격자를 엮습니다.','Six connected hexagrams weave a translucent crystalline lattice.'],
        ['grimoire-star','魔導書の星','마도서의 별','Grimoire Star','#f2a1c8','#ffe4ee','古い魔導書の頁から、薔薇色の星と装飾が浮かびます。','고대 마도서의 책장에서 장밋빛 별과 장식이 떠오릅니다.','A rose-colored star and ornament emerge from an ancient grimoire.'],
        ['world-tree','世界樹の息吹','세계수의 숨결','Breath of the World Tree','#8bd6a0','#e1fbd0','枝と根が対になり、若葉の光が生命の環へ広がります。','가지와 뿌리가 짝을 이루고 새잎의 빛이 생명의 고리로 퍼집니다.','Paired branches and roots spread young-leaf light through a ring of life.'],
        ['blood-moon','紅月の封域','붉은 달의 봉역','Blood Moon Ward','#f27484','#ffd2c8','四つの赤い月が、荊の星を静かに封じます。','네 개의 붉은 달이 가시별을 고요히 봉인합니다.','Four red moons quietly contain a thorn-woven star.'],
        ['all-seeing-eye','真理の眼','진리의 눈','Eye of Truth','#c5a1f2','#f2e5ff','重なる三角形の奥で、真理を見つめる眼が輝きます。','겹친 삼각형 깊은 곳에서 진리를 바라보는 눈이 빛납니다.','An eye of truth shines within layered triangular geometry.'],
        ['spacetime-prism','時空の稜鏡','시공의 프리즘','Spacetime Prism','#a5d2ff','#e7f8ff','四次元の投影図と菱形の門が、静かな奥行きを作ります。','사차원의 투영도와 마름모 문이 고요한 깊이를 만듭니다.','A four-dimensional projection and diamond gates create quiet depth.'],
        ['spiral-galaxy','銀河の螺旋','은하의 나선','Spiral Galaxy','#ecc28d','#fff1cc','六本の星雲が黄金の核へ流れ、繊細な銀河を描きます。','여섯 성운의 팔이 황금 핵으로 흘러 섬세한 은하를 그립니다.','Six nebular arms flow toward a golden core in a delicate galaxy.'],
        ['heart-sanctuary','心の聖域','마음의 성역','Sanctuary of the Heart','#f3a3c4','#ffe8f0','光の心臓を抱く蔓と星。穏やかな守護の術式。','빛의 심장을 감싸는 덩굴과 별로 이루어진 온화한 수호 술식.','Vines and stars cradle a luminous heart in a gentle protective seal.'],
        ['snowflake','六花の祈り','눈꽃의 기도','Prayer of Snow','#bfdfff','#f4fcff','六方向へ枝分かれする氷の羽根を、途切れない線で描きます。','여섯 방향으로 뻗는 얼음 가지를 끊김 없는 선으로 그립니다.','Unbroken lines trace six branching wings of frost.'],
        ['jade-lotus','翡翠の蓮','비취의 연꽃','Jade Lotus','#80d9c1','#d5ffed','幾重もの蓮の花弁と清らかな炎が、翡翠色に開きます。','겹겹의 연꽃잎과 맑은 불꽃이 비취빛으로 피어납니다.','Layered lotus petals and a pure flame open in jade light.'],
        ['flame-spirit','炎の精霊','불의 정령','Spirit of Flame','#ffa467','#fff0b0','揺らめく炎の紋章と太陽の節点に、琥珀の魔力が集まります。','타오르는 불꽃 문양과 태양의 절점에 호박빛 마력이 모입니다.','Amber power gathers in a flame emblem and solar nodes.'],
        ['tidal-vortex','蒼潮の渦','푸른 조수의 소용돌이','Tidal Vortex','#75b8fc','#d7f5ff','三つの波が渦を結び、水の曲線が外周へ続きます。','세 물결이 소용돌이를 이루고 물의 곡선이 바깥 고리로 이어집니다.','Three waves form a vortex whose curves flow toward the outer rings.'],
        ['moon-wheel','時を刻む月輪','시간을 새기는 월륜','Wheel of Moons','#dfdfea','#ffffff','十二の月の円盤と細い目盛りが、夜の時を刻みます。','열두 달 원판과 가느다란 눈금이 밤의 시간을 새깁니다.','Twelve lunar discs and fine divisions mark the hours of night.'],
        ['gravity-well','虚空の門','공허의 문','Gate of the Void','#b18af0','#e1caff','暗い中心を、幾層もの紫の重力線が包みます。','어두운 중심을 겹겹의 보랏빛 중력선이 감쌉니다.','Layered violet gravitational curves surround a silent dark center.'],
        ['sakura-seal','桜花の結印','벚꽃의 결인','Sakura Seal','#f3bdce','#fff0eb','五枚の桜の花弁と小花が、淡い桃色の円環に咲きます。','다섯 벚꽃잎과 작은 꽃이 연분홍 원환에 피어납니다.','Five cherry petals and tiny blossoms flower within a pale pink ring.'],
        ['elemental-concord','四元素の調和','사원소의 조화','Elemental Concord','#bedcbe','#f4edc8','火・水・風・土、それぞれの紋章を一つの環へ結びます。','불·물·바람·흙의 문양을 하나의 고리로 잇습니다.','Fire, water, air, and earth join their emblems in one circle.'],
        ['spirit-bloom','精霊の契約花','정령의 계약화','Spirit Blossom','#9cd7b4','#e4f9d0','十二の花弁が重なり、精霊との契約を静かに開きます。','열두 꽃잎이 겹쳐 정령과의 계약을 고요하게 펼칩니다.','Twelve interwoven petals quietly unfold a spirit covenant.'],
        ['crescent-moon','宵月の庭','초승달의 정원','Crescent Garden','#b5bced','#edf0ff','大きな三日月と三つの小月を、夜空の蔓が囲みます。','큰 초승달과 세 작은 달을 밤하늘의 덩굴이 둘러쌉니다.','Night-sky vines enclose a great crescent and three smaller moons.'],
        ['star-guide','星導の灯','별길의 등불','Guiding Star','#d7b2ff','#fff0ff','長い四光芒と八角の星が、遠い道を指し示します。','길게 뻗은 네 광망과 팔각별이 먼 길을 비춥니다.','Four long rays and an eight-pointed star illuminate a distant path.'],
        ['chronos-gears','時の歯車','시간의 톱니','Gears of Time','#d9ba83','#fbeace','ローマ数字の時計環に、大小の歯車が噛み合います。','로마 숫자 시계 고리 안에서 크고 작은 톱니가 맞물립니다.','Large and small gears interlock within a Roman-numeral clock.'],
        ['seraph-wings','天使の印章','천사의 인장','Seraphic Seal','#d8c4f8','#fff1ff','左右へ広がる羽根と光輪が、天使の星を抱きます。','양옆으로 펼친 깃털과 광륜이 천사의 별을 품습니다.','Spreading feathered wings and a halo cradle an angelic star.'],
        ['infernal-pact','深淵の契約','심연의 계약','Infernal Pact','#dd5c73','#ffb3a2','湾曲する双角と棘の紋章を、紅い契約環が封じます。','휘어진 쌍각과 가시 문양을 붉은 계약의 고리가 봉인합니다.','Curved twin horns and a barbed sigil are bound by a crimson pact ring.'],
        ['binding-chains','封鎖の鎖環','봉쇄의 사슬환','Binding Chains','#bbcbe8','#eef5ff','四本の鎖と中央の錠前が、静かな封印を守ります。','네 사슬과 중앙 자물쇠가 고요한 봉인을 지킵니다.','Four chains and a central lock protect a quiet seal.'],
        ['guardian-shield','守護の結界','수호의 결계','Guardian Shield','#8ed2ff','#e0f7ff','六芒の外壁と中央の盾が、重なる守護の層を作ります。','육망의 외벽과 중앙 방패가 겹겹의 수호층을 이룹니다.','A hexagram wall and central shield form layered protection.'],
        ['infinite-bond','無限の絆','무한의 유대','Infinite Bond','#86dcf0','#d6faff','一本の光が途切れず交差し、無限の結び目を描きます。','한 줄기 빛이 끊김 없이 교차하며 무한한 매듭을 그립니다.','One unbroken strand of light crosses into an infinite bond.'],
        ['fate-compass','運命の羅針盤','운명의 나침반','Compass of Fate','#e1bd87','#fff0ce','方位の針と星の座標が、運命の進む先を示します。','방위 바늘과 별의 좌표가 운명이 향하는 길을 가리킵니다.','Directional needles and stellar coordinates reveal the course of fate.'],
        ['earth-crystal','大地の結晶','대지의 결정','Crystal of Earth','#d6be8d','#fff0c6','根を張る菱形の結晶に、大地の温かな光が宿ります。','뿌리내린 마름모 결정에 대지의 따뜻한 빛이 깃듭니다.','Warm earthen light rests in a rooted, faceted crystal.'],
        ['water-spirit','水の精霊','물의 정령','Spirit of Water','#80d6e8','#d4fbff','大きな雫と水面の波紋が、清らかな水の契約を描きます。','큰 물방울과 수면의 파문이 맑은 물의 계약을 그립니다.','A great droplet and rippling water trace a pure aquatic covenant.'],
        ['wind-spirit','風の精霊','바람의 정령','Spirit of Wind','#96d3a5','#e3f8cb','三つの風紋と舞う葉が、柔らかな緑の環を結びます。','세 바람 문양과 흩날리는 잎이 부드러운 초록 고리를 잇습니다.','Three wind spirals and dancing leaves join a soft green ring.'],
        ['light-rosette','光明の薔薇窓','광명의 장미창','Rosette of Light','#e4e1cd','#ffffff','細密な薔薇窓の奥から、白金の光が穏やかに広がります。','정교한 장미창 깊은 곳에서 백금빛이 부드럽게 퍼집니다.','Platinum light spreads gently through a finely traced rose window.'],
        ['dream-constellation','夢渡りの星座','꿈을 건너는 별자리','Dream Constellation','#a8a6ec','#e8e5ff','星座の道と小さな月が、夢の夜空を繋ぎます。','별자리의 길과 작은 달이 꿈의 밤하늘을 잇습니다.','Constellation paths and small moons connect a dreaming night sky.'],
        ['twilight-balance','陰陽の均衡','음양의 균형','Twilight Balance','#85d1d8','#e9f9f3','二つの流れが一つの円を分け、光と影の均衡を保ちます。','두 흐름이 하나의 원을 나누어 빛과 그림자의 균형을 지킵니다.','Two currents divide one circle, balancing light and shadow.']
    ];
    collection.forEach((entry, index) => {
        const [id, ja, ko, en, color, accent, descJa, descKo, descEn] = entry;
        list.push({id, name:ja, names:{ja,ko,en}, desc:descJa, descriptions:{ja:descJa,ko:descKo,en:descEn},
            group:'collection', tag:String(index+1).padStart(2,'0')+' / '+id.toUpperCase().replace(/-/g,' '), color, accent, rings:3, orbits:0, core:'collection'});
    });
    const get = id => list.find(theme => theme.id === id) || list.find(theme => theme.id === 'classic');
    function battery(params) {
        const number = key => {
            const value = params.get(key);
            return value !== null && value.trim() !== '' && Number.isFinite(Number(value)) ? Number(value) : null;
        };
        const level = number('battery'), temperature = number('temperature');
        const plugged = number('plugged'), status = number('status');
        return {
            percent: level !== null && level >= 0 && level <= 100 ? String(Math.round(level)) : '—',
            temperature: temperature === null ? '—' : (temperature / 10).toFixed(1) + '°C',
            health: ({2:'良好',3:'高温',4:'劣化',5:'過電圧',6:'異常',7:'低温'})[number('health')] || '不明',
            connection: ({0:'未接続',1:'AC',2:'USB',4:'ワイヤレス',8:'ドック'})[plugged] || '不明',
            status: plugged === 0 ? '未接続' : status === 5 ? '充電完了' : status === 2 ? '充電中' : plugged > 0 ? '充電待機' : '状態不明'
        };
    }
    // Closed SVG polygons keep every central edge connected throughout the animation.
    function polygon(count, radius, step = 1, offset = -90) {
        return Array.from({length:count}, (_, i) => {
            const angle = (offset + i * step * 360 / count) * Math.PI / 180;
            return (300 + Math.cos(angle) * radius).toFixed(2) + ',' + (300 + Math.sin(angle) * radius).toFixed(2);
        }).join(' ');
    }
    const glyphs = ['M-3 7V-7L4-2 -3 2M-3-7 3-3','M-4 5 0-7 4 5M-3 1H3M0-7V7','M-3-7V7M-3-5 4 0-3 5','M-4-7 4 0-4 7M0-3V7','M-4-6 0-2 4-6M0-2V7M-4 2 4-2','M-3 7V-7L4-2 -3 2 4 7','M-4 0 0-7 4 0 0 7Z','M-4-7 4 7M4-7-4 7M0-7V7','M-3-7H4L-3 0H4L-3 7','M-4-5 4-1-4 3M0-7V7'];
    function runeRing(radius, count, offset = 0) {
        return Array.from({length:count}, (_, i) => '<g transform="translate(300 300) rotate(' + (360 * i / count + offset) + ') translate(0 -' + radius + ')"><path d="' + glyphs[i % glyphs.length] + '"/></g>').join('');
    }
    // Collection motifs use center-relative coordinates; the original thirteen renderers stay unchanged.
    const line = (d, attrs = '') => '<path d="'+d+'" '+attrs+'/>';
    const ring = (r, attrs = '') => '<circle cx="0" cy="0" r="'+r+'" '+attrs+'/>';
    const group = (body, attrs = '') => '<g '+attrs+'>'+body+'</g>';
    const place = (x,y,body,scale=1) => group(body,'transform="translate('+x+' '+y+') scale('+scale+')"');
    const around = (n, body, offset=0) => Array.from({length:n},(_,i)=>group(typeof body==='function'?body(i):body,'transform="rotate('+(offset+i*360/n)+')"')).join('');
    const point = (r,a) => [(r*Math.cos(a*Math.PI/180)).toFixed(2),(r*Math.sin(a*Math.PI/180)).toFixed(2)];
    function shape(n,r,step=1,offset=-90) {
        return '<polygon points="'+Array.from({length:n},(_,i)=>point(r,offset+i*360*step/n).join(',')).join(' ')+'"/>';
    }
    function star(n,r,inner,offset=-90) {
        return '<polygon points="'+Array.from({length:n*2},(_,i)=>point(i%2?inner:r,offset+i*180/n).join(',')).join(' ')+'"/>';
    }
    function rosette(n,r,w,offset=0) {
        return around(n,line('M0 0C'+(-w)+' '+(-r*.5)+' '+(-w)+' '+(-r*.84)+' 0 '+(-r)+'C'+w+' '+(-r*.84)+' '+w+' '+(-r*.5)+' 0 0Z'),offset);
    }
    const hexagram = r => shape(3,r)+shape(3,r,1,90);
    const diamond = (r,w) => line('M0 '+(-r)+' '+w+' 0 0 '+r+' '+(-w)+' 0Z');
    const crescent = r => line('M0 '+(-r)+'A'+r+' '+r+' 0 1 0 0 '+r+'A'+(r*.7)+' '+r+' 0 0 1 0 '+(-r)+'Z');
    function localRunes(radius,count,scale=1) {
        return around(count,i=>place(0,-radius,line(glyphs[i%glyphs.length]),scale));
    }
    function seal(r,kind=6) {
        return ring(r)+ring(r*.83,'stroke-width=".45"')+group(kind===6?hexagram(r*.68):star(kind,r*.68,r*.29),'stroke-width=".7"')+ring(r*.16);
    }
    function branches(depth,length,width) {
        if(!depth) return line('M0 0Q-10 -12 0 -23Q10 -12 0 0Z','fill="currentColor" fill-opacity=".12"');
        return line('M0 0Q'+(length*.08)+' '+(-length*.6)+' 0 '+(-length),'stroke-width="'+width+'"')+
            place(0,-length,group(branches(depth-1,length*.74,width*.69),'transform="rotate(-39)"')+group(branches(depth-1,length*.70,width*.69),'transform="rotate(42)"'));
    }
    function wings(span=1) {
        let wing = line('M-14 6C-41-3-52-23-70-36C-88-48-124-53-174-103C-171-63-151-37-136-21L-158-34C-150-4-131 14-108 25L-128 17C-115 42-91 52-71 49L-88 61C-64 64-45 56-29 37Q-16 20-14 6Z','fill="currentColor" fill-opacity=".07" stroke-width="1.6"');
        const tips=[[-163,-80],[-154,-51],[-144,-26],[-134,-6],[-119,16],[-103,33],[-82,46],[-61,48]];
        tips.forEach(([x,y],i)=>{
            wing += line('M-21 '+(10+i*2)+'Q'+(x*.62)+' '+(y-25)+' '+x+' '+y+'Q'+(x+18)+' '+(y+17)+' '+(-34-i*2)+' '+(25+i*2),'stroke-width="'+(i%3===0?1.15:.7)+'"');
        });
        return group(wing+group(wing,'transform="scale(-1 1)"'),'transform="scale('+span+')"');
    }
    function gear(radius,teeth) {
        return '<polygon points="'+Array.from({length:teeth*4},(_,i)=>point(i%4===1||i%4===2?radius:radius*.91,i*90/teeth).join(',')).join(' ')+'"/>'+ring(radius*.73)+ring(radius*.28)+around(6,line('M0 '+(-radius*.31)+'V'+(-radius*.69)));
    }
    function spiral(arms,radius,turns,reverse=false) {
        const d = Array.from({length:101},(_,i)=>{
            const r=12+(radius-12)*i/100, a=(reverse?-1:1)*i/100*360*turns;
            return (i?'L':'M')+point(r,a).join(' ');
        }).join('');
        return around(arms,line(d));
    }
    const flame = line('M0-126C-4-77-63-62-58-9C-57 28-32 53 0 56C46 55 70 25 57-17C49-44 29-65 33-97C14-73 11-57 14-41C-10-58-16-82 0-126Z','fill="currentColor" fill-opacity=".10" stroke-width="2"')+
        line('M0 50C-34 17-20-6-5-25C-6-1 20 1 19 24C19 35 12 43 0 50Z','fill="currentColor" fill-opacity=".35"');
    const drop = line('M0-112C-13-74-63-29-63 15A63 63 0 0 0 63 15C63-29 13-74 0-112Z','fill="currentColor" fill-opacity=".12" stroke-width="1.8"')+
        line('M-32-7Q-48 31-16 51','stroke-width="3" opacity=".6"');
    function collectionMotif(t) {
        const accent = body => group(body,'stroke="'+t.accent+'"');
        const fine = body => group(body,'stroke-width=".65" opacity=".72"');
        const bright = body => group(body,'stroke-width="1.6"');
        const smallNodes = (n,r,sz=18,offset=0) => around(n,place(0,-r,seal(sz)),offset);
        switch(t.id) {
        case 'celestial-satellites':
            return fine(ring(185)+hexagram(170)+shape(9,152,4))+bright(seal(96,8))+around(6,i=>place(0,-182,seal(34,i%2?5:6)+localRunes(29,12,.4)))+
                fine(around(3,'<ellipse cx="0" cy="0" rx="178" ry="74"/>'))+accent(rosette(6,78,22)+ring(14));
        case 'crimson-abyss':
            return bright(star(8,183,54)+star(8,148,99, -67.5))+fine(rosette(16,176,23)+ring(132)+ring(123)+shape(12,190,5))+
                around(8,line('M0-185Q-31-150-16-124Q-3-102 0-83Q4-124 16-124Q31-150 0-185Z'))+accent(star(8,55,19)+ring(12));
        case 'ivory-alchemy':
            return group(ring(239,'fill="#e8e2cc" stroke="#f6f0de" stroke-width="4"')+ring(224)+ring(216)+localRunes(207,72,.67)+
                shape(3,190)+shape(3,163,1,90)+ring(149)+ring(90)+ring(82)+hexagram(70)+
                around(3,place(0,-170,ring(24,'fill="#e8e2cc"')+ring(19)+line('M-8 9V-7Q-8-15 0-15Q8-15 8-7V9M-13 9H-4M4 9H13')))+
                around(3,place(0,-118,ring(10)+line('M0-5V5M-5 0H5')),60)+fine(around(12,line('M0-145V-154'))),
                'stroke="#292827" stroke-width="1.25" color="#292827"');
        case 'triune-seal':
            return bright(shape(3,185)+ring(183))+fine(shape(3,151,1,90)+ring(153))+around(3,place(0,-65,ring(78)))+
                bright(ring(24))+smallNodes(3,185,19)+around(3,place(0,-151,diamond(12,6)),60)+fine(around(12,line('M0-173V-177')));
        case 'vital-staff':
            return fine(hexagram(169)+ring(163)+rosette(8,138,25))+bright(line('M0-179V166M-68-91H68M0-179-9-159H9Z','fill="currentColor" fill-opacity=".2"')+ring(50))+
                line('M21-124C85-101 65-48 8-29C-49-10-51 30 0 48C45 64 38 111-9 127C-37 139-26 165 0 176','stroke-width="10"')+
                line('M21-124C85-101 65-48 8-29C-49-10-51 30 0 48C45 64 38 111-9 127','stroke="'+t.accent+'" stroke-width="1.3"')+
                line('M21-124 7-137 34-139 43-126Z','fill="currentColor"')+smallNodes(4,183,15);
        case 'solar-crown':
            return bright(star(16,194,106))+fine(star(16,173,131,-78.75)+around(16,line('M0-80V-177'))+ring(105)+ring(116))+
                accent(bright(star(8,98,43)+rosette(8,66,21)+ring(28)))+around(8,place(0,-202,diamond(8,3)));
        case 'healing-wings':
            return fine(shape(3,176)+shape(3,176,1,90)+ring(164))+place(0,-47,wings(.86))+
                bright(line('M0-156V167M-9-151Q0-172 9-151L0-141Z')+place(0,-126,ring(12)))+
                line('M-23-35C-80-4 52 27 27 61C4 84-35 92-14 130L0 152M23-35C80-4-52 27-27 61C-4 84 35 92 14 130L0 152','stroke-width="2.5"')+
                accent(place(0,-47,ring(17)))+smallNodes(3,182,17);
        case 'lunar-phases':
            return fine(ring(163)+ring(149)+star(8,139,75))+around(8,i=>place(0,-181,ring(26,'fill="#07101c"')+
                (i===0?ring(21,'fill="currentColor" fill-opacity=".6"'):i===4?ring(21):group(crescent(21),'transform="rotate('+(i<4?180:0)+')" fill="currentColor" fill-opacity="'+(.13+Math.abs(4-i)*.12)+'"'))))+
                accent(bright(crescent(67)))+fine(rosette(8,117,12));
        case 'frost-crystal':
            return fine(shape(6,181)+shape(6,147)+hexagram(155)+ring(176))+bright(hexagram(183))+accent(rosette(6,133,21)+star(6,103,38))+
                around(6,line('M0-44V-181M0-131-15-147M0-131 15-147M0-164-8-176M0-164 8-176'))+smallNodes(6,195,9);
        case 'sage-nexus':
            return fine(shape(8,180,3)+shape(8,137,3)+ring(163)+ring(145)+rosette(8,121,17))+bright(shape(4,153)+shape(4,122,1,-45))+
                accent(bright(shape(6,73)+hexagram(73)+shape(4,60,1,-45))+ring(14))+smallNodes(4,184,22)+fine(around(8,line('M0-81V-162')));
        case 'hex-lattice':
            return fine(ring(180)+shape(6,183)+hexagram(175))+around(6,place(0,-91,hexagram(89)))+
                accent(bright(hexagram(106)+shape(6,91)))+fine(around(6,line('M0-181V-34')))+star(6,40,18);
        case 'grimoire-star':
            return fine(ring(180)+shape(8,174,3)+shape(4,147,1,-45))+bright(star(8,181,82))+around(4,line('M0-199-14-163 0-174 14-163Z'))+
                fine(around(8,line('M0-129Q-26-147-38-104Q-18-121 0-107Q18-121 38-104Q26-147 0-129Z')))+
                accent(bright(star(8,83,28)+ring(39)))+smallNodes(4,205,8);
        case 'world-tree':
            return fine(ring(183)+ring(173)+hexagram(168)+rosette(8,145,25))+place(0,104,branches(5,63,3.5))+
                place(0,103,group(branches(4,40,2),'transform="rotate(180)"'))+line('M-57 107Q0 122 57 107','stroke-width="1.7"')+
                accent(smallNodes(4,186,12));
        case 'blood-moon':
            return fine(ring(169)+shape(12,157,5)+rosette(8,145,29))+bright(star(8,155,57))+around(4,place(0,-182,
                ring(31,'fill="#130307"')+group(crescent(27),'fill="currentColor" fill-opacity=".65"')))+accent(star(8,52,21)+ring(11));
        case 'all-seeing-eye':
            return fine(ring(178)+shape(3,153,1,90))+bright(shape(3,187))+fine(hexagram(129)+rosette(6,121,17))+
                line('M-103 0Q0-94 103 0Q0 94-103 0Z','fill="#0a0714" stroke-width="2"')+
                bright(ring(42)+ring(33))+accent(line('M0-27Q-13 0 0 27Q13 0 0-27Z','fill="currentColor" fill-opacity=".7"'))+smallNodes(3,187,14);
        case 'spacetime-prism': {
            let lattice = shape(4,146,1,-45)+shape(4,68,1,-45);
            for(let i=0;i<4;i++) lattice += line('M'+point(146,-45+i*90).join(' ')+'L'+point(68,-45+i*90).join(' '));
            return fine(ring(181)+shape(8,170,3))+bright(diamond(194,127))+fine(diamond(139,194))+accent(lattice)+
                group(lattice,'transform="rotate(45)" opacity=".45" stroke-width=".65"')+smallNodes(4,191,11);
        }
        case 'spiral-galaxy':
            return fine(ring(180)+ring(166,'stroke-dasharray="1 6"'))+bright(spiral(6,184,1.2))+fine(group(spiral(6,179,1.2),'transform="rotate(5)"'))+
                around(6,place(0,-172,ring(3,'fill="currentColor"')),22)+accent(star(8,45,10)+ring(10));
        case 'heart-sanctuary':
            return fine(ring(172)+shape(8,174,3)+rosette(8,159,19))+bright(star(4,185,72))+
                line('M0 87C-27 64-108 6-96-44C-84-94-27-101 0-57C27-101 84-94 96-44C108 6 27 64 0 87Z','stroke-width="2" fill="currentColor" fill-opacity=".07"')+
                accent(line('M0 52C-37 20-66-2-56-30C-47-55-16-55 0-31C16-55 47-55 56-30C66-2 37 20 0 52Z'))+
                around(4,place(0,-190,rosette(4,11,5)));
        case 'snowflake':
            return fine(ring(181)+hexagram(171)+shape(6,136))+around(6,bright(line('M0 0V-185M0-161-19-177M0-161 19-177M0-121-40-153M0-121 40-153M0-77-38-108M0-77 38-108'))+
                fine(line('M-40-153-38-172M-40-153-58-153M40-153 38-172M40-153 58-153M-38-108-38-126M38-108 38-126')))+
                accent(shape(6,49)+star(6,62,27))+smallNodes(6,195,7);
        case 'jade-lotus':
            return fine(ring(179)+shape(8,166,3))+line('M0 117C-60 101-129 64-151-13C-87-9-43 15 0 72C43 15 87-9 151-13C129 64 60 101 0 117Z','stroke-width="1.8"')+
                line('M0 112C-47 84-93 20-83-78C-36-40-9 0 0 49C9 0 36-40 83-78C93 20 47 84 0 112Z','stroke-width="1.5"')+
                line('M0 96C-56 26-46-58 0-124C46-58 56 26 0 96Z','fill="currentColor" fill-opacity=".06" stroke-width="1.8"')+
                accent(place(0,27,flame,.53))+line('M-82 132Q0 146 82 132')+smallNodes(4,189,11);
        case 'flame-spirit':
            return fine(ring(172)+shape(3,185)+shape(3,147,1,90))+bright(star(4,189,93))+place(0,22,flame)+
                around(4,place(0,-189,ring(15)+rosette(8,12,4)))+fine(around(12,line('M0-148Q-10-168 0-184Q10-168 0-148Z')));
        case 'tidal-vortex':
            return fine(ring(183)+ring(173)+hexagram(157))+around(3,bright(line('M-12-8C-117-38-146 49-106 106C-170 76-193-22-128-81C-86-116-16-101 8-59C-13-78-46-74-50-48C-54-23-21-11-12-8Z')))+
                fine(around(3,line('M-61-99C-131-112-192-45-182 12M-38-112C-86-143-162-125-187-76')))+accent(rosette(3,43,11));
        case 'moon-wheel':
            return fine(ring(180)+ring(155)+ring(105)+around(12,line('M0-111V-152')))+around(12,i=>place(0,-181,ring(18,'fill="#080a13"')+
                (i%3===0?ring(13,'fill="currentColor" fill-opacity=".4"'):group(crescent(13),'transform="rotate('+(i*30)+')"'))))+
                bright(star(12,104,49))+accent(ring(44)+shape(6,44)+ring(17))+fine(around(12,place(0,-124,diamond(8,3))));
        case 'gravity-well':
            return fine(ring(182)+shape(8,170,3))+bright(spiral(9,180,.78))+fine(group(spiral(9,174,.78),'transform="rotate(6)"'))+
                ring(68,'stroke="'+t.accent+'" stroke-width="5" opacity=".23"')+ring(61,'fill="#05020d" stroke-width="2.2"')+ring(67,'opacity=".65"')+
                smallNodes(4,192,10);
        case 'sakura-seal':
            return fine(ring(179)+shape(5,167,2)+rosette(10,140,17))+around(5,
                line('M0 5C-59-30-67-103-20-125L0-108 20-125C67-103 59-30 0 5Z','fill="currentColor" fill-opacity=".12" stroke-width="1.65"')+
                fine(line('M0-11V-96M0-35-22-57M0-51 20-70')))+accent(rosette(10,34,9)+ring(11))+
                around(5,place(0,-185,rosette(5,17,8)),36);
        case 'elemental-concord': {
            const symbols=[flame,drop,line('M-50 8C-93-35-18-77 13-36C40 8-8 37-26 13C-41-8-4-24 8-1M3-57C40-93 80-30 50 7C22 43-27 54-35 87M49 29C88 41 48 103 8 79C-21 62-24 45-42 43','stroke-width="5"'),diamond(86,53)+line('M0-86V86M-53 0H53')];
            const colors=['#ffad7a','#9adeff','#ade1b0','#f2d389'];
            return fine(ring(171)+hexagram(158)+shape(4,164,1,-45))+bright(star(4,169,71))+around(4,i=>place(0,-178,
                group(ring(32,'fill="#070b0d"')+ring(27)+place(0,7,symbols[i],.22),'stroke="'+colors[i]+'" color="'+colors[i]+'"')))+
                accent(seal(53,8))+fine(around(4,line('M0-79V-139')));
        }
        case 'spirit-bloom':
            return fine(ring(181)+shape(12,170,5))+bright(rosette(12,178,40))+accent(rosette(6,120,31,30)+rosette(6,79,20))+ring(23)+
                around(6,place(0,-192,diamond(11,5)));
        case 'crescent-moon':
            return fine(ring(180)+ring(167)+around(12,line('M0-176V-169')))+place(22,-44,group(crescent(85),'fill="currentColor" fill-opacity=".12" stroke-width="2"'))+
                place(-111,10,crescent(31))+place(105,82,crescent(23))+place(-32,121,crescent(25))+
                fine(line('M-148 85C-183-56-72-164 89-132M-127 111C-170-3-79-129 63-133')+around(5,place(0,-159,diamond(8,3)),18))+
                accent(place(-94,-78,star(4,15,4))+place(77,15,star(4,12,3)));
        case 'star-guide':
            return fine(ring(179)+shape(8,167,3)+shape(4,133,1,-45))+bright(star(4,203,38))+accent(star(8,120,41,-67.5)+star(8,56,20))+
                around(4,place(0,-191,diamond(15,6)))+fine(around(8,line('M0-124V-165'),22.5))+ring(12);
        case 'chronos-gears': {
            const roman=['XII','I','II','III','IV','V','VI','VII','VIII','IX','X','XI'];
            return ring(196)+ring(166)+around(12,i=>place(0,-180,'<text text-anchor="middle" dominant-baseline="middle" font-family="serif" font-size="20" fill="'+t.color+'" stroke="none">'+roman[i]+'</text>'))+
                fine(around(60,line('M0-156V-161')))+place(0,19,gear(79,20))+place(-90,88,gear(48,12))+place(90,-77,gear(57,14))+
                bright(line('M0 117V-126M0-104 10-73H-10Z','fill="currentColor" fill-opacity=".25"')+line('M0 0 70 44','stroke-width="3"'))+
                accent(ring(12)+place(0,-132,diamond(17,6)));
        }
        case 'seraph-wings':
            return fine(ring(180)+shape(3,177)+shape(3,149,1,90))+place(0,3,wings())+
                '<ellipse cx="0" cy="-133" rx="51" ry="11" stroke-width="2"/>'+fine('<ellipse cx="0" cy="-133" rx="61" ry="16"/>')+
                accent(bright(star(6,67,25)))+place(0,118,rosette(6,35,12))+smallNodes(4,189,12);
        case 'infernal-pact':
            return fine(ring(182)+shape(5,182,2)+shape(5,146,2,90))+bright(line('M-9 25C-49-18-50-57-111-83C-161-104-158-142-134-167C-147-114-95-129-63-106C-31-84-23-56 0-51C23-56 31-84 63-106C95-129 147-114 134-167C158-142 161-104 111-83C50-57 49-18 9 25L0 136Z','fill="currentColor" fill-opacity=".08"'))+
                around(2,line('M-12-35-61-55-42-17Z','fill="currentColor" fill-opacity=".28"'))+accent(star(5,58,23,90))+
                fine(line('M-97 80-122 116-79 102M97 80 122 116 79 102M-23 144 0 177 23 144'));
        case 'binding-chains':
            return fine(ring(178)+ring(148)+hexagram(164))+around(4,Array.from({length:8},(_,i)=>
                place(0,-82-i*15,'<rect x="-7" y="-11" width="14" height="22" rx="7" transform="rotate('+(i%2?18:-18)+')" stroke-width="1.6"/>')).join(''),45)+
                '<rect x="-48" y="-11" width="96" height="88" rx="10" fill="#0a101b" stroke-width="2"/>'+
                bright(line('M-30-11V-45A30 30 0 0 1 30-45V-11M-21-11V-44A21 21 0 0 1 21-44V-11'))+
                accent(line('M-5 27A10 10 0 1 1 5 27L10 50H-10Z','fill="currentColor" fill-opacity=".3"'))+smallNodes(4,197,10);
        case 'guardian-shield':
            return fine(ring(180)+ring(167)+shape(6,179))+bright(hexagram(186))+fine(hexagram(158))+
                line('M0-99Q44-78 84-84V-11Q77 66 0 116Q-77 66-84-11V-84Q-44-78 0-99Z','fill="#061019" stroke-width="2"')+
                accent(line('M0-80Q34-63 65-65V-8Q59 51 0 93Q-59 51-65-8V-65Q-34-63 0-80Z')+star(4,44,13))+
                smallNodes(6,194,8);
        case 'infinite-bond': {
            const infinity='M0 0C-53-68-156-115-163-22C-171 81-62 83 0 0C53-68 156-115 163-22C171 81 62 83 0 0Z';
            return fine(ring(181)+ring(157)+shape(4,178))+line(infinity,'stroke-width="6" opacity=".17"')+bright(line(infinity))+
                fine(group(line(infinity),'transform="scale(1 .82)"'))+accent(smallNodes(2,176,19)+star(4,27,8))+
                fine(around(4,place(0,-137,diamond(21,7)),45));
        }
        case 'fate-compass':
            return fine(ring(180)+ring(162)+ring(109)+around(72,line('M0-163V-172')))+bright(star(8,187,44))+
                fine(star(8,159,79,-67.5)+around(8,line('M0-22V-158')))+accent(ring(43)+star(8,53,18))+
                around(4,place(0,-198,diamond(10,4)))+smallNodes(4,136,7,45);
        case 'earth-crystal':
            return fine(ring(180)+shape(4,180,1,-45)+hexagram(151))+bright(diamond(135,82))+line('M0-135-30 0 0 135 30 0ZM-82 0H82M-82 0 0-66 82 0 0 58Z')+
                fine(line('M-24 142-62 173M-14 143-20 179M24 142 62 173M14 143 20 179'))+accent(smallNodes(4,188,14));
        case 'water-spirit':
            return fine(ring(180)+shape(4,171)+shape(4,132,1,-45))+place(0,7,drop)+
                fine('<ellipse cx="0" cy="108" rx="83" ry="16"/><ellipse cx="0" cy="111" rx="111" ry="28"/><ellipse cx="0" cy="113" rx="139" ry="39"/>')+
                accent(smallNodes(4,191,15))+around(4,place(0,-155,diamond(9,3)));
        case 'wind-spirit':
            return fine(ring(180)+shape(3,172)+shape(3,146,1,90))+around(3,bright(line('M0 10C-61 15-93-33-65-65C-37-97 13-63 1-38C-7-21-28-26-29-39'))+
                fine(line('M-94-28C-119-75-62-129-10-104')))+
                around(6,place(0,-163,line('M0-20C-29-9-26 17 0 26C-10 7 10-3 0-20Z','fill="currentColor" fill-opacity=".12"')))+accent(ring(12));
        case 'light-rosette':
            return fine(ring(182)+ring(165)+shape(12,164,5))+bright(rosette(12,164,35))+accent(rosette(12,114,26,15)+star(12,92,46))+
                fine(around(12,place(0,-138,ring(16)))+around(24,line('M0-178V-193')))+ring(25)+rosette(6,40,12);
        case 'dream-constellation': {
            const stars=[[-121,-99,8],[-59,-146,5],[2,-84,10],[111,-123,5],[150,-26,8],[72,16,6],[107,120,9],[-12,156,6],[-91,76,8],[-143,11,4],[-20,24,11]];
            return fine(ring(181,'stroke-dasharray="2 6"')+line('M-121-99-59-146 2-84 111-123 150-26 72 16 107 120-12 156-91 76-143 11-121-99M2-84-20 24 72 16M-91 76-20 24-12 156'))+
                stars.map(([x,y,r])=>place(x,y,star(4,r,r*.25)+ring(r*1.4,'opacity=".3"'))).join('')+
                accent(place(-39,-24,crescent(31))+place(73,-64,crescent(15)))+fine(around(8,place(0,-197,diamond(6,2))));
        }
        case 'twilight-balance':
            return fine(ring(184)+hexagram(171)+ring(158))+bright(ring(124))+line('M0-124A124 124 0 0 1 0 124A62 62 0 0 1 0 0A62 62 0 0 0 0-124Z','fill="currentColor" fill-opacity=".13" stroke-width="1.4"')+
                place(0,-62,ring(22,'fill="currentColor" fill-opacity=".55"')+ring(31,'opacity=".45"'))+
                place(0,62,ring(22,'fill="#041115"')+ring(31,'opacity=".45"'))+smallNodes(4,190,11);
        default: return seal(165);
        }
    }
    function collectionSvg(t,p) {
        const idx = collection.findIndex(entry=>entry[0]===t.id);
        const dense = ['celestial-satellites','crimson-abyss','sage-nexus','grimoire-star','light-rosette'].includes(t.id);
        const outer = ring(277,'stroke-width="4" opacity=".12"')+ring(245,'stroke-width="3" opacity=".09"')+
            ring(277,'stroke-width="1.1"')+ring(272,'stroke-width=".4"')+ring(245,'stroke-width=".7"')+
            group(localRunes(259,72,.8),'class="rune-ring" stroke-width=".9"')+
            ring(233,'stroke-width=".5" opacity=".8"')+ring(223,'stroke-dasharray=".7 4.2" stroke-width="2.8" opacity=".48"')+
            (dense?ring(210,'stroke-width=".45"')+group(localRunes(216,96,.38),'stroke-width=".7" opacity=".65"'):ring(209,'stroke-width=".5" opacity=".5"'))+
            around(8,place(0,-277,diamond(8,3)+'<circle cx="0" cy="0" r="2" fill="'+t.accent+'"/>'),idx%2?22.5:0)+
            around(4,line('M0-286V-296M-4-291H4','opacity=".6"'));
        const dust = Array.from({length:25},(_,i)=>{
            const xy=point(196+i*19%99,i*137.508+idx*7);
            return '<circle class="mote" cx="'+xy[0]+'" cy="'+xy[1]+'" r="'+(i%7===0?1.35:.6)+'" fill="'+t.accent+'" opacity="'+(.2+i%4*.12)+'" style="--delay:-'+i%5+'s"/>';
        }).join('');
        const glow = (x,y,r=36,opacity=1) => place(x,y,ring(r,'fill="url(#'+p+'-radiance)" stroke="none" opacity="'+opacity+'"'));
        const glint = (x,y,r=8) => place(x,y,line('M0 '+(-r)+'Q1-1 '+r+' 0Q1 1 0 '+r+'Q-1 1 '+(-r)+' 0Q-1-1 0 '+(-r)+'Z','fill="#fff8e6" stroke="none"')+ring(1.8,'fill="#ffffff" stroke="none"'));
        const illuminatedNodes = (n,r,sz=28,offset=0) => around(n,glow(0,-r,sz,.8)+glint(0,-r,5),offset);
        let light='';
        switch(t.id) {
        case 'ivory-alchemy': light=group(ring(224),'stroke="#fffdf0" opacity=".6"'); break;
        case 'celestial-satellites':
            light=glow(0,0,76,.9)+illuminatedNodes(6,182,44)+group(hexagram(65)+ring(95),'stroke="#edfaff" stroke-width=".65" opacity=".85"')+glint(0,0,15); break;
        case 'sage-nexus':
            light=glow(0,0,83,.85)+illuminatedNodes(4,184,38)+group(shape(6,73)+hexagram(73),'stroke="#f6faff" stroke-width=".9" opacity=".88"')+glint(0,0,15); break;
        case 'world-tree':
            light=glow(0,-58,105,.4)+glow(0,91,57,.5)+group(line('M0 105Q8 70 0 41M0 40Q-36 18-48-17M0 40Q37 12 49-18'),'stroke="'+t.accent+'" stroke-width="1.1"')+
                [[-91,-30],[-53,-72],[-11,-93],[30,-85],[76,-50],[92,-16]].map(([x,y])=>glow(x,y,24,.55)+glint(x,y,3)).join(''); break;
        case 'healing-wings':
        case 'seraph-wings':
            light=glow(0,t.id==='seraph-wings'?-133:-126,69,.65)+glow(0,0,57,.7)+glint(0,0,14)+
                group(line('M-18 12Q-83-24-165-87M18 12Q83-24 165-87'),'stroke="#fff6ff" stroke-width="1.25" opacity=".9"')+illuminatedNodes(4,189,23); break;
        case 'gravity-well':
            light=group(ring(65,'stroke-width="8" opacity=".12"')+ring(63,'stroke-width=".9" opacity=".9"'),'stroke="#ddbbff"')+
                around(4,glow(0,-73,30,.6),20)+glint(0,-64,5); break;
        case 'all-seeing-eye':
            light=group(ring(42)+line('M-99-1Q0-90 99-1'),'stroke="#fff0ff" stroke-width="1" opacity=".86"')+glow(0,-39,35,.6)+glint(0,-40,6); break;
        case 'lunar-phases':
        case 'crescent-moon':
            light=t.id==='lunar-phases'?illuminatedNodes(8,181,23)+glow(-48,0,58,.6):glow(-40,-43,71,.5)+glow(-111,10,37,.35);
            light+=group(t.id==='lunar-phases'?crescent(67):place(22,-44,crescent(85)),'stroke="#f7f3ff" stroke-width="1.2" opacity=".8"'); break;
        case 'moon-wheel':light=illuminatedNodes(12,181,18)+glow(0,0,43,.6)+glint(0,0,8); break;
        case 'blood-moon':light=illuminatedNodes(4,182,40)+glow(0,0,66,.6)+glint(0,0,8); break;
        case 'twilight-balance':light=glow(0,-62,58,.6)+group(ring(124),'stroke="#edffff" opacity=".8"')+glint(-9,-68,5); break;
        case 'binding-chains':light=illuminatedNodes(4,170,29,45)+glow(0,31,48,.5)+glint(0,21,5); break;
        case 'solar-crown':
        case 'spiral-galaxy':
        case 'light-rosette':light=glow(0,0,118,.9)+glint(0,0,21)+illuminatedNodes(8,176,20,22.5); break;
        case 'sakura-seal':light=glow(0,0,87,.7)+illuminatedNodes(5,185,25,36)+glint(0,0,8); break;
        case 'elemental-concord':light=illuminatedNodes(4,178,43)+glow(0,0,67,.7)+glint(0,0,10); break;
        case 'heart-sanctuary':light=glow(0,-5,79,.7)+group(line('M0 52C-37 20-66-2-56-30C-47-55-16-55 0-31C16-55 47-55 56-30C66-2 37 20 0 52Z'),'stroke="#ffeaf2" stroke-width="1.35"')+glint(0,51,8); break;
        case 'flame-spirit':light=glow(0,25,101,.85)+illuminatedNodes(4,189,28)+group(line('M0 71C-34 38-20 15-5-4'),'stroke="#fff4d3" stroke-width="1.1"'); break;
        case 'water-spirit':light=glow(0,33,81,.5)+illuminatedNodes(4,191,28)+group(line('M-32 0Q-48 38-16 58'),'stroke="#f1ffff" stroke-width="2"'); break;
        case 'jade-lotus':light=glow(0,53,85,.55)+illuminatedNodes(4,189,23)+group(line('M0 96C-56 26-46-58 0-124'),'stroke="#e1fff1" stroke-width="1.1"')+glint(0,96,8); break;
        case 'dream-constellation':
            light=[[-121,-99,8],[-59,-146,5],[2,-84,10],[111,-123,5],[150,-26,8],[72,16,6],[107,120,9],[-12,156,6],[-91,76,8],[-143,11,4],[-20,24,11]]
                .map(([x,y,r])=>glow(x,y,r*4,.7)+glint(x,y,r*.8)).join(''); break;
        case 'infinite-bond':
            light=glow(0,0,72,.7)+group(line('M0 0C-53-68-156-115-163-22C-171 81-62 83 0 0C53-68 156-115 163-22C171 81 62 83 0 0Z'),'stroke="#eaffff" stroke-width=".9" opacity=".82"')+glint(0,0,12); break;
        case 'frost-crystal':
        case 'snowflake':
        case 'hex-lattice':light=glow(0,0,69,.8)+illuminatedNodes(6,185,29)+glint(0,0,11); break;
        default:light=glow(0,0,62,.7)+illuminatedNodes(4,188,25)+glint(0,0,10); break;
        }
        return '<svg class="circle-svg" viewBox="0 0 600 600" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><defs>'+
            '<filter id="'+p+'-glow" x="-15%" y="-15%" width="130%" height="130%"><feGaussianBlur stdDeviation="1.1" result="blur"/><feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge></filter>'+
            '<radialGradient id="'+p+'-aura"><stop stop-color="'+t.accent+'" stop-opacity=".17"/><stop offset=".36" stop-color="'+t.color+'" stop-opacity=".075"/><stop offset="1" stop-color="'+t.color+'" stop-opacity="0"/></radialGradient>'+
            '<radialGradient id="'+p+'-radiance"><stop stop-color="#fffdf3" stop-opacity=".92"/><stop offset=".06" stop-color="'+t.accent+'" stop-opacity=".7"/><stop offset=".2" stop-color="'+t.color+'" stop-opacity=".35"/><stop offset=".53" stop-color="'+t.color+'" stop-opacity=".11"/><stop offset="1" stop-color="'+t.color+'" stop-opacity="0"/></radialGradient></defs>'+
            '<circle cx="300" cy="300" r="247" fill="url(#'+p+'-aura)"/>'+
            '<g transform="translate(300 300)" fill="none" color="'+t.color+'" stroke="'+t.color+'" stroke-width="1.05" stroke-linecap="round" stroke-linejoin="round" filter="url(#'+p+'-glow)">'+outer+
            '<g class="fixed-seals" data-motif="'+t.id+'">'+collectionMotif(t)+'</g>'+light+dust+'</g></svg>';
    }
    function svg(id, prefix) {
        const t = get(id);
        const p = String(prefix || t.id).replace(/[^a-zA-Z0-9_-]/g, '') || 'circle';
        if (t.group === 'collection') return collectionSvg(t,p);
        const circle = (r, attrs = '') => '<circle cx="300" cy="300" r="' + r + '" ' + attrs + '/>';
        const poly = (n,r,s=1,o=-90) => '<polygon points="' + polygon(n,r,s,o) + '"/>';
        let rings = circle(275,'stroke-width="1"') + circle(269,'stroke-width=".35"') + circle(247,'stroke-width=".65"');
        rings += '<g class="rune-ring ' + (t.turn ? 'ring-turn' : '') + '" stroke-width=".85">' + runeRing(258,64) + '</g>';
        if (t.rings >= 3) rings += '<g stroke="' + t.accent + '" opacity=".75">' + circle(232,'stroke-width=".65"') + circle(223,'stroke-dasharray=".7 5.15" stroke-width="5"') + circle(216,'stroke-width=".45"') + '</g>';
        if (t.rings >= 4) rings += '<g class="' + (t.turn ? 'ring-reverse' : '') + '" stroke="' + t.accent + '" opacity=".6" stroke-width=".5">' + runeRing(204,80,2) + circle(194) + '</g>';
        rings += circle(284,'stroke-width=".35" opacity=".5" stroke-dasharray="1 5"');
        if (t.turn) rings += '<g class="ring-turn" stroke-width="2.6" opacity=".55">' + circle(278,'stroke-dasharray="80 357" transform="rotate(25 300 300)"') + '</g>';
        const compass = [0,90,180,270].map(angle => '<g transform="rotate(' + angle + ' 300 300)"><path d="M300 9V103M287 25H313" opacity=".6"/><path d="M300 14 305 25 300 36 295 25Z" fill="' + t.color + '"/><circle cx="300" cy="93" r="2" fill="' + t.accent + '"/></g>').join('');
        let seals;
        if (t.core === 'classic') {
            const points = Array.from({length:16},(_,i)=>{
                const a = (i*22.5-90)*Math.PI/180, r=i%2 ? 83 : 200;
                return (300+Math.cos(a)*r).toFixed(2)+','+(300+Math.sin(a)*r).toFixed(2);
            }).join(' ');
            seals = '<polygon points="'+points+'" stroke-width="1.8"/><g stroke="'+t.accent+'" stroke-width="1.3">'+poly(3,158)+poly(3,158,1,90)+circle(130)+'</g>';
        } else {
            seals = '<g stroke-width=".65" opacity=".8">'+poly(8,190,3)+poly(4,190)+poly(4,148,1,-45)+'</g>';
            seals += '<g stroke="'+t.accent+'" stroke-width=".8">'+poly(6,75)+poly(3,75)+poly(3,75,1,90)+poly(4,59,1,-45)+poly(4,72)+'</g>';
            seals += '<g opacity=".4" stroke-width=".45">'+poly(8,102,3)+circle(115,'stroke-dasharray="1 4"')+'</g>';
        }
        let orbit = '';
        for (let i=0; i<t.orbits; i++) {
            const color = i%2 ? t.color : t.accent, angle = t.orbits===2 ? [-30,45][i] : [0,60,120][i];
            orbit += '<g transform="rotate('+angle+' 300 300)" stroke="'+color+'"><ellipse cx="300" cy="300" rx="201" ry="72" opacity=".25" stroke-width="5"/><ellipse cx="300" cy="300" rx="201" ry="72" opacity=".8" stroke-width=".9"/><circle cx="501" cy="300" r="6" fill="url(#'+p+'-orb)" stroke-width=".75"><animateMotion class="orbit-motion" begin="indefinite" dur="'+(14+i*3)+'s" repeatCount="indefinite" path="M0 0a201 72 0 1 1-402 0a201 72 0 1 1 402 0"/></circle></g>';
        }
        const dust = t.quiet ? '' : Array.from({length:42},(_,i)=>{
            const a = i*2.399963, r=96+(i*47)%203, x=300+Math.cos(a)*r, y=300+Math.sin(a)*r;
            return '<circle class="mote" cx="'+x.toFixed(2)+'" cy="'+y.toFixed(2)+'" r="'+(i%5===0?1.4:.6)+'" fill="'+(i%3?t.color:t.accent)+'" opacity="'+(.2+i%4*.15)+'" style="--delay:-'+i%5+'s"/>';
        }).join('');
        return '<svg class="circle-svg" viewBox="0 0 600 600" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><defs>'+
            '<filter id="'+p+'-glow" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="1.5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>'+
            '<radialGradient id="'+p+'-orb"><stop stop-color="#fffdf0"/><stop offset=".45" stop-color="'+t.accent+'"/><stop offset="1" stop-color="'+t.color+'" stop-opacity=".25"/></radialGradient>'+
            '<radialGradient id="'+p+'-core"><stop stop-color="#fffcea"/><stop offset=".12" stop-color="'+t.color+'" stop-opacity=".95"/><stop offset=".4" stop-color="'+t.color+'" stop-opacity=".2"/><stop offset="1" stop-color="'+t.color+'" stop-opacity="0"/></radialGradient></defs>'+
            '<circle cx="300" cy="300" r="240" fill="url(#'+p+'-core)" opacity="'+(t.halo?.22:.065)+'"/>'+
            '<g fill="none" stroke="'+t.color+'" stroke-linecap="round" stroke-linejoin="round" filter="url(#'+p+'-glow)">'+rings+'<g stroke-width=".65">'+compass+'</g><g class="fixed-seals">'+seals+'</g>'+orbit+'</g>'+dust+
            (t.core==='battery' ? '<circle cx="300" cy="300" r="86" fill="#050b10" stroke="'+t.accent+'" stroke-width="1.5"/><circle cx="300" cy="300" r="91" fill="none" stroke="'+t.color+'" opacity=".45" stroke-dasharray="1 4"/>' : '<circle class="core-glow" cx="300" cy="300" r="'+(t.core==='flare'?100:t.quiet?13:30)+'" fill="url(#'+p+'-core)"/><path d="M300 288V312M288 300H312" stroke="'+t.accent+'" opacity=".8" stroke-width=".7"/>')+'</svg>';
    }
    const api = {list, get, battery, svg};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    else root.CircleDesigns = api;
})(typeof globalThis === 'undefined' ? this : globalThis);
