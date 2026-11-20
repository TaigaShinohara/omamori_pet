# UIカタログ：お守りペット（Omamori Pet）

- 最終更新：2026-11-17
- アート方針：`docs/ART_DIRECTION.md` の版 `1.1` に準拠

---

## パーツ一覧（役割順に並べる）

| パーツ名 | 役割 | 正本の場所（コード／ファイル） | 状態 |
| --- | --- | --- | --- |
| OmamoriPetAvatar | 4状態の感情・呼吸リズムを伝えるお守りシンボル | `mobile/.../OmamoriPetMobileCanvas.kt` / `wear/.../OmamoriPetWearCanvas.kt` | 使用中（双子正本） |
| HeroStatusCard | 今画面の主役（状態・心拍・1分深呼吸への導入） | `mobile/.../MobileScreens.kt` | 使用中 |
| BiometricSummaryPanel | Wear ホームの非インタラクティブ心拍・静止状態表示 | `wear/.../WatchScreens.kt` | 使用中 |
| AiReflectionCard | 端末内AIまたはルールによる短い寄り添いの言葉 | `mobile/.../MobileScreens.kt` | 使用中 |
| HealthConnectRecoveryCard | Health Connect 権限未許可時の復旧導線 | `mobile/.../MobileScreens.kt` | 使用中 |
| WellnessNoticeCard | 医療非該当・自己観察目的の共通注記カード | `mobile/.../MobileScreens.kt` | 使用中（統合済み） |
| SectionHeader | 一覧・設定画面の統一セクション見出し | `mobile/.../MobileScreens.kt` / `wear/.../WatchScreens.kt` (`ListSubHeader`) | 使用中（統合済み） |
| DemoModeFilterGroup | 動作確認用デモ状態の排他選択（Mobile） | `mobile/.../MobileScreens.kt` (`DemoModeSelector`) | 使用中 |
| HistoryTimelineRow | 呼吸・気づきイベントのタイムライン行（＋DemoTagBadge） | `mobile/.../MobileScreens.kt` | 使用中 |
| SettingsSwitchRow | 即時反映される ON/OFF 設定行（行全体トグル対応） | `mobile/.../MobileScreens.kt` / `wear/.../WatchScreens.kt` (`SwitchButton`) | 使用中 |
| SettingsThresholdSlider | 心拍上昇しきい値（+10〜+25 bpm）の段階調整 | `mobile/.../MobileScreens.kt` | 使用中 |
| BreathingProgressDisplay | 4-2-6 呼吸フェーズ案内と残り時間・セット進捗 | `mobile/.../MobileScreens.kt` / `wear/.../WatchScreens.kt` | 使用中 |
| OmamoriTopAndNavChrome | モバイルの TopAppBar と画面幅適応型ナビゲーション | `mobile/.../MainActivity.kt` | 使用中 |
| OnboardingWindowDots | 最大5ドットの窓進捗表示（総ページ数を数えさせない） | `mobile/.../OnboardingScreen.kt` | 使用中（新規） |
| OnboardingChoiceCard | 初回オンボーディング用の3択カード（弾み反応付き） | `mobile/.../OnboardingScreen.kt` | 使用中（新規） |
| WatchSetupGuideCard | Watch 連携希望の初心者向け 3手順案内カード | `mobile/.../MobileScreens.kt` | 使用中（新規） |

> **オンボーディング図版に関する注記**：オンボーディング内のステップ・価値・期待ページの図解構成（`StepPage` 等）は画面固有の図解イラストであり、他画面で汎用流用するパーツではないためカタログ単体登録は行わない（`OmamoriPetMobileAvatar` と既存 M3 アイコンのみ流用）。

---

## パーツ詳細

