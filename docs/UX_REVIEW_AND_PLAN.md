# UXレビュー・初心者対応および改善計画（お守りペット）

- 作成日：2026-11-17（改訂 2.0：独立審査 `[F-01]`〜`[F-05]` 解消反映版）
- 対象版：`5742d5d` + 現在の作業ツリー（`docs/ART_DIRECTION.md` v1.1、`docs/UI_CATALOG.md`、`:mobile` / `:wear` / `:shared`）
- 適用スキル：`concept-driven-ux` / `meaningful_app_design` / `mobile_onboarding`
- 目的：初心者でも迷わず理解し、不安にならず最初の体験（1分間の深呼吸・静かな見守り）に到達できるよう、体験と文言・導線を整備する。

---

## 1. コンセプトと達成条件（L1 / L2）

- **コンセプト**：不安や緊張、あるいは静止中の心拍上昇を感じた人（Pixel Watch を持つ人も、まずスマホだけで試したい人も）が、日常や夜間にアプリを開き、**専門知識なしで自分の状態を穏やかに受け止め、1分間の深呼吸ガイドで「今はこれだけで大丈夫」と落ち着きを取り戻せる体験**。
- **4つの達成条件（オンボーディング）**：
  1. **何のためのアプリか**：オンボーディングを見た人が「（運動していないのに）心拍が早いときや落ち着きたいときに、1分間の深呼吸で息を整えるお守りアプリ」と自分の言葉で言える。
  2. **基本的な使い方（ちょうど3ステップ）**：1. みる → 2. すう・はく → 3. ふりかえる の3手順だけで使い始められる。
  3. **ワクワクする瞬間**：選択に反応して自分のために整えられる実感（マッチ宣言）と、1タップで試し呼吸ができる瞬間、さらに継続で自分のペースが見える期待ページで「やってみたい」と感じられる。
  4. **早く使いたい**：最後の接続ページの主ボタンを押すと、説明の続きではなく本体の「1分間の深呼吸ガイド（最初の小さな成功）」へ直結する。
- **守るべき制約・契約**：
  - 生体データの外部送信を行わない（すべて端末内で処理）。
  - 実測値がない状態で心拍減少や数値を捏造しない（憶測なし原則）。
  - 既存データの互換性（`AlertPreferences` / `BreathingSessionRecord` / `CalmEventRecord`）と Reduced Motion / TalkBack 対応を保つ。
  - オンボーディング選択内容は機微情報になり得るため端末内のみに保存し、非 PII 計測の識別子と結び付けない。設定から消去・再表示できる。

---

## 2. 厳格チェック：現状の初心者障壁（事実と仮説）

| 分類 | 対象箇所 | 現状の仕様・表示（事実） | 初心者にとっての障壁（根拠と課題） | 重要度 |
| --- | --- | --- | --- | --- |
| 初回導入 | Mobile 起動直後 (`MainActivity`) | オンボーディングがなく、いきなり `HomeMobileScreen` が開く。 | 何のアプリか、Watch が必須なのか、スマホだけで使えるのかが初回に伝わらない。 | **致命的** |
| Watch 未接続時の表示 | `HomeMobileScreen` (`HeroStatusCard`, `CalmAdvisorEngine`) | Watch 未接続時に「ウォッチの計測待ち」「-- bpm」「未装着（`OFF_BODY`）」が表示され、AIカードも「ウォッチを装着すると〜」と二重催促する。 | スマホ単体初心者に「Watchがないから使えない・壊れている」と誤認される。 | **重大** |
| 専門・開発者用語 | `mobile/strings.xml`, `wear/strings.xml` | `4-2-6`、`誤検知ガード中`、`Health Connect 基準連携`、`Gemini Nano を準備`、`端末内ルール助言`、`動作確認・デモ表示`。 | 医療・運動・Android/AI の知識がないと意味が直感できない。 | **重大** |
| 未接続時の失敗導線 | `HeroStatusCard` (`cta_breathe_watch`), `MainActivity` | Watch 未接続でも「ウォッチで呼吸を開く」が並び、押すと Snackbar を出しつつ強制的に `breathing` 画面へ遷移し Snackbar が見落とされる。 | 失敗するボタンが並んでおり、Watch とのつなぎ方も分からない。 | **重大** |
| デモ復帰・履歴矛盾 | `MobileRepository`, `HistoryMobileScreen` | `setDemoMode(LIVE_WATCH)` で `lastWatchSyncMs` が残り表示矛盾が起きる。またデモ深呼吸後に履歴へ「きょう：0 回」の直下にデモ記録が並ぶ。 | デモと実測の境界が曖昧で混乱する。ホームからデモを1タップで戻せない。 | **中** |
| 権限・準備の状態漏れ | `MobileScreens.kt`, `HealthConnectManager`, `GeminiNanoAdvisorEngine` | `UPDATE_REQUIRED` でも設定側が「未導入」扱い、拒否後の復旧導線なし、Gemini Nano 準備失敗が無言、通知 OFF でもスライダー・おやすみスイッチが有効。 | なぜ動かないのか・どう直せばいいかが分からない。 | **中** |
| Wear 側の初心者配慮 | `wear/MainActivity`, `WatchScreens.kt` | 1) `ON_RESUME` 権限再判定なし 2) `permission_denied_recovery` 未使用＋設定ボタン常時並置 3) 1分基準計測の進捗・完了表示なし 4) `4-2-6` 残存・`1/5` 表記曖昧・デモ解除や休止確認がホームで完結しない。 | 設定で許可して戻っても消えず、基準計測を押しても何も起きないように見える。 | **重大** |

