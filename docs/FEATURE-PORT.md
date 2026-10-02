# Feature Port Checklist — from MTForum (reference) to MT Luntan (Compose)

Reference: https://github.com/yz8023/mt-lun-tan (v3.8, Java/Views)
Target: /workspace/mtLuntan (Kotlin + Compose)
Site: https://bbs.binmt.cc (Discuz + Comiis mobile template, Alibaba ESA WAF)

Status legend: [ ] not started | [~] in progress | [x] done | [-] intentionally deferred

## Round 0 — Content-not-viewable & network alignment (root cause)

- [x] Pure-Kotlin WAF `acw_sc__v2` solver (no Rhino), verified vs real challenges
- [x] WafInterceptor cookie merge on retry (preserves session cookies)
- [x] Thread list parser aligned to reference `li.forumlist_li` selectors + lazy-image attrs + hidden-post detection
- [x] Thread detail parser aligned to reference mobile Comiis template (`div.comiis_viewtit`, `div.comiis_postli`, `div.comiis_message`) with PC-table fallback
- [x] ThreadScreen fetches mobile `&mobile=2` viewthread (was desktop PC page)
- [x] Mobile UA aligned to reference (`SM-S918B / Chrome/120`)
- [x] RequestThrottle: foreground lane (no wait) + background token bucket (foreground param in Net)
- [x] WebView acw-cookie sync in the login flow (WebView self-solves ESA via JS; full session incl. acw_sc__v2 imported via importSession)

## Round 1 — Session & account

- [x] Multi-account login + switch (LoginScreen WebView 导入 → AuthRepository.activate + 每账号独立 cookie 文件)
- [x] Cookie snapshot whole-string save/restore (`CookieRepository.exportCookieString` + `cookieString` 列，重登后回存)
- [x] Password custody via Android KeyStore AES-GCM (`KeystoreCrypto`，界面只显示「已托管」，不回显明文)
- [x] SessionGuard: silent auto re-login on 403 / session drop（签到页探测 → 有托管密码则重登，`AuthRepository.ensureSession()` 供前台调用）
- [x] Account list in Mine screen with quick switch (guest → login)（我的页顶部账号芯片 + 账号管理页排序/启用/删除）

## Round 2 — Content & interaction enhancements

- [x] Browse history (HistoryStore) persisted locally（Room history 表，带 lastFloor/totalPages）
- [x] Favorites list + favorite state（本地 favorite 表 + 服务端 favorite 接口双向同步）
- [x] Code block fold + copy（`CodeBlockCard`：折叠/展开、单独复制、行号不进剪贴板）
- [x] Quote block fold + copy（`QuoteBlockCard`）
- [x] Post/reply copy: 顶栏复制整帖 + 楼层菜单（正文 / BBCode / 带楼层署名 / 只复制代码）
- [x] Like users list（`ForumRepository.likeUsers` + 点赞名单弹窗）
- [x] Hidden content in-place expand（`HideBlockCard` 原位解锁）+ 列表「含隐藏内容」标记
- [x] Relative image URL fix + placeholder-gif skip（`BbcBlocks.absUrl`，列表与详情统一）
- [x] Full-width in-place image + tap-to-preview（`ImageGallery`：双指缩放、拖动、上一张/下一张）

## Round 3 — Post / reply editor

- [x] BBCode editor with 21 preset tags + RGB picker + gradient text + live preview（`EditorScreen`）
- [x] Quick-reply phrase strip（设置里的常用语一行一条，编辑器一键插入）
- [x] Reply image upload / edit / report / delete（`ThreadExtrasParser.editForm` 整表单重放）
- [x] Attachment parse + download with coin-spend confirm（金币附件先确认再下载，落 MediaStore 下载目录）
- [x] Draft persistence（草稿箱 + 编辑器 2.5s 自动暂存）

## Round 4 — Multi-account auto sign-in + records