### OmamoriPetAvatar
- 役割：お守りペットの現在の状態（平静・静止上昇・呼吸中・待機）と呼吸テンポを、言葉の前に直感で伝える。
- 使う場面：Mobile 今画面・呼吸画面、Wear ホーム・呼吸画面・完了画面。
- 使わない場面：設定画面、履歴行、エラー説明カードなどの管理 UI。
- 状態：4状態（CALM / ELEVATED_AT_REST / BREATHING / RESTING）／ タップ反応（ウインク）／ 動き軽減モード（静止ポーズ）。
- 狙い：数値を読む前に「今の自分は大丈夫か／息を整えるときか」を柔らかい表情と色で一瞬で感じてもらう。
- アート方針との対応：「世界の言葉」「形の言語（ミニマル球体＋丸四角目）」「動きの性格」に準拠。
- バリエーション：サイズ（Mobile Hero `92dp` / 呼吸 `160dp`、Wear ホーム `48dp` / 呼吸 `70dp` / 完了 `54dp`）。
- 実装の場所：`mobile/.../ui/components/OmamoriPetMobileCanvas.kt`、`wear/.../ui/OmamoriPetWearCanvas.kt`
- 体験デモでは、共通のベクター素材（`shared/src/main/android-res/drawable`）から黄色・赤・紫の表情を表示する。
- 登録日と経緯：2026-11-17 既存資産の棚卸しにより登録。`:shared` は純 JVM のため双子正本として仕様・配色・目モーションを同期管理。

### HeroStatusCard
- 役割：Mobile「いま」画面の唯一の主役として、現在状態・心拍・呼吸開始 CTA を 1 箇所にまとめる。
- 使う場面：`HomeMobileScreen` の最上位（Expanded 画面では左ペイン）。
- 使わない場面：履歴画面・設定画面など他画面への使い回し。
- 状態：4状態ごとのコンテナ色遷移（おだやか・ひと息タイムは `primaryContainer`、深呼吸中は `secondaryContainer`、待機中は `surfaceContainerHigh`）、Watch 未同期・同期済み・デモ表示。
- 狙い：開いて 3 秒で今の状態と「1分間の深呼吸ガイド」への導線が迷わず目に入るようにする。
- アート方針との対応：ElevatedCard を主役 1 枚に限定し、状態ごとに穏やかな色遷移を適用。主ボタン（`Button`）＋副導線（`TextButton`）の強弱を徹底。
- バリエーション：CTA は `primary`。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt` (`HeroStatusCard`)
- 登録日と経緯：2026-11-17 棚卸し・3問テストにより「動作状態ピル枠の廃止」「副ボタンの TextButton 化」を実施して登録。

### BiometricSummaryPanel
- 役割：Wear OS ホーム画面で現在の心拍・動作判定・平常時目安を誤タップなしで読ませる。
- 使う場面：`HomeWatchScreen` の `TransformingLazyColumn` 内。
- 使わない場面：タップ操作が必要な設定やボタン。
- 状態：平常・静止上昇（`primary`）／未計測（`-- bpm`）。
- 狙い：誤って押して呼吸画面に飛ばさず、実測根拠だけを静かに伝える。
- アート方針との対応：Wear M3 `SurfaceTransformation` + `transformedHeight` 準拠、`Icons.Rounded.Favorite` と `tnum` 統一。
- バリエーション：なし（1パターン固定）。
- 実装の場所：`wear/.../ui/WatchScreens.kt` (`HomeWatchScreen` 内 `biometric_panel`)
- 登録日と経緯：2026-11-17 既存の手書き背景から Wear M3 `SurfaceTransformation` ベースへ統一して登録。

### AiReflectionCard
- 役割：実測値に基づく穏やかな 1 文の寄り添い言葉を届ける。
- 使う場面：`HomeMobileScreen` の Hero 下（Expanded 画面では右ペイン）。
- 使わない場面：Wear 画面・呼吸中画面（呼吸中の認知負荷を避ける）。
- 状態：通常表示／更新中（`CircularProgressIndicator`＋透過切替）。
- 狙い：数値の解釈に迷うユーザーに寄り添う言葉だけを届け、Hero より主張させない。
- アート方針との対応：シャドウなし Filled `Card`（`surfaceContainerLow`）＋ `animateContentSize`。更新はヘッダー右端の `IconButton` のみに簡素化し、端末内AI準備は設定タブへ移設。
- バリエーション：なし。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt` (`AiReflectionCard`)
- 登録日と経緯：2026-11-17 棚卸し・情報削減監査によりフッター行（テキストボタン・出典常時注記・準備導線）を削減・移設して登録。