---

## 3. 維持・変更・統合・削減の比較と改善対象の選定

| 対象 | 採る方針 | 変更内容の要点 | 選定理由 |
| --- | --- | --- | --- |
| 初回オンボーディング（Mobile） | **追加（新設）** | `mobile_onboarding` 準拠の **15ページ構成**（1ページ1主張、3択×2＋マッチ、アイデンティティ、価値1、振動3択＋結果、3ステップ、1動作試し呼吸＋「あとで試す」、試し結果、期待、接続）。版番号 `1` で初回のみ表示（設定から再閲覧・回答消去可）。 | 初心者が3秒で目的と使い方を理解し、自分事化して最初の体験（1分深呼吸）へ進めるようにするため。 |
| `HeroStatusCard` と AI カード | **変更** | Watch 未接続時は「未装着」「-- bpm」を出さず「スマホでいつでも深呼吸できます」の穏やかな案内へ。Watch 接続確認済み時のみ「ウォッチで開く」を表示（送信失敗時は遷移せず「スマホで開く」アクション付き案内）。デモ中はホーム上で「実測にもどす」1タップ導線を提供。 | 失敗する操作と「使えない感」をなくし、スマホ単体でも迷わず始められるようにするため。 |
| 専門・技術用語（日英 `strings.xml`） | **変更** | `4-2-6`／`誤検知ガード`／`端末内ルール助言`／`Gemini Nano`（技術語前面出し）を平易な日英表現へ刷新。Gemini Nano 準備時は進捗表示と失敗時の穏やかなフォールバック通知を追加。 | 専門知識がない初心者でも一読で意味と安心感を得られるようにするため。 |
| Health Connect・通知設定・履歴 | **変更** | `UPDATE_REQUIRED` の正しい表示・拒否後の OS 設定復旧・スマホ単体時の控えめな扱いを整備。通知 OFF 時は感度スライダーとおやすみ時間スイッチを無効化。履歴サマリーはデモ記録のみの場合に「体験デモ記録」として矛盾なく集計。 | 設定状態・履歴・権限の嘘や操作矛盾をなくすため。 |
| `OmamoriTileService`（Wear Tile） | **変更** | `BREATHING` 状態を `ART_DIRECTION.md` v1.1 の青磁色（`#93C7B5` / `#0B251D`）へ統一。 | 実コードの配色ズレを解消するため。 |
| Wear ホーム・設定・呼吸（`:wear`） | **変更** | `ON_RESUME` 権限再チェック、初回1ボタン→拒否後 `permission_denied_recovery`＋設定誘導、ホームでの1分基準計測カウント表示・完了確認、1時間休止フィードバック、ホーム上のデモ終了ボタン、`1/5回目` 表記、平易な文言を実装。 | Watch 単独で始めた初心者も許可・計測・体験を迷わず完結できるようにするため。 |
| 既存アート方針・4状態モデル・呼吸即開始 | **維持** | `docs/ART_DIRECTION.md` v1.1 の配色・Elevated 1枚制限・Reduced Motion・呼吸即開始を維持し、新規部品も同トークンに合わせる。 | 既に成立している静穏で低刺激な体験を壊さないため。 |

---

## 4. 新設：初心者オンボーディングのページ地図と詳細仕様（`mobile_onboarding` 準拠）

