(function(root){
  'use strict';
  // IDs follow the supplied reference catalog, not the older procedural designs.
  const groups={
    C:[
      ['기본형 · 로즈 룬','基本形 · ローズルーン','Rose Runes'],
      ['심플 블루','シンプルブルー','Simple Blue'],
      ['라파엘 · 궤도','ラファエル · 軌道','Raphael · Orbits'],
      ['실버 · 고정밀','シルバー · 精密','Fine Silver'],
      ['바이올렛 · 링','バイオレット · リング','Violet Rings'],
      ['시안 · 홀로그램','シアン · 幻影','Cyan Illusion'],
      ['골드 · 코어 발광','ゴールド · 光の核','Golden Core'],
      ['미니멀 실버','ミニマルシルバー','Minimal Silver'],
      ['블루 · 궤도','ブルー · 軌道','Blue Orbits'],
      ['리무르 스킬풍','リムルの術式','Rimuru’s Spell'],
      ['레드 · 다중 링','レッド · 多重リング','Crimson Rings'],
      ['정령 계약진','精霊の契約','Spirit Covenant'],
      ['태양의 마법진','太陽の魔法陣','Solar Circle'],
      ['달의 마법진','月の魔法陣','Lunar Circle'],
      ['별의 인도','星の導き','Guiding Stars'],
      ['시간의 톱니','時の歯車','Gears of Time'],
      ['연금술 마법진','錬金術の魔法陣','Alchemy Circle'],
      ['4속성 조화','四元素の調和','Fourfold Harmony'],
      ['신성 문장','神聖な紋章','Sacred Emblem'],
      ['천사의 인장','天使の印','Angelic Seal'],
      ['악마의 계약','悪魔の契約','Infernal Covenant'],
      ['봉인의 마법진','封印の魔法陣','Sealing Circle'],
      ['보호 결계진','守護結界','Protective Ward'],
      ['공간 전이진','空間転移','Spatial Gate'],
      ['생명의 나무','生命の樹','Tree of Life'],
      ['운명의 수레바퀴','運命の輪','Wheel of Destiny'],
      ['심판의 눈','審判の眼','Eye of Judgment'],
      ['무한의 고리','無限の環','Infinite Ring'],
      ['원소 융합진','元素融合','Elemental Fusion'],
      ['대현자 · 최종형','大賢者 · 最終形','Great Sage · Final Form']
    ],
    W:[
      ['천상 · 민트빛 세계수','天上 · ミントの世界樹','Celestial Mint World Tree'],
      ['달빛 · 꽃의 세계수','月光 · 花の世界樹','Moonblossom World Tree'],
      ['천체 · 금빛과 푸른 세계수','星空 · 金と青の世界樹','Astral Gold and Azure Tree'],
      ['사계절 세계수','四季の世界樹','Four Seasons World Tree'],
      ['천상 · 수정 세계수','天上 · 水晶の世界樹','Celestial Crystal World Tree']
    ],
    F:[
      ['기본 · 마도 발동','基本 · 魔術発動','Arcane Awakening'],
      ['신성 · 축복','神聖 · 祝福','Sacred Blessing'],
      ['어둠 · 금지된 마법','闇 · 禁じられた魔法','Forbidden Darkness'],
      ['자연 · 치유','自然 · 癒し','Nature’s Healing'],
      ['우주 · 천체','宇宙 · 天体','Cosmic Celestials']
    ],
    A:[
      ['리무르의 마법진','リムルの魔法陣','Rimuru’s Circle'],
      ['심연의 붉은 마법진','深淵の赤い魔法陣','Crimson Abyss'],
      ['고전식 마법진','古典の魔法陣','Classical Circle'],
      ['연금술 마법진','錬金術の魔法陣','Alchemical Emblem'],
      ['생명의 상징','生命の象徴','Symbol of Life'],
      ['황금의 별','黄金の星','Golden Star'],
      ['태양과 달의 조화','太陽と月の調和','Sun and Moon Harmony'],
      ['치유의 마법진','癒しの魔法陣','Healing Circle'],
      ['천체의 순환','天体の循環','Celestial Cycle'],
      ['결정의 구조','結晶の構造','Crystal Structure'],
      ['원자의 궤도','原子の軌道','Atomic Orbits'],
      ['심층 분석','深層解析','Deep Analysis'],
      ['자연의 순환','自然の循環','Nature’s Cycle'],
      ['시간의 톱니바퀴','時の歯車','Clockwork of Time'],
      ['공간의 인도','空間の導き','Spatial Guidance'],
      ['물의 정령','水の精霊','Water Spirit'],
      ['불의 정령','火の精霊','Fire Spirit'],
      ['바람의 정령','風の精霊','Wind Spirit'],
      ['대지의 정령','大地の精霊','Earth Spirit'],
      ['빛의 정령','光の精霊','Light Spirit'],
      ['어둠의 정령','闇の精霊','Dark Spirit'],
      ['정신의 영역','精神の領域','Realm of the Mind'],
      ['마차원 분석','魔次元解析','Arcane Dimension'],
      ['무한의 지식','無限の知識','Infinite Knowledge'],
      ['금기의 마법진','禁忌の魔法陣','Forbidden Circle'],
      ['이중성의 균형','二元の均衡','Balance of Duality'],
      ['세계의 연결','世界の繋がり','Worlds Connected'],
      ['푸른 별의 세계','青い星の世界','World of Blue Stars'],
      ['관측과 예측','観測と予測','Observe and Foresee'],
      ['대현자 · 라파엘 종합형','大賢者 · ラファエル','Great Sage · Raphael']
    ],
    B:[
      ['애니메이션풍 · 블루 연결진','青い連結陣','Azure Constellation'],
      ['레드 · 장식 마법진','赤い装飾魔法陣','Ornate Crimson'],
      ['고전 · 문자환','古典 · 文字の環','Classical Rune Ring'],
      ['삼각 구조형','三角の構造','Triangular Seal'],
      ['상징 중심형','象徴の中心','Emblem Heart'],
      ['옐로우 스타형','黄金の星形','Golden Starform'],
      ['힐링 상징형','癒しの象徴','Healing Emblem'],
      ['태양과 달 조화형','日月の調和','Solar and Lunar Harmony'],
      ['달과 태양형','月と太陽','Moon and Sun'],
      ['청록 육각형','青緑の六角形','Turquoise Hexagon'],
      ['리무르 코어형','リムルの核','Rimuru’s Core'],
      ['원자 궤도형','原子軌道','Atomic Orbital'],
      ['다층 육망성형','多層の六芒星','Layered Hexagrams'],
      ['방사형 스타형','放射の星','Radiant Star'],
      ['마도서 스타일','魔導書の術式','Grimoire Seal'],
      ['에테르 회로형','エーテル回路','Aether Circuit'],
      ['자연 마력형','自然の魔力','Nature’s Mana'],
      ['월식형','月蝕','Lunar Eclipse'],
      ['전지의 눈형','全知の眼','All-Seeing Eye'],
      ['시공간형','時空','Spacetime'],
      ['나선 은하형','螺旋銀河','Spiral Galaxy'],
      ['수호 마법진','守護の魔法陣','Guardian Circle'],
      ['빙결 마법진','氷結の魔法陣','Frozen Circle'],
      ['정화형','浄化','Purification'],
      ['화염 마법진','炎の魔法陣','Flame Circle'],
      ['수폭 마법진','水の渦の魔法陣','Water Vortex'],
      ['시간의 흐름형','時の流れ','Flow of Time'],
      ['중력 제어형','重力制御','Gravity Control'],
      ['벚꽃 마법진','桜の魔法陣','Cherry Blossom Circle'],
      ['모든 속성 통합형','全属性の統合','All Elements United']
    ],
    G:[
      ['얼음의 마법진','氷の魔法陣','Ice Circle'],
      ['빛의 마법진','光の魔法陣','Light Circle'],
      ['어둠의 마법진','闇の魔法陣','Dark Circle'],
      ['자연의 마법진','自然の魔法陣','Nature Circle'],
      ['우주의 마법진','宇宙の魔法陣','Cosmic Circle']
    ],
    E:[
      ['기본형 · 정적','基本形 · 静寂','Still Rose'],
      ['심플 블루','シンプルブルー','Simple Azure'],
      ['라파엘 스타일 · 궤도','ラファエル · 軌道','Raphael Orbits'],
      ['고정밀 버전','精密の術式','Fine Geometry'],
      ['회전 링 버전','重なる環','Layered Rings'],
      ['홀로그램 스타일','幻影の術式','Aqua Illusion'],
      ['코어 발광 버전','輝く核','Radiant Core'],
      ['미니멀 버전','ミニマル','Minimal Rune']
    ],
    U:[
      ['미니멀 룬 서클','ミニマルルーンサークル','Minimal Rune Circle'],
      ['라파엘 HUD','ラファエルの叡智','Raphael’s Wisdom'],
      ['멀티 레이어','多層の環','Layered Rose'],
      ['프리미엄 성역','至高の聖域','Premium Sanctuary']
    ],
    R:[['육엽 마법진 · 기준 선화','六葉の魔法陣 · 線画','Sixfold Circle · Line Art']]
  };
  const desc={ko:'첨부 도안의 형태와 색을 보존한 벡터 마법진입니다. 도형은 고정하고 빛과 입자가 은은하게 나타납니다.',ja:'参照図の形と色を保つベクター魔法陣。図形を固定し、光と粒子が静かに現れます。',en:'Vector artwork preserving the reference composition and color. The geometry stays still as light and particles gently appear.'};
  const overlapped={ko:'원본 하단 제목이 일부 도형과 겹쳐 남아 있습니다. 가려진 룬과 획은 임의로 복원하지 않았습니다.',ja:'元の下部タイトルが図形と重なって残っています。隠れたルーンや線は推測で復元していません。',en:'The original bottom caption overlaps some geometry and remains visible. Hidden runes and strokes have not been invented.'};
  const list=Object.entries(groups).flatMap(([group,rows])=>rows.map(([ko,ja,en],i)=>{
    const code=group+String(i+1).padStart(2,'0');
    const descriptions=group==='A'?overlapped:desc;
    return {id:'ref-'+code,code,group:'reference',name:code+' · '+ko,names:{ko:code+' · '+ko,ja:code+' · '+ja,en:code+' · '+en},desc:descriptions.ko,descriptions,color:'#cbdce9',portrait:group==='W',thumb:'collection/thumbs/'+code+'.png'};
  }));
  const byId=new Map(list.map(x=>[x.id,x]));
  const api={list,get:id=>byId.get(id)||null};
  if(typeof module!=='undefined'&&module.exports)module.exports=api;else root.ReferenceDesigns=api;
})(globalThis);
