# HappyEat 交付报告

日期：2026-10-16 · 会话：设计共识（grilling Q1–Q26）→ 工程 → 素材 → 模拟器实机验证 → **v2 迭代（真机反馈 6 项）→ v2 全量重取证**

## v2 迭代（真机试玩反馈）

1. BGM 循环 + 7 种合成音效（SoundPool 实装，res/raw）
2. 背景白天/黄昏/夜晚：每 60 秒阶段自动切换 + 设置可选
3. 帮助页（物品图例）+ 首次开局箭头教学浮层
4. 主菜单（开始游戏/排行榜/设置/帮助）+ Game Over 加回主菜单
5. 玩家跳跃：快速点按起跳，可躲地面猛兽
6. 掉落物类别光环（绿=食物/黄=载具/红=危险）+ 危险物调暗/食物提亮
   v2 证据：`out/verify/v2/`（截图/录像/代码快照）+ `out/cmp/*_v2.png`（16 张成对对比图）

## Shipped

- **Android APK**：`app/build/outputs/apk/debug/app-debug.apk`（debug 签名，minSdk 24 / targetSdk 34）
  - Kotlin + SurfaceView 自定义游戏循环，零第三方引擎
  - 玩法：无尽接物生存——拖动接豆/肉/派得分，躲石头/炸弹/刀，虎狮狼单线穿越，车/坦克骑乘护身（10 秒或 3 撞）
  - 生命 3 起步、每 200 分 +1 命上限 30、掉命 2 秒无敌；难度 5 分钟封顶 2.5 倍
  - 本机排行榜（Room）+ 免密用户名（SharedPreferences）+ Game Over 面板
  - 中英双语：跟随系统 + 齿轮内切换（AppCompat per-app locales，持久化）
- **全套 AI 素材**（GPT Image 2.5 Sunburst via flowith.io，13 张，品红色键抠图）：`app/src/main/assets/sprites/`
- **规格与验证**：`out/plan.md`（S1–S9/D1–D16 可观察项）；`out/verify.jsonl`（25/25 pass，contract_audit 闭环 ✓）；成对对比图 `out/cmp/`（D1–D16 共 16 张 SRC|REP）；原始证据在 `out/verify/`（截图 13 张 + 实机录像 2 段 + 对比帧）
- **文档**：`AGENTS.md`（设计共识 + 工程约定 + 构建命令 + 素材再生成管线）

## Deployed URL

N/A —— 原生 Android 应用，无 Web 部署。安装方式：

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
# 或 Android Studio 打开工程直接 Run
```

## Deferred

- **D5/D9 的长时间运行时观察**：送命（200 分/条）与 5 分钟难度封顶已代码实现并有 HUD 输出佐证，但本次实机session 未连续游玩 5 分钟以上，长时间曲线留待真机体验。
- **D16 真实音效**：SoundManager 为留接口的空实现（事件点已埋），未接音频资源。
- **骑乘视觉截图**：骑乘状态有 HUD 文本证据（骑乘中·剩 3 秒·撞击 2/3），载具精灵叠底玩家的清晰截图未捕获（载具生成率 4%，需更长游玩）。
- **Game Over 界面下齿轮不可达**：Game Over 面板为可点击全屏层，会吞掉 HUD 齿轮的点击（设置入口仅游戏进行中/暂停时可用）。符合当前设计最小化原则，记录为已知行为。
- 未做：发布签名/混淆配置验证、ProGuard release 构建、更多机型适配测试。

## 环境

- macOS + JDK 17（`gradle.properties` 固定）+ Gradle wrapper 8.9 + AGP 8.7.3 + Kotlin 2.1.0 + Room 2.6.1 (KSP)
- Android SDK `~/Library/Android/sdk`；模拟器 Medium_Phone_API_36.1（API 36.1 arm64）实机验证
- 构建：`./gradlew assembleDebug` · 测试：`./gradlew testDebugUnitTest` · lint：`./gradlew lint`