- **流派**：体験直結型（全 15 ページ、想定完了時間 約 85〜105 秒）。
- **絶対ルールと設計ガード**：
  1. **1ページ1主張**：見出しは 1 行（日本語 20 字前後・英語短文）、補足は最大 2 行（試し操作ページ #12 は補足なし・見出し 1 行のみ）。
  2. **窓ドット進捗（`OnboardingWindowDots`）**：
     - 表示数 `windowSize = min(5, totalPages)`（5 つ固定表示）。
     - 開始位置 `windowStart = (currentPage - 2).coerceIn(0, totalPages - windowSize)`。
     - 1 ページ進むごとにアクティブドットが必ず 1 つ移動（戻る時は逆方向へスライド）。数字表記（`3/15` 等）は一切出さず、タップジャンプも無効。最終ページ（#15 接続）では完了形（すべて同じ色で点灯）にする。
     - TalkBack ではドット自体は装飾として読み上げ対象から外し、ページごとの章名・ステップ名を見出し側または専用ラベルで伝える。
  3. **固定ナビゲーション配置**：
     - 上部ヘッダー左右の絶対位置を全ページで固定：左に「もどる」（#1 では `alpha=0f`・操作無効で場所のみ確保）、中央に窓ドット、右に「スキップ」（#15 接続ページのみ非表示で場所確保）。
     - もどる・スキップは中立の画面遷移のみ（称賛文・成功触覚なし）。
  4. **選択・試し操作ページ（#2, #3, #7, #12）の自動遷移シーケンス**：
     - 選択ページ（#2, #3, #7）には「次へ」ボタンを置かない。
     - 選択タップ時および #12 試し操作完了時の厳密シーケンス：
       1. `0ms`: 選択カードが軽く弾む（`250〜350ms`、#12 はミニ呼吸 `4,000ms` 完了または実行中の再タップ割り込み即完了時）＋ OS 標準触覚（`View.performHapticFeedback(CONFIRM)` / 低APIは `KEYBOARD_TAP`、#7 で振動オフ選択時のみ #12 の呼吸触覚は省略可）＋ 現在のページ内に具体的な称賛 1 文を表示（次ページ内容はまだ出さない）。
       2. `~1,000ms`（余韻）：称賛が読める間待機。ただし余韻中にユーザーがページをタップ／スワイプした場合は即座に待ちを打ち切って次ページへ遷移（連打による二重遷移はロックで防止）。
       3. `遷移（300ms）` → 次ページ（#3, #4, #8, または #13）へ切替。
  5. **結果ページ（#4 マッチ, #8 振動設定結果）の順次入場（各 120〜180ms ずらし）**：
     - 質問・選択肢は一切再掲しない。
     - `0ms`：選んだ言葉の映し返しテキスト → `150ms`：主役図形（#4 は `OmamoriPetMobileAvatar(CALM)`＋寄り添うお守り輪 ※ハート不使用、#8 はお守りペット＋振動/静穏アイコン）→ `300ms`：宣言・設定反映見出し＋根拠補足 1 文。（Reduced Motion 有効時は位置移動なし・短いフェード、順次タイミング順は保持）。
  6. **「押したら褒める」3段階 × 各3通り以上（直前重複回避）**：
     - `PraiseSelector` により段階別に直前インデックスを保持し、同じ文の連続を防止する。
     - **小**（通常送り）：ボタン軽弾み（`200ms`）＋ `CLOCK_TICK` 触覚 ＋ アイコン/短い受け取り印（「つぎへ」等）。
     - **中**（Q1/Q2/Q3 選択・試し操作成功）：
       - 選択時（Ja）：①「教えてくれてありがとう。それに合わせて整えます。」②「受け取りました。あなたのペースに合わせます。」③「わかりました。ぴったりの使い方をご案内します。」
       - 選択時（En）：①「Thanks for sharing—tailoring your guide.」②「Got it. Matching this to your pace.」③「Understood. Setting up what fits you best.」
       - 試し成功時（Ja）：①「とても穏やかにできています。」②「いいリズムです。この感覚を覚えておけます。」③「落ち着いて息が吐けました。」（※#13 見出し「ひと息つけました。」と重複しない）
       - 試し成功時（En）：①「Gentle and steady—that felt calm.」②「Nice rhythm. You have got the feel of it.」③「Calm and unhurried—nicely done.」
     - **大**（オンボーディング完了接続・本体初の1分深呼吸達成）：
       - Ja：①「準備が整いました。あとはあなたのペースで大丈夫です。」②「おつかれさま。いつでもこのひと息に戻れます。」③「ここまでで準備は完了です。今日の落ち着きを始めましょう。」
       - En：①「All set. Take everything at your own pace.」②「Well done. You can return to this breath anytime.」③「You are ready. Let's begin today's calm minute.」
  7. **版管理・非 PII 計測・保存と削除**：
     - `ONBOARDING_VERSION = 1`。完了・スキップ・「あとで試す」時はすべて `completedOnboardingVersion = 1` を DataStore に保存し、次回起動では非表示。
     - Q3 の回答（`breathingHapticsEnabled`）は `AlertPreferences` / DataStore に反映され、本体の `BreathingMobileScreen` と `SettingsMobileScreen` で切り替え可能にする。
     - `OnboardingAnalyticsTracker`：ページ表示・離脱・スキップ・回答アクション種別（ページ番号と役割のみ。選択内容の文字列や個人識別子は紐付けない）を非 PII イベントとして記録。
     - 設定画面の「データとプライバシー」欄に「案内設定と回答を消去」および「はじめての使い方を見る」を配置。