- [x] Daily scheduled sign-in（`CheckInWorker` + `SignScheduler`，默认 08:30，可在设置改时间）
- [x] One-click sign-in for all accounts（`IsolatedClient` 隔离会话，不动前台账号）
- [x] Per-account sign-in records（`sign_records` 表 + 签到记录页，按账号筛选）
- [x] Sign-in result notifications（汇总通知，可展开看每个账号的结果）
- [x] Account spacing / ESA throttle between accounts（默认 5 秒，设置里可调 0/3/5/10/20）
- [x] Record center（`RecordCenterScreen`：日志 / 解锁决策 / 耗时 / 历史 四个 Tab，简洁↔详细切换）
- [x] PerfLog load-timing instrumentation（网络 / 解析 / 总耗时三段埋点）

## Round 5 — Notices / PM / profile

- [x] Notice list + detail（卡片折叠 3 行，可展开全文 + 复制 + 打开原帖/用户页）
- [x] PM list + chat bubbles（`PmChatScreen` 左右气泡 + 输入框发送）
- [x] User profile page（资料、金币/积分、TA 的主题分页、关注/私信/拉黑入口）
- [x] Friend list + follow/unfollow（好友列表 + 服务端关注接口 + 本地关注态缓存）
- [x] Blacklist（本地黑名单 + 楼层直接折叠该用户发言）
- [x] Credit detail（积分/金币明细页）

## Round 6 — AI features

- [x] AI chat session list + store（Room ai_* 三张表，历史对话可删）
- [x] AI client + config（OpenAI 兼容 `/chat/completions`，Key 由用户自己填，可一键测连通）
- [x] AI summarize thread（AI 页「总结帖子」输入 tid，自动抓全文再总结）
- [x] Auto-reply engine + scheduler（进帖解锁「回复可见」，同帖 6 小时只回复一次，支持演练模式/每日上限）

## Round 7 — Theme & polish

- [x] Dark mode + theme colors（6 套预设主题、跟随系统/深/浅色）
- [x] Theme opacity presets（底栏与侧边栏共用的不透明度档位）
- [x] Gradient text（`GradientText` 8 套渐变 + 编辑器渐变预览）
- [x] Badge baseline per-account（按账号存角标基线，切号不再「历史全变新消息」）
- [x] Bottom bar tap-to-refresh; auto-hide on scroll（点当前 Tab 刷新 + 滚动自动隐藏，可关）

## Build & release

- [x] `:app:assembleDebug` / `:app:assembleRelease` 均可产出 APK（仓库未提交 keystore 时 release 自动退回 debug 签名，见 `app/build.gradle.kts`）
- [ ] 发布到 GitHub release（需要维护者本机用正式 keystore 重新签名后上传）
- [x] Keep WafChallengeSolver tests + ParserTest green（`./gradlew :app:testDebugUnitTest` 通过）

## 本轮补齐（对照 Java 版 v4.0–v4.7 的增量功能）

- [x] 阅读进度：记录「看到第几楼 / 第几页」，再次进帖给「继续阅读 / 从头看」入口
- [x] 离线阅读：断网时自动回落到离线快照，并在顶部提示「离线快照」
- [x] 导出 HTML / 纯文本（自包含样式，含全部楼层与时间署名）
- [x] 附件下载：>0 金币先弹「购买附件」确认，落地 MediaStore 下载目录
- [x] TID / UID 快速跳转：支持粘整条链接自动识别（导读页顶栏 + 我的页入口）
- [x] 会话自愈：`AuthRepository.ensureSession()`，掉线时用托管密码静默重登，帖子页错误态可直接「自动重登」
- [x] 通知详情：长通知展开全文 + 复制 + 打开（帖子 / 用户页都能跳）
- [x] 底部栏「点当前 Tab 再刷新」与切号后整页重载（`Refresh.tabTick` / `Refresh.generation`）
- [x] 构建健壮性：keystore 缺失时 release 自动退回 debug 签名，`assembleRelease` 不再直接失败

## 有意不做的部分

- MCP / Cloudflare Quick Tunnel（Java 版 v4.3/v4.4）：属于「把论坛内容暴露成 MCP 服务」的服务端能力，
  与客户端定位不同，且需要常驻进程与鉴权配置，本轮不做。
- 视频转 GIF（Java 版 v3.9）：依赖 ffmpeg 级别的转码，客户端体积与收益不成正比，
  图片上传已做等比压缩（≤900 KB）。如确需，可后续单独接 MediaCodec 方案。