### HealthConnectRecoveryCard
- 役割：Health Connect 権限が未許可・要対応のときだけ、静かな復旧導線を出す。
- 使う場面：`HomeMobileScreen`（未許可時のみ条件表示）。
- 使わない場面：連携済み・非対応端末時（設定タブ側へ任せる）。
- 状態：未許可（表示）／許可済み（非表示）。
- 狙い：必要なときだけ迷わず許可へ進めるようにし、日常の画面を塞がない。
- アート方針との対応：`OutlinedCard` + `FilledTonalButton`（主役ボタンと競合させない）。
- バリエーション：なし。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt` (`HealthConnectRecoveryCard`)
- 登録日と経緯：2026-11-17 棚卸しにより登録。

### WellnessNoticeCard
- 役割：医療機器ではないことを落ち着いたトーンで共通表示する。
- 使う場面：`SettingsMobileScreen` 最下部、`PrivacyRationaleActivity` 最下部。
- 使わない場面：ホーム画面・呼吸画面（日常の静けさを邪魔しない）。
- 状態：静的表示のみ。
- 狙い：安心感と法的な明確さを同じ見た目で統一して伝える。
- アート方針との対応：補助注記向けの `OutlinedCard`（`shapes.medium` + `onSurfaceVariant`）。
- バリエーション：外部からの `Modifier`（余白・幅）のみ許可。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt` (`internal fun WellnessNoticeCard`)
- 登録日と経緯：2026-11-17 `SettingsMobileScreen` と `PrivacyRationaleActivity` に重複していた `OutlinedCard` を統合。

### SectionHeader
- 役割：リストや設定内のグループ境界とセクション名を統一リズムで示す。
- 使う場面：`HistoryMobileScreen`（今日／それ以前）、`SettingsMobileScreen`（通知／連携／デモ）。Wear では `ListSubHeader` を正本とする。
- 使わない場面：カード内部の小見出し。
- 状態：静的表示（`semantics { heading() }` 付き）。
- 狙い：一覧のスキャン性を高め、画面間で同じ視覚リズムを保つ。
- アート方針との対応：`titleSmall` + `primary`、統一パディング（16dp 横、上 16dp、下 4dp）。
- バリエーション：なし。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt` (`internal fun SectionHeader`)
- 登録日と経緯：2026-11-17 `DemoModeSelector` 内の独自見出しと履歴セクション見出しを統合して登録。

### DemoModeFilterGroup
- 役割：検証・デモ用の4状態（実測／おだやか／高め(静止)／移動後）を切り替える。
- 使う場面：`SettingsMobileScreen` のデモセクション。
- 使わない場面：ホーム画面（誤ってデモに切替えないように隔離）。
- 状態：選択中（チェックアイコン＋塗り）／非選択。
- 狙い：狭い画面・文字拡大・日英切替でも文字が切れず安全に切り替えられる。
- アート方針との対応：`FlowRow` + `FilterChip` による非色伝達（チェックアイコン併用）。
- バリエーション：なし。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt` (`DemoModeSelector`)
- 登録日と経緯：2026-11-17 `SingleChoiceSegmentedButtonRow` の文字切れ懸念解消のため変更・登録。

### HistoryTimelineRow
- 役割：深呼吸セッションおよび心拍上昇／スヌーズ記録を 1 行で表示する。
- 使う場面：`HistoryMobileScreen` の `LazyColumn` 内。
- 使わない場面：他画面。
- 状態：通常／デモ記録（`DemoTagBadge` 付き）／アニメーション挿入・削除。
- 狙い：カードで囲みすぎず、アイコンの種類（`Spa` / `FavoriteBorder` / `NotificationsPaused`）と時系列で振り返りやすくする。
- アート方針との対応：M3 `ListItem` + `HorizontalDivider`（末尾を除く）、デモタグは控えめな `labelSmall` バッジ。
- バリエーション：Breath / Event（Snoozed vs Elevated）。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt` (`HistoryTimelineRow`, `DemoTagBadge`)
- 登録日と経緯：2026-11-17 タイムライン描画の重複整理として登録。

### SettingsSwitchRow
- 役割：即時反映される ON/OFF 設定を提供する。
- 使う場面：`SettingsMobileScreen`（通知・おやすみ時間）、`DemoSettingsWatchScreen`（`SwitchButton`）。
- 使わない場面：複数値の選択。
- 状態：ON / OFF（行全体 `Modifier.toggleable` または Wear `SwitchButton`）。
- 狙い：小さなトグルを狙わせず、どの領域をタップ・音声操作しても確実に切り替えられる。
- アート方針との対応：標準 M3 `ListItem` + `Switch` / Wear `SwitchButton`。
- バリエーション：補足説明（`supportingContent` / `secondaryLabel`）の有無。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt`、`wear/.../ui/WatchScreens.kt`
- 登録日と経緯：2026-11-17 棚卸しにより登録。