### ページ地図（15ページ）

| # | 役割 | 見出し（1行） | 補足（最大2行） | 視覚の主役 | 主役アニメ（意味） | その他アニメ | 操作と遷移先 | 反応 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | フック | なんでもないのに、落ち着かない日へ。 | 動いていないのにドキドキするとき、そっと気づいて息を整えます。 | `OmamoriPetMobileAvatar(ELEVATED_AT_REST)` | 入場時に一度だけ穏やかに丸く脈打ち見守る目になる（気づきと寄り添いを伝える） | なし | タップ／つぎへ → 2 | 小 |
| 2 | 選択（悩み） | いま、いちばん気になるのは？ | 選んだ内容に合わせて案内を整えます。 | 3択カード（①「動いていないのに、心拍が早くて落ち着かない」②「自分のいつもの落ち着きを知っておきたい」③「まだわからない（まずは見てみたい）」） | 選んだカードが軽く弾む（決定の手応えを伝える） | なし | 「次へ」なし。選択→反応・余韻→自動遷移で 3 | 中 |
| 3 | 選択（環境） | Pixel Watch はお持ちですか？ | なくてもスマホだけで今日から使えます。 | 3択カード（①「持っていて、つなげて使いたい」②「今はスマホだけで使ってみたい」③「あとで決める（まだわからない）」） | 選んだカードが軽く弾む（決定の手応えを伝える） | なし | 「次へ」なし。選択→反応・余韻→自動遷移で 4 | 中 |
| 4 | マッチ（結果） | （選択組み合わせ9通りの宣言・下記表） | （選んだ環境に効く強み1つ・保証語なし） | `OmamoriPetMobileAvatar(CALM)` ＋ 寄り添うお守り輪（※ハート不使用） | 輪がペット側へゆっくり寄って重なる（合っていることを伝える） | なし | 順次入場。つぎへ／スワイプ → 5 | 小 |
| 5 | アイデンティティ | 診断ではなく、ひと息の相棒。 | 怖い警告や点数は出しません。そっと寄り添うだけのお守りです。 | `OmamoriPetMobileAvatar(CALM)` ＋ 柔らかなお守り背景円 | 静かにフェード入場（非威圧の安心感を伝える） | なし | つぎへ／スワイプ → 6 | 小 |
| 6 | 価値 | （選択優先の価値・下記参照） | （対応する1〜2行の場面説明） | 状態モック図（`CalmRuleMock`：歩行除外 ↔ スマホ1分呼吸） | 選択対象側の状態が一度だけ滑らかに切り替わる（違いを伝える） | なし | つぎへ／スワイプ → 7 | 小 |
| 7 | インタラクション（振動好み） | 深呼吸のリズムを振動でも伝えますか？ | 画面を見つめなくても、手元の震えで息を合わせられます。 | 3択カード（①「やさしい振動で教えてほしい」②「画面の動きだけで十分」③「あとで決める（標準: 振動あり）」） | 選んだカードが軽く弾む（選択を伝える） | なし | 「次へ」なし。選択→反応・余韻→自動遷移で 8 | 中 |
| 8 | マッチ（振動結果） | （振動あり／画面のみ／標準で始める、のいずれか1行） | 設定タブからいつでも切り替えられます。 | `OmamoriPetMobileAvatar(BREATHING)` ＋ 状態アイコン | 順次入場で穏やかに整う（設定反映を伝える） | なし | つぎへ／スワイプ → 9 | 小 |
| 9 | ステップ 1 | 1. みる | 今の気分や落ち着きを、ペットの色と表情で確かめます。 | ステップ番号「1」＋ 簡略ホーム図形（同一構図） | 番号とペットが順に表示される（最初の手順を伝える） | なし | つぎへ／スワイプ → 10 | 小 |
| 10 | ステップ 2 | 2. すう・はく | 4秒吸って、2秒止めて、6秒吐く。リズムに合わせるだけです。 | ステップ番号「2」＋ 呼吸リング図形（同一構図） | リングがゆっくり1回だけ膨らんで縮む（呼吸のテンポを伝える） | なし | つぎへ／スワイプ → 11 | 小 |
| 11 | ステップ 3 | 3. ふりかえる | 深呼吸できた回数や気づきが、あとから静かに見返せます。 | ステップ番号「3」＋ きろく行図形（同一構図） | 記録行が1行ずつ静かに現れる（積み重なりを伝える） | なし | つぎへ／スワイプ → 12 | 小 |
| 12 | インタラクション（試し操作） | 下の丸を1回押して、ひと息ためす | **（なし：見出し1行のみ）** | 試し操作サークル（`OmamoriPetMobileAvatar` 内包）＋ 下部に中立ボタン「あとで試す」 | 押すと「ゆっくり吸う(1.5s)→長く吐く(2.5s)」で膨張収縮＋OS標準触覚（振動あり選択時）。4秒中の再タップで即完了へ割り込み可能 | なし | 1タップでミニ呼吸→完了時に#12上で称賛・触覚→余韻(~900ms, タップ割り込み可)→13へ自動遷移／「あとで試す」で即完了し本体ホームへ | 中 |
| 13 | マッチ（試し結果） | ひと息つけました。 | 1分間の本番でも、同じようにゆったり整えられます。 | `OmamoriPetMobileAvatar(CALM)` ＋ チェック印 | 一度だけ優しく広がって収まる（達成を伝える、450ms） | なし | つぎへ／スワイプ → 14 | 小 |
| 14 | 期待 | 続けるほど、自分のリズムが見えてきます。 | 1日1分のひと息から、落ち着いた時間が少しずつ積み重なります。 | 3つの穏やかな記録印が並ぶ図形 | 3つの印が順に浮かび上がる（これからの変化を伝える） | なし | つぎへ／スワイプ → 15 | 小 |
| 15 | 接続 | 1分間の深呼吸からはじめましょう | 約1分で終わります。いつでも途中でやめられます。 | `OmamoriPetMobileAvatar(CALM)` ＋ 主ボタン「1分間の深呼吸をはじめる」 | 主ボタンが一度だけ静かに脈打つ（最初の行動へ誘導） | なし | 主ボタン→完了保存し本体の `breathing` 画面へ直結／副ボタン「まずはホームを見る」→ホームへ | 大 |

