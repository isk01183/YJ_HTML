(function (root) {
    'use strict';
    // Coordinates only: the original reference JPEGs are displayed without re-encoding.
    // Preserve each viewport's aspect ratio; the sheet artwork is not always square.
    const sheets = [
        {
            sheet: 2,
            // On this sheet captions overlap the lower rune strokes. Keep the full
            // panel contents rather than cutting the artwork to remove its lettering.
            columns: [[24,192],[231,193],[440,194],[649,184],[848,195],[1061,195]],
            rows: [[55,147],[212,144],[367,144],[519,139],[667,138]],
            names: [
                ['리무르의 마법진','リムルの魔法陣','Rimuru’s Magic Circle'],
                ['심연의 붉은 마법진','深淵の紅き魔法陣','Crimson Circle of the Abyss'],
                ['고전적 마법진','古典の魔法陣','Classical Magic Circle'],
                ['연금술 마법진','錬金術の魔法陣','Alchemy Circle'],
                ['생명의 상징','生命の象徴','Symbol of Life'],
                ['황금의 별','黄金の星','Golden Star'],
                ['태양과 달의 조화','太陽と月の調和','Harmony of Sun and Moon'],
                ['치유의 마법진','癒やしの魔法陣','Healing Circle'],
                ['천체의 순환','天体の循環','Celestial Cycle'],
                ['결정의 구조','結晶の構造','Crystal Structure'],
                ['원자의 궤도','原子の軌道','Atomic Orbits'],
                ['심층 분석','深層解析','Deep Analysis'],
                ['자연의 순환','自然の循環','Cycle of Nature'],
                ['시간의 톱니바퀴','時の歯車','Gears of Time'],
                ['공간의 인도','空間の導き','Guidance through Space'],
                ['물의 정령','水の精霊','Water Spirit'],
                ['불의 정령','火の精霊','Fire Spirit'],
                ['바람의 정령','風の精霊','Wind Spirit'],
                ['대지의 정령','大地の精霊','Earth Spirit'],
                ['빛의 정령','光の精霊','Light Spirit'],
                ['어둠의 정령','闇の精霊','Darkness Spirit'],
                ['정신의 영역','精神の領域','Realm of the Mind'],
                ['다차원 분석','多次元解析','Multidimensional Analysis'],
                ['무한의 지식','無限の知識','Infinite Knowledge'],
                ['금기의 마법진','禁忌の魔法陣','Forbidden Circle'],
                ['이중성의 균형','二面性の均衡','Balance of Duality'],
                ['세계의 연결','世界の繋がり','Connection of Worlds'],
                ['꿈의 세계','夢の世界','World of Dreams'],
                ['관측과 예측','観測と予測','Observation and Prediction'],
                ['대현자 · 라파엘 종합형','大賢者・ラファエル統合型','Great Sage · Raphael Synthesis']
            ]
        },
        {
            sheet: 3,
            columns: [[14,196],[227,192],[436,194],[647,195],[860,193],[1070,194]],
            rows: [[94,178],[307,178],[520,183],[736,181],[951,175]],
            names: [
                ['애니 스타일','アニメスタイル','Anime Style'],
                ['레드 마법진','紅の魔法陣','Red Magic Circle'],
                ['영문 문구형','英字銘文型','Inscribed Lettering'],
                ['삼각 구조형','三角構造型','Triangular Structure'],
                ['상징 중심형','象徴中心型','Central Emblem'],
                ['옐로우 스타형','黄金の星型','Golden Star Form'],
                ['힐링 상징형','癒やしの象徴型','Healing Emblem'],
                ['태양·달 조합형','太陽と月の融合型','Sun and Moon Fusion'],
                ['달과 태양형','月と太陽型','Moon and Sun'],
                ['블루 육각형','蒼の六角型','Blue Hexagon'],
                ['리무르 코어형','リムルの核型','Rimuru Core'],
                ['원자 궤도형','原子軌道型','Atomic Orbit Form'],
                ['다층 육망성형','多層六芒星型','Layered Hexagrams'],
                ['방사형 스타형','放射星型','Radiant Star'],
                ['마도서 스타일','魔導書スタイル','Grimoire Style'],
                ['게이트 회로형','ゲート回路型','Gate Circuit'],
                ['자연 마력형','自然魔力型','Nature’s Mana'],
                ['월식형','月食型','Lunar Eclipse'],
                ['전지의 눈형','全知の眼型','All-Seeing Eye'],
                ['시공간형','時空型','Spacetime'],
                ['나선 은하형','螺旋銀河型','Spiral Galaxy'],
                ['수호 마법진','守護の魔法陣','Guardian Circle'],
                ['빙결 마법진','氷結の魔法陣','Frost Circle'],
                ['정화형','浄化型','Purification'],
                ['화염 마법진','炎の魔法陣','Flame Circle'],
                ['수폭 마법진','水瀑の魔法陣','Water Vortex Circle'],
                ['시간의 흐름형','時の流れ型','Flow of Time'],
                ['중력 제어형','重力制御型','Gravity Control'],
                ['벚꽃 마법진','桜の魔法陣','Cherry Blossom Circle'],
                ['모든 속성 통합형','全属性統合型','All Elements United']
            ]
        },
        {
            sheet: 4,
            columns: [[11,148],[171,147],[328,148],[486,148],[645,148],[805,147],[964,147],[1122,147]],
            rows: [[60,149],[240,151],[420,153],[604,153]],
            names: [
                ['기본형 · 정적','基本形・静止','Basic · Still'],
                ['심플 블루','シンプルブルー','Simple Blue'],
                ['라파엘 스타일','ラファエルスタイル','Raphael Style'],
                ['고정밀 버전','高精細版','Precision Circle'],
                ['회전 링 버전','回転環','Orbiting Rings'],
                ['홀로그램 스타일','ホログラムスタイル','Hologram Style'],
                ['코어 발광','核の輝き','Glowing Core'],
                ['미니멀 버전','ミニマル','Minimal Circle'],
                ['애니메이션 스타일','アニメーションスタイル','Animation Style'],
                ['리무르 스킬풍','リムルのスキル風','Rimuru Skill Style'],
                ['다층 링 구조','多層環構造','Layered Ring Structure'],
                ['정령 계약진','精霊契約陣','Spirit Pact Circle'],
                ['태양의 마법진','太陽の魔法陣','Solar Circle'],
                ['달의 마법진','月の魔法陣','Lunar Circle'],
                ['별의 인도','星の導き','Stellar Guidance'],
                ['시간의 톱니','時の歯車','Clockwork of Time'],
                ['연금술 마법진','錬金術の魔法陣','Alchemy Circle'],
                ['4속성 조화','四属性の調和','Four-Element Harmony'],
                ['신성 교회풍','聖堂様式','Sacred Cathedral'],
                ['천사의 인장','天使の印章','Angelic Seal'],
                ['악마의 계약','悪魔との契約','Demonic Pact'],
                ['봉인의 마법진','封印の魔法陣','Seal of Binding'],
                ['보호 결계진','守護結界陣','Protective Barrier'],
                ['공간 전이진','空間転移陣','Spatial Gate'],
                ['생명의 나무','生命の樹','Tree of Life'],
                ['운명의 수레바퀴','運命の輪','Wheel of Fate'],
                ['심판의 눈','審判の眼','Eye of Judgment'],
                ['무한의 고리','無限の輪','Infinite Loop'],
                ['원소 융합진','元素融合陣','Elemental Fusion'],
                ['최종 형태 · 대현자','最終形態・大賢者','Final Form · Great Sage']
            ]
        }
    ];
    const catalog = sheets.flatMap(function (sheet) {
        return sheet.names.map(function (names, i) {
            const column = sheet.columns[i % sheet.columns.length];
            const row = sheet.rows[Math.floor(i / sheet.columns.length)];
            return {id: 'ref-' + sheet.sheet + '-' + String(i + 1).padStart(2, '0'),
                sheet: sheet.sheet, index: i + 1, rect: [column[0], row[0], column[1], row[1]],
                names: {ko: names[0], ja: names[1], en: names[2]}};
        });
    });
    root.ReferenceCatalog = catalog;
    if (typeof module !== 'undefined' && module.exports) module.exports = catalog;
})(typeof globalThis !== 'undefined' ? globalThis : this);