### SettingsThresholdSlider
- 役割：通知感度（+10 〜 +25 bpm、4段階）を調整する。
- 使う場面：`SettingsMobileScreen` の通知セクション。
- 使わない場面：Wear 画面（手首の狭い操作面には不向きなため Mobile 側へ集約）。
- 状態：通常／ドラッグ中（ローカル値＋ステップ触覚）／確定時保存・同期。
- 狙い：ドラッグ中のガタつきや過剰通信を防ぎ、両端の意味（敏感／控えめ）を添えて迷わせない。
- アート方針との対応：M3 `Slider` + `tnum` 数値＋`stateDescription`。
- バリエーション：なし。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt`
- 登録日と経緯：2026-11-17 棚卸しにより登録。

### BreathingProgressDisplay
- 役割：4-2-6 呼吸の今どのフェーズか、あと何秒か、何セット目かを静かに伝える。
- 使う場面：`BreathingMobileScreen`、`BreathingWatchScreen`。
- 使わない場面：他画面。
- 状態：吸う（4s）／止める（2s）／吐く（6s）／完了。
- 狙い：Mobile では「残り秒数＋横バー＋5ドット」に情報を絞り、数字の重複ノイズをなくして呼吸に集中させる。
- アート方針との対応：連続補間（Mobile `Animatable` / Wear `animateFloatAsState`）＋穏やかな `AnimatedContent`＋控えめな中断ボタン（Mobile `TextButton` / Wear 低強調 `OutlinedButton`）。
- バリエーション：Mobile（ドットインジケータ付き）と Wear（省スペースな `N/5` 表記）。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt`、`wear/.../ui/WatchScreens.kt`
- 登録日と経緯：2026-11-17 3問テストにより Mobile 側の `cycle / total` 文字重複を削減して登録。

### OmamoriTopAndNavChrome
- 役割：モバイルの現在地表示と 3 つのトップレベル画面（いま・ふりかえり・設定）間の移動を担う。
- 使う場面：`MainActivity` のトップレベルルート（呼吸画面では非表示）。
- 使わない場面：`BreathingMobileScreen`（集中タスクのため隠す）、`PrivacyRationaleActivity`（標準戻る `TopAppBar`）。
- 状態：Compact（`NavigationBar`）／Medium・Expanded（`NavigationRail`）、選択中（Filled）／非選択（Outlined）。
- 狙い：画面幅が変わってもナビゲーション順と選択状態を維持し、タブ名・タイトルを明確にする。
- アート方針との対応：スクロール連動 `CenterAlignedTopAppBar`＋兄弟間クロスフェード遷移。
- バリエーション：ウィンドウ幅 `< 600dp` vs `>= 600dp`。
- 実装の場所：`mobile/.../MainActivity.kt`
- 登録日と経緯：2026-11-17 棚卸しにより登録。

### OnboardingWindowDots
- 役割：総ページ数を意識させず、現在地と前後ページの存在だけを静かに示す。
- 使う場面：`OnboardingScreen` 上部ヘッダー中央のみ。
- 使わない場面：本体の呼吸画面・設定・履歴画面。
- 状態：先頭（右端縮小）／中間（両端縮小）／末尾（完了点灯）。タップジャンプ不可、TalkBack 装飾除外。
- 狙い：ページ数を数えて離脱する認知負荷を防ぎ、「1歩ずつ進んでいる」テンポ感を保つ。
- アート方針との対応：主色 `primary`（現在・完了）＋ `outlineVariant`（未到達）、200ms の穏やかな幅・色遷移（Reduced Motion 時は即時）。
- バリエーション：なし。
- 実装の場所：`mobile/.../ui/screens/OnboardingScreen.kt` (`OnboardingWindowDots`)
- 登録日と経緯：2026-11-17 初心者向けオンボーディング新設に伴い新規ゲート（狙い・流用検討・統一感確認）を通過して登録。