（※ステップ #9〜#11 は `page-patterns.md` L48-52 の「ちょうど3ページで1セット・同一構図」に準拠し、受動の連続は最大2ページ（#5〜#6、およびステップセット自体）に抑制した。なお「あとで試す」または「スキップ」で抜けた場合は `hasCompletedFirstBreathing = false` を保存し、本体のホーム画面に「はじめての1分深呼吸をためしてみましょう」の1行案内を残す。）

### 選択9通りのマッチ宣言（Page #4）と価値（Page #6）の対応表

| Q1（気になること） | Q2（Watch有無） | Page #4 映し返し（Ja / En） | Page #4 宣言見出し（Ja / En） | Page #4 根拠補足1つ（Ja / En） | Page #6 優先表示される価値 |
| --- | --- | --- | --- | --- | --- |
| `CALM_DOWN` | `HAS_WATCH` | 静止中のドキドキを落ち着けたいあなたに。 / For calming racing moments at rest. | お守りペットは、あなたにぴったりです。 / Omamori Pet fits you naturally. | 歩いた直後の高い心拍は除外し、落ち着きたいときだけそっと知らせます。 / It ignores post-walk spikes and only nudges when you're truly still. | Watch 見守り価値（運動除外） |
| `CALM_DOWN` | `PHONE_ONLY` | ふとしたドキドキを落ち着けたいあなたに。 / For settling sudden tension anytime. | お守りペットは、あなたにぴったりです。 / Omamori Pet fits you naturally. | 時計がなくても、開いてすぐ1分間の深呼吸で息を整えられます。 / Even without a watch, a 1-minute guided breath is one tap away. | スマホ単体呼吸価値 |
| `CALM_DOWN` | `UNDECIDED_DEVICE` | ふとしたドキドキを落ち着けたいあなたに。 / For calming moments when your heart feels quick. | お守りペットは、あなたにぴったりです。 / Omamori Pet fits you naturally. | まずはスマホの1分深呼吸から始め、あとから Watch もつなげられます。 / Start with 1-minute phone breathing and add a watch anytime later. | スマホ単体呼吸価値 |
| `LEARN_BASELINE` | `HAS_WATCH` | いつもの落ち着きを知りたいあなたに。 / For learning your everyday calm rhythm. | お守りペットは、あなたにぴったりです。 / Omamori Pet fits you naturally. | Watch とスマホであなたの平常時目安を少しずつ学び、静かに見守ります。 / It gently learns your resting baseline between your watch and phone. | Watch 見守り価値（運動除外） |
| `LEARN_BASELINE` | `PHONE_ONLY` | 自分の落ち着きを確かめたいあなたに。 / For checking in with your calm rhythm. | お守りペットは、あなたにぴったりです。 / Omamori Pet fits you naturally. | 1分間の深呼吸とその日のきろくから、落ち着きの流れを振り返れます。 / Track your 1-minute breathing sessions and reflect on calm moments. | スマホ単体呼吸価値 |
| `LEARN_BASELINE` | `UNDECIDED_DEVICE` | 自分の落ち着きを知っておきたいあなたに。 / For noticing how your calm rhythm feels. | お守りペットは、あなたにぴったりです。 / Omamori Pet fits you naturally. | 毎日の小さな深呼吸を記録しつつ、Watch を追加すれば自動計測もできます。 / Log calm breaths daily now, and add watch sensing whenever you choose. | スマホ単体呼吸価値 |
| `UNDECIDED_CONCERN` | `HAS_WATCH` | Watch とつなげて使ってみたいあなたに。 / For pairing gently with your Pixel Watch. | お守りペットは、あなたにぴったりです。 / Omamori Pet fits you naturally. | 手首で見守りつつ、気になったときは振動の呼吸ガイドですぐ整えられます。 / Quiet watch awareness paired with gentle haptic breathing anytime. | Watch 見守り価値（運動除外） |
| `UNDECIDED_CONCERN` | `PHONE_ONLY` | まずはスマホだけで試したいあなたに。 / For trying gently on your phone first. | お守りペットは、あなたにぴったりです。 / Omamori Pet fits you naturally. | むずかしい設定なしで、開いたその場から1分間の深呼吸を始められます。 / No complex setup needed—begin a 1-minute breath right away. | スマホ単体呼吸価値 |
| `UNDECIDED_CONCERN` | `UNDECIDED_DEVICE` | まだ決まっていなくて大丈夫です。 / No need to decide right away. | まずは 1つだけ、試してみましょう。 / Let's try just one calm minute together. | 時計がなくても、スマホからいつでも穏やかな呼吸を始められます。 / You can start a gentle breathing session right from your phone anytime. | スマホ単体呼吸価値 |

