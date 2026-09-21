(function (root) {
    'use strict';
    // Bundled translations: the app never needs a network request or a downloaded font.
    const strings = {};
    const add = (key, ja, ko, en) => { strings[key] = {ja, ko, en}; };
    add('galleryTitle', '魔法陣の書庫', '마법진 보관함', 'The Arcane Archive');
    add('brand', '魔法陣コレクション', '마법진 컬렉션', 'ARCANE COLLECTION');
    add('settings', '接続設定 ↗', '연결 설정 ↗', 'Setup ↗');
    add('intro', '充電のひとときに、あなたの術式を。\n好きな魔法陣を選んで、光の演出をお楽しみください。', '충전이 시작되는 순간, 나만의 마법을.\n마음에 드는 마법진을 골라 빛의 연출을 즐겨 보세요.', 'A little magic, every time you connect.\nChoose a circle and let its light unfold.');
    add('language', '表示言語', '표시 언어', 'Language');
    add('serviceCheck', 'アプリで接続設定を確認', '앱에서 연결 설정 확인', 'Check setup in the app');
    add('serviceOn', '充電アニメーション：有効', '충전 애니메이션: 켜짐', 'Charging animation: on');
    add('serviceOff', '充電アニメーション：無効', '충전 애니메이션: 꺼짐', 'Charging animation: off');
    add('maxTime', '最大7秒', '최대 7초', 'Up to 7 seconds');
    add('offline', 'オフライン', '오프라인', 'Offline');
    add('selectedCircle', '選択した魔法陣', '선택한 마법진', 'Selected magic circle');
    add('serviceHint', '充電時の表示には、ユーザー補助サービスを有効にしてください。上の「接続設定」から設定できます。', '충전할 때 표시하려면 접근성 서비스를 켜 주세요. 위의 ‘연결 설정’에서 설정할 수 있습니다.', 'Enable the accessibility service to show the animation when charging. Open Setup above to enable it.');
    add('choose', '術式を選ぶ', '마법진 선택', 'Choose your circle');
    add('designCount', '{count} 種の術式', '마법진 {count}종', '{count} designs');
    add('categories', 'デザインの分類', '디자인 분류', 'Design categories');
    add('all', 'すべて', '전체', 'All');
    add('reference', '参照コレクション', '첨부 도안', 'Reference collection');
    add('directReview', '手描きパス · 確認用', '직접 그리기 · 검토용', 'Direct paths · Review');
    add('legacyReview', '既存の図案 · 再制作待ち', '기존 도안 · 직접 그리기 대기', 'Legacy art · Redraw pending');
    add('directReviewHint', 'コードを指定して、形・色・光の修正を依頼できます。参照図と同一と認定された完成版ではありません。', '수정할 부분과 도안 코드를 함께 알려 주세요. 원본 동일성이 확정된 완성본은 아닙니다.', 'Use the design code when requesting shape, color or light corrections. Reference fidelity has not been approved.');
    add('nativeName', '星を読む聖域', '별을 읽는 성역', 'Sanctuary of Stars');
    add('nativeDescription', '星空と魔法陣を端末で描画し、電池の状態を表示します。', '별빛 배경과 마법진을 기기에서 직접 그려 배터리 상태를 보여 줍니다.', 'A device-rendered star field and magic circle show your battery status.');
    add('nativeLabel', 'ネイティブ描画', '네이티브 화면', 'Native rendering');
    add('nativePreviewHint', 'ネイティブ画面はアプリのプレビューで確認', '네이티브 화면은 앱의 미리보기에서 확인', 'View the native screen in the app preview');
    add('collection', '幻想の書', '환상의 서', 'Fantasy collection');
    add('signature', '代表作', '대표작', 'Signature');
    add('variation', '変奏', '변주', 'Variations');
    add('classic', '従来', '기존', 'Classic');
    add('search', '魔法陣の名前を検索', '마법진 이름 검색', 'Search magic circles');
    add('noResults', '該当する魔法陣はありません。', '검색 결과가 없습니다.', 'No circles found.');
    add('note', '中央の星形は回転しません。外周の光と粒子が静かに巡ります。\n端子を外すと終了し、再接続すると最初から再生します。', '가운데 별은 고정되고 바깥 빛과 입자가 은은하게 움직입니다.\n충전기를 분리하면 종료되며, 다시 연결하면 처음부터 재생됩니다.', 'The central stars stay still while outer lights and particles drift gently.\nDisconnect to dismiss; reconnect to play again from the beginning.');
    add('preview', '▷ 7秒プレビュー', '▷ 7초 미리보기', '▷ 7-second preview');
    add('apply', 'この魔法陣を適用', '이 마법진 적용', 'Apply this circle');
    add('applied', '✓ 適用済み', '✓ 적용됨', '✓ Applied');
    add('appliedBadge', '適用中', '적용 중', 'Active');
    add('saved', '✓ 適用中の魔法陣', '✓ 현재 적용된 마법진', '✓ Your active circle');
    add('pending', '選択中 · 適用すると次の充電から反映', '선택 중 · 적용하면 다음 충전부터 표시', 'Selected · apply for your next charge');
    add('current', '適用中：{name}', '적용 중: {name}', 'Active: {name}');
    add('select', '{name}を選択', '{name} 선택', 'Select {name}');
    add('browserOnly', 'ブラウザ確認用 · アプリへの保存は行われません', '브라우저 미리보기 · 앱 설정에는 저장되지 않습니다', 'Browser preview · does not save app settings');
    add('appSettings', '接続設定はAndroidアプリで開いてください', '연결 설정은 Android 앱에서 열어 주세요', 'Open Setup in the Android app');
    add('close', '閉じる ×', '닫기 ×', 'Close ×');
    add('previewTitle', '魔法陣プレビュー', '마법진 미리보기', 'Magic circle preview');
    add('animationTitle', '魔法陣充電演出', '마법진 충전 애니메이션', 'Magic Circle Charging');
    add('detected', '― 魔力の流入を確認 ―', '― 마력의 유입을 확인 ―', '— An influx of mana detected —');
    add('connected', '充電端子が\n接続されました', '충전 단자가\n연결되었습니다', 'Charger\nconnected');
    add('deploy', '― 術式を展開 ―', '― 술식을 전개 ―', '— Unfolding the magic circle —');
    add('begin', '充電開始', '충전 시작', 'Charging begins');
    add('wisdom', '智慧は、明日を照らす。', '지혜는 내일을 비춘다.', 'Wisdom lights tomorrow.');
    add('blessing', '智慧は、より明るい\n明日を照らす。', '지혜는 더 밝은\n내일을 비춘다.', 'KNOWLEDGE ILLUMINATES\nA BRIGHTER TOMORROW');
    add('circuit', '魔力回路', '마력 회로', 'Mana circuit');
    add('circuitConnecting', '魔力回路 接続中', '마력 회로 연결 중', 'Connecting the mana circuit');
    add('complete', '接続完了', '연결 완료', 'Connection complete');
    add('temperature', 'バッテリー温度', '배터리 온도', 'Temperature');
    add('health', 'バッテリー状態', '배터리 상태', 'Battery health');
    add('connection', '接続方式', '연결 방식', 'Connection');
    add('unknown', '不明', '알 수 없음', 'Unknown');
    add('good', '良好', '양호', 'Good');
    add('hot', '高温', '고온', 'Overheated');
    add('degraded', '劣化', '성능 저하', 'Degraded');
    add('overvoltage', '過電圧', '과전압', 'Overvoltage');
    add('failure', '異常', '이상', 'Fault');
    add('cold', '低温', '저온', 'Cold');
    add('disconnected', '未接続', '연결되지 않음', 'Disconnected');
    add('charged', '充電完了', '충전 완료', 'Fully charged');
    add('charging', '充電中', '충전 중', 'Charging');
    add('waiting', '充電待機', '충전 대기', 'Waiting to charge');
    add('unknownStatus', '状態不明', '상태를 알 수 없음', 'Status unknown');
    add('wireless', 'ワイヤレス', '무선', 'Wireless');
    add('dock', 'ドック', '도크', 'Dock');
    add('runeVerse', '古き星の記憶　生命の環　光よ満ちよ　魂の灯火　', '고대 별의 기억　생명의 고리　빛이여 가득 차라　영혼의 등불　', 'MEMORY OF ANCIENT STARS · CIRCLE OF LIFE · LET LIGHT ABOUND · LANTERN OF THE SOUL · ');
    add('moonGlyph', '月', '달', 'MOON');
    add('fireGlyph', '火', '불', 'FIRE');
    add('starGlyph', '星', '별', 'STAR');
    add('windGlyph', '風', '바람', 'WIND');
    add('yourMedia', 'あなただけの光を', '나만의 빛을 담아 보세요', 'Make this moment yours');
    add('mediaHint', 'GIF · PNG · JPG / 端末内に保存、オフライン再生', 'GIF · PNG · JPG / 기기에만 저장 · 오프라인 재생', 'GIF · PNG · JPG / saved on your device, played offline');
    add('importMedia', '＋ ファイルを追加', '＋ 파일 가져오기', '＋ Import a file');
    add('uploads', 'マイファイル', '내 파일', 'My files');
    add('deleteDesign', '選択項目を削除', '선택 항목 삭제', 'Remove selected');
    add('restoreDesigns', '標準デザインを復元', '기본 마법진 복원', 'Restore built-in designs');
    add('removeConfirm', 'この項目を一覧から削除しますか？ 元の写真は削除されません。', '이 항목을 목록에서 삭제할까요? 원본 사진은 삭제되지 않습니다.', 'Remove this item from your library? The original photo will not be deleted.');
    add('nothingSelected', '表示する項目がありません', '선택된 항목이 없습니다', 'No design selected');
    add('emptyLibrary', 'ファイルを追加するか、標準デザインを復元してください。', '파일을 가져오거나 기본 마법진을 복원해 주세요.', 'Import a file or restore the built-in designs.');
    add('mediaStill', '元の比率を保ち、ゆっくり現れて静かに消えます。', '원래 비율을 유지하며 부드럽게 나타났다가 사라집니다.', 'Your image gently appears and fades away, keeping its original proportions.');
    add('mediaAnimated', '接続するたび、GIFを最初から再生します。', '충전기를 연결할 때마다 GIF를 처음부터 재생합니다.', 'Your GIF starts fresh every time you connect.');
    add('mediaLoading', 'ファイルを準備中', '파일 준비 중', 'Preparing your image');
    add('mediaFailed', 'ファイルを読み込めませんでした', '파일을 불러오지 못했습니다', 'Unable to load this image');
    add('libraryUnavailable', '一覧を読み込めません。保護のためファイルは変更していません。', '목록을 읽을 수 없습니다. 파일 보호를 위해 변경하지 않았습니다.', 'The library cannot be read. Your files have been left unchanged for safety.');

    const originalThemes = {
        moon:{ko:['달그림자의 룬','은은한 장밋빛 선과 달을 맴도는 빛의 궤도. 고요한 빛으로 충전의 시작을 알립니다.'],en:['Moonlit Runes','Fine rose lines and lunar orbits announce each charge with a quiet glow.']},
        raphael:{ko:['지혜의 대현자','금빛과 푸른빛의 이중 룬, 세 개의 빛 궤도. 기하학적 결정에 지혜의 빛이 모입니다.'],en:['The Great Sage','Gold and azure runes surround three luminous orbits and a crystalline heart of wisdom.']},
        layered:{ko:['겹겹의 마법 고리','장밋빛 룬 고리가 천천히 교차합니다. 가운데 별은 고정된 채 마력이 흐릅니다.'],en:['Layered Arcana','Rose rune rings drift slowly around a still central star.']},
        premium:{ko:['별을 읽는 성역','백금빛과 달빛의 성역. 가운데에 배터리 잔량을, 아래에는 온도·상태·연결 방식을 표시합니다.'],en:['Stargazer’s Sanctuary','Platinum and moonlight frame the battery level, with temperature, health and connection below.']},
        basic:{ko:['태초의 붉은빛','붉은 룬과 고정된 별을 조합한 단정한 기본 술식입니다.'],en:['Primordial Crimson','A restrained original seal of crimson runes and an unchanging star.']},
        blue:{ko:['창공의 술식','푸른 세선이 그려 내는 맑은 밤하늘의 마법진입니다.'],en:['Azure Firmament','Cool blue linework traces a circle as clear as the night sky.']},
        gold:{ko:['황금의 예지','황금 룬과 세 개의 빛 구슬. 느린 궤도가 중심의 결정을 감쌉니다.'],en:['Golden Wisdom','Golden runes and three luminous spheres follow gentle paths around a crystal.']},
        silver:{ko:['은빛 별자리 지도','은빛의 정밀한 눈금과 겹겹의 별자리. 섬세한 선들이 고요하게 빛납니다.'],en:['Silver Star Atlas','Delicate silver markings and layered geometry form a quietly radiant star chart.']},
        violet:{ko:['보랏빛 윤무','보랏빛 바깥 고리만 천천히 흐르고, 부드러운 빛이 고정된 별을 감쌉니다.'],en:['Violet Reverie','A violet outer ring drifts slowly, wrapping a fixed star in soft light.']},
        cyan:{ko:['물거울의 결계','투명한 청록빛 층과 가느다란 궤도. 잔잔한 물결 같은 깊이를 가진 결계입니다.'],en:['Watermirror Ward','Translucent aqua layers and delicate orbits create the depth of still water.']},
        core:{ko:['별핵의 빛','황금빛이 별의 중심으로 모였다가 은은하게 퍼져 나갑니다.'],en:['Stellar Heart','Golden light gathers at a stellar core and softly radiates outward.']},
        minimal:{ko:['고요의 각인','장식과 움직임을 덜어 낸 가느다란 선의 마법진입니다.'],en:['Seal of Stillness','A quiet circle of fine lines with minimal ornament and movement.']},
        classic:{ko:['달·불·별·바람','금색 팔망성과 보라색 육망성이 만나는 기존의 7초 연출입니다.'],en:['Moon · Fire · Star · Wind','The original seven-second ritual with a golden eight-pointed star and violet hexagram.']}
    };
    function normalize(language) { return ['ko','ja','en'].includes(language) ? language : 'en'; }
    function t(key, language, values = {}) {
        const entry = strings[key];
        const text = entry ? entry[normalize(language)] : key;
        return text.replace(/\{(\w+)\}/g, (_, name) => Object.hasOwn(values, name) ? String(values[name]) : '{'+name+'}');
    }
    function theme(design, language) {
        const lang = normalize(language), original = originalThemes[design.id];
        return {...design,
            name: design.names?.[lang] || original?.[lang]?.[0] || design.name,
            desc: design.descriptions?.[lang] || original?.[lang]?.[1] || design.desc};
    }
    function battery(info, language) {
        const translate = value => {
            const key = Object.keys(strings).find(key => strings[key].ja === value);
            return key ? t(key, language) : value;
        };
        return {...info, health:translate(info.health), connection:translate(info.connection), status:translate(info.status)};
    }
    function apply(document, language) {
        document.documentElement.lang = normalize(language);
        document.querySelectorAll('[data-i18n]').forEach(node => node.textContent = t(node.dataset.i18n, language));
        document.querySelectorAll('[data-i18n-label]').forEach(node => node.setAttribute('aria-label',t(node.dataset.i18nLabel,language)));
    }
    const api = {strings, normalize, t, theme, battery, apply};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    else root.MagicI18n = api;
})(typeof globalThis === 'undefined' ? this : globalThis);
