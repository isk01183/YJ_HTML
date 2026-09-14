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
    const get = id => list.find(theme => theme.id === id) || list[list.length - 1];
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
    function svg(id, prefix) {
        const t = get(id);
        const p = String(prefix || t.id).replace(/[^a-zA-Z0-9_-]/g, '') || 'circle';
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