---

## 5. 日英 `strings.xml` の初心者向け平易化計画（抜粋・正本）

- `HeroStatusCard` の基準行（`平常時の目安: 65 bpm`）は Watch 未接続（`lastWatchSyncMs == null && !isSimulated`）かつ Health Connect 未連携時は非表示とし、初心者を未説明の数値で迷わせない（HC 連携済み時のみ「Health Connect 基準: XX bpm」を小さく添え、Watch 同期後から通常表示する）。
- `TemplateCalmAdvisorEngine` に `isWatchConnected: Boolean` を追加し、Watch 未接続かつ `RESTING` のときは「時計がなくても、スマホだけで1分間の深呼吸を始められます。深呼吸したあとや日々の変化をここでお伝えします。」（En: `"You can start a 1-minute breath on your phone anytime—no watch required. Your reflections will appear here."`）を返し、Watch 接続歴があり外しているときのみ「Watch を手首につけると、いつもの目安に合わせた見守りを再開します。」を返す。
- `HealthConnectRecoveryCard` の分岐と動作：
  - オンボーディングで `PHONE_ONLY` を選んだ初心者のホームには最初から出さず（設定タブからいつでも連携可）、`HAS_WATCH` / `UNDECIDED_DEVICE`（または未回答）かつ `hcDismissedOnHome == false` のとき表示する。
  - `UPDATE_REQUIRED` 時：設定タブ・ホームともに `hc_update_required` を表示し、ボタンで Health Connect 更新画面（`ACTION_HEALTH_CONNECT_SETTINGS` / Play ストア `com.google.android.apps.healthdata` の安全インテント）を開く。
  - 権限拒否後（`hcPermissionAttempted && !hasHealthConnectPermission`）：文言を `hc_denied_recovery` に切り替え、「設定から許可を確認」（OS の Health Connect 設定画面を開く）＋「あとにする」（ホーム上のみ畳む `TextButton`）を表示する。