### OnboardingChoiceCard
- 役割：悩み・環境・振動の好みを「次へボタンなし」で直感的に選ばせる 3 択専用カード。
- 使う場面：`OnboardingScreen` の質問ページ（#2, #3, #7）。
- 使わない場面：設定画面の ON/OFF 項目（`SettingsSwitchRow` を使用）。
- 状態：通常（`surfaceContainerLow`）／選択・押下（`primaryContainer` + 軽い弾み + チェックアイコン）／遷移中ロック。
- 狙い：タップした瞬間の気持ちいい手応えで「自分のために整えられている」実感を作る。
- アート方針との対応：`shapes.large` と `primaryContainer` / `surfaceContainerLow` の低刺激ロールを継承。
- バリエーション：なし。
- 実装の場所：`mobile/.../ui/screens/OnboardingScreen.kt` (`OnboardingChoiceCard`)
- 登録日と経緯：2026-11-17 初心者向けオンボーディング新設に伴い新規ゲートを通過して登録。

### WatchSetupGuideCard
- 役割：Pixel Watch 連携を希望した初心者が未接続時に迷わないよう、3 手順を折りたたみで控えめに示す。
- 使う場面：`HomeMobileScreen`（`HAS_WATCH` 選択かつ未同期・非デモ・未非表示時のみ）。
- 使わない場面：スマホ単体モード選択時、Watch 連携済み時、他タブ。
- 状態：初期折りたたみ（1行見出し）／タップで3手順展開／「非表示」操作で折りたたみ保存。
- 狙い：Watch 未接続時の初回ホームを長文で圧迫せず、必要な人だけ 1 タップで確認できるようにする。
- アート方針との対応：`HealthConnectRecoveryCard` と同じ `OutlinedCard`（`shapes.large` + `TextButton`）を流用し、Hero と競合させない。
- バリエーション：なし。
- 実装の場所：`mobile/.../ui/screens/MobileScreens.kt` (`WatchSetupGuideCard`)
- 登録日と経緯：2026-11-17 情報削減監査により初期折りたたみ式へ改善して登録。

---

## 統合候補・重複の記録

| 似ているパーツ | 正本に選ぶもの | 理由 | 統合の状態 |
| --- | --- | --- | --- |
| Settings 内の注意カード と PrivacyRationale 内の注意カード | `WellnessNoticeCard`（`MobileScreens.kt`） | 同一メッセージ・同一構造の `OutlinedCard` の重複をなくしデザインを一貫させるため | 完了（2026-11-17） |
| Settings 各セクション見出し と DemoModeSelector 見出し と 履歴セクション見出し | `SectionHeader`（`MobileScreens.kt`） | パディングとタイポグラフィの揺れをなくし画面間のリズムを揃えるため | 完了（2026-11-17） |
| `OmamoriPetMobileAvatar` と `OmamoriPetWearAvatar` | 双子正本（`:mobile` と `:wear` で仕様同期） | `:shared` が純 JVM（Compose 非依存）のため、モジュール構成を変えずに仕様と色ロールを同期管理する | 運用中（同期確認済み） |

---

## 廃止・削除したパーツ／要素（3問テストによる整理）

| パーツ／要素名 | 廃止日 | 理由 | 代替・残した手段 |
| --- | --- | --- | --- |
| `HeroStatusCard` 内の動作状態ピル背景枠 (`Surface`) | 2026-11-17 | ボタンや操作タグに誤認されやすく、主役（ペット＋状態＋心拍＋呼吸CTA）から視線を奪っていたため | 枠なしの静かなアイコン＋テキスト行へ簡素化 |
| `HeroStatusCard` 副ボタンの `OutlinedButton` 枠強調 | 2026-11-17 | 主ボタンの直下に枠線ボタンが同幅で並ぶと「2つの主行動」に見えて3秒テストで迷いを生むため | 控えめな `TextButton` に強弱を戻した |
| `AiReflectionCard` ヘッダー右上のシステム出典ラベル | 2026-11-17 | タイトルと同じ高さで競合し、一番見せたい「寄り添いの本文」よりシステム都合が目立っていたため | フッター行の控えめな位置へ移動 |
| `BreathingMobileScreen` のテキスト `· $cycle / $totalCycles` | 2026-11-17 | 下の5ドットインジケータと意味が重複しており、呼吸中の数字ノイズになっていたため | ドットインジケータ（＋TalkBack説明）へ一本化 |
| Wear 設定画面のテキスト流用ボタン（`"通知: オン"`, `"✓ $label"`） | 2026-11-17 | パーツの役割がテキストに依存しており、Wear M3 標準コントロールとの統一感を欠いていたため | Wear M3 `SwitchButton` / `RadioButton` へ置換 |