- `HeroStatusCard` の副ボタン「ウォッチで開く」は Watch 同期済みかつ非デモ時のみ表示する。送信失敗時は強制画面遷移せず、現在画面のまま「ウォッチに届きませんでした」＋「スマホで開く」アクション付き Snackbar で案内する。
- `MobileRepository.setDemoMode(LIVE_WATCH)` 解除時は最後に保存・受信した実測 `WatchLivePayload` へ復元（未受信なら `RESTING`＋`lastWatchSyncMs` クリア）し、「連携済みなのに未装着」の矛盾を防ぐ。
- `SettingsMobileScreen` で `alertsEnabled == false` のとき、感度スライダーとおやすみ時間スイッチを無効化し「通知をオンにすると調整できます」の補足を添える。

### Mobile (`mobile/src/main/res/values-ja/strings.xml` & `values/strings.xml`)
- **状態の文言（Watch 未接続時は「計測待ち／未装着」を出さない）**：
  - Watch 未接続時タイトル：`いつでも深呼吸できます` / `Ready for a calm breath`
  - Watch 未接続時説明：`スマホだけで1分間の深呼吸を始められます。Pixel Watch をつなぐと心拍も見守ります。` / `Start a 1-minute breath right on your phone. Pair a Pixel Watch anytime for heart rate awareness.`
  - `state_breathing_desc`：`4秒吸う・2秒止める・6秒吐く、やさしいリズムで整えています。` / `Inhale 4s, hold 2s, exhale 6s—moving at a gentle pace.`
  - `motion_still`：`落ち着いて静止中（3分以上）` / `Sitting calmly (3+ min still)`
  - `motion_moving`：`歩いたあと（運動による心拍を除外中）` / `Recently active (ignoring post-walk rise)`
  - `motion_off`：`Watch を外しています` / `Watch is off wrist`
- **AI・Health Connect・デモ・履歴**：
  - `ai_card_title`：`お守りからのひとこと` / `Omamori Note`
  - `ai_source_nano`：`端末内AI（Gemini Nano）で作成 · 外部送信なし` / `Created on-device with Gemini Nano`
  - `ai_source_local`：`端末内で作成 · 外部送信なし` / `Created on-device · Private`
  - `ai_download_btn`：`より自然なひとことを準備` / `Enable smarter on-device notes`
  - `ai_preparing`：`端末内AIを準備しています…` / `Preparing on-device AI…`
  - `ai_prepare_failed`：`準備できませんでした。いまは標準のやさしい言葉でお届けします。` / `Couldn't prepare right now. Using standard calm notes instead.`
  - `hc_title`：`健康データ連携（Health Connect）` / `Health Data Sync (Health Connect)`
  - `hc_missing_perm`：`いつもの落ち着いた心拍（安静時心拍）を読み取り、あなたに合った目安に合わせます。` / `Reads only your resting heart rate on-device to tailor your personal baseline.`
  - `hc_update_required`：`Health Connect の更新が必要です。更新するか、Watch の自動学習だけでそのまま使えます。` / `Health Connect needs an update. You can update it or rely on watch learning.`
  - `hc_denied_recovery`：`許可がオフになっています。設定から許可するか、このままでも深呼吸は使えます。` / `Permission is off. Enable it in Settings anytime, or keep using breathing as is.`
  - `settings_demo_section`：`おためし体験モード（デモ表示）` / `Preview & Demo Mode`
  - `settings_demo_subtitle`：`Watch がなくても「心拍が上がったときの通知や画面」を体験できます。` / `Try how alerts and calm states look even without a watch.`
  - `demo_exit_chip`：`実測にもどす` / `Exit demo`
  - `history_demo_summary`：`体験デモの記録を表示中（デモ呼吸: %1$d 回）` / `Showing demo preview (%1$d demo breath(s))`

### Wear OS (`wear/src/main/res/values-ja/strings.xml` & `values/strings.xml`)
- `ON_RESUME` でセンサー権限（心拍＋身体活動）および `POST_NOTIFICATIONS` 権限を再判定し、許可済みなら即 `BiometricMonitorService.start()` を実行する。
- `POST_NOTIFICATIONS` が未許可かつ `alertsEnabled == true` の場合は、`DemoSettingsWatchScreen` 通知セクション直下に「通知許可がオフです（設定でオンにする）」の控えめなボタンを出し、タップで許可要求／アプリ設定を開けるようにする。
- `HomeWatchScreen` の `snooze_cta`（「いま大丈夫 (1時間休止)」）押下後は `isCurrentlySnoozed` または一時フィードバックにより即座に `settings_snoozed_confirm`（`1時間おやすみにしました`）表示・連打抑止を行う。
- `status_breathing_detail`：`ゆっくり吸って吐くリズムで整えています。` / `Following a slow, gentle breathing pace.`（`4-2-6` を排除）
- `breathing_cycle_label`：`%1$d/%2$d回目` / `Set %1$d/%2$d`
- `calibration_running`：`基準を計測中… あと %1$d秒` / `Measuring baseline… %1$ds`
- `calibration_done`：`落ち着き基準を更新しました（%1$d bpm）` / `Resting baseline updated (%1$d bpm)`
- `action_calibrate_desc`：`座って1分間、普段の心拍を測ります` / `Sit still for 1 min to learn your resting rate`
- `action_exit_demo`：`デモを終了（実センサーへ）` / `Exit demo (Live sensors)`
- `settings_snoozed_confirm`：`1時間おやすみにしました` / `Paused for 1 hour`

---

## 6. UI パーツカタログ（`docs/UI_CATALOG.md` 追加予定）と `ART_DIRECTION` 整合

- `OmamoriTileService.kt`：`BREATHING` 状態を `0xFF93C7B5` / `0xFF0B251D`（`ART_DIRECTION.md` v1.1 青磁色）へ修正する。
- 新規パーツ（実装時に `docs/UI_CATALOG.md` へ登録、計 16 件以内で粒度を維持）：
  1. `OnboardingWindowDots`：最大 5 ドットの窓進捗表示。数字なし・タップ不可・末尾のみ完了色。
  2. `OnboardingChoiceCard`：3 択専用カード（`shapes.large`、`surfaceContainerLow` ↔ `primaryContainer`、押下弾み＋チェック）。
  3. `WatchSetupGuideCard`：`HAS_WATCH` 選択かつ未接続時に表示する 3 手順案内（`OutlinedCard` トークン流用・折りたたみ/非表示可）。

---

## 7. 検証計画

1. **ユニットテスト（`./gradlew --no-daemon testDebugUnitTest`）**：
   - `OnboardingModelTest`：窓ドット計算（`total=15` の 0..14 全位置で窓サイズ 5・1ページ1移動・開始位置・完了形）、選択 9 通りのマッチ宣言生成（保証表現の禁止・非断定表現チェック）、Q3 振動設定反映、価値並び替え、称賛文の段階別 3 パターン以上＋直前重複回避、非 PII 計測イベント構造。
   - `TemplateCalmAdvisorEngineTest`：Watch 未接続時・デモ時・平易化後の文言チェック。
   - `MobileRepository` のデモ切替→解除時の同期状態復元および履歴サマリー判定ロジックの単体テスト。
2. **ビルド確認**：
   - `:mobile:assembleDebug`、`:wear:assembleDebug`、および Android Lint の確認。
3. **実機・エミュレータ実画面確認**（`emulator-5556` Phone / `emulator-5554` Wear）：
   - 初回起動オンボーディング（全ページ・選択自動遷移・スキップ・あとで試す・接続直結）および再起動時の非表示確認。
   - 日のライト/ダーク、文字サイズ拡大、Reduced Motion、日本語/英語のレイアウト破綻がないこと。
   - Wear の権限許可・拒否後復旧・1分計測表示・デモ終了の実画面確認。
4. **未確認として残す境界**：
   - 人間（初見ユーザー）が触って答える 3 問（「何のためのアプリか」「最初に何をするか」「使ってみたいか」）は AI の実画面確認では代替できないため、実装報告時に「未確認」として明記し、人間確認用の手順を添えて引き渡す。

---

## 8. 審査・承認記録

- **独立審査（`ux-independent-reviewer`）**：初回審査（不合格：`[F-01]`〜`[F-05]`）→ 改訂 2.0 再審査（条件付き合格：`[R-01]`〜`[R-03]`）。
- **条件解消および実装開始OK（ディレクター主担当記録）**：`[R-01]`（15ページ統一・#12→#13遷移シーケンスと文言重複解消）、`[R-02]`（Watch未接続時65bpm非表示・AI未接続時文言・Health Connect `PHONE_ONLY`/`UPDATE_REQUIRED`/拒否後動作）、`[R-03]`（Wear `POST_NOTIFICATIONS` 拒否時設定誘導・ホーム1時間休止即時確認）を本計画へ反映完了。上記範囲について **実装開始OK** とする。
