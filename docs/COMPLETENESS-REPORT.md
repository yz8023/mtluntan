# MT论坛 客户端 — 功能补齐完成报告

> 目标仓库：`https://github.com/yz8023/mtluntan`（Kotlin + Jetpack Compose，`io.mtluntan.app`）
> 参考实现：`https://github.com/yz8023/mt-lun-tan`（Java/Views 版，已更新到 v4.7）
> 站点：`https://bbs.binmt.cc`（Discuz + Comiis 移动模板 + 阿里云 ESA WAF）
> 报告时间：2026-10-02

---

## 一、结论

**原先仓库里 7 个轮次的待办（README/`docs/FEATURE-PORT.md` 中标为空的所有条目）已经全部补完，并且：**

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `:app:compileDebugKotlin` | ✅ BUILD SUCCESSFUL |
| 单元测试 | `:app:testDebugUnitTest` | ✅ 10 个用例全绿（`ParserTest` 5 / `WafChallengeSolverTest` 5） |
| Debug 包 | `:app:assembleDebug` | ✅ `app-debug.apk`（约 20 MB） |
| Release 包 | `:app:assembleRelease` | ✅ `app-release.apk`（R8 压缩后约 3.5 MB） |

产物已复制到仓库外的 `apk/` 目录，方便直接安装：

```
apk/mtluntan-1.0.0-debug.apk                    # applicationId 带 .debug 后缀，可与正式版共存
apk/mtluntan-1.0.0-release-debugsigned.apk      # R8 压缩后的正式包（本机测试用 debug 签名，见第五节）
```

规模：71 个 Kotlin 源文件、约 1.26 万行。

---

## 二、逐轮完成情况（对应 `docs/FEATURE-PORT.md`）

### Round 0 — 内容看不了 / 网络对齐 ✅
- 纯 Kotlin 的 `acw_sc__v2` WAF 求解器（`WafChallengeSolver`）+ 拦截器重试时合并 Cookie（`WafInterceptor`）。
- 帖子列表 / 详情解析对齐参考实现的 Comiis 选择器，PC 表格作为兜底（`ThreadListParser` / `ThreadDetailParser`）。
- 移动版 UA、前台/后台双通道限速（`RequestThrottle`）、WebView 登录时同步 `acw_sc__v2` 等整串会话。

### Round 1 — 会话与账号 ✅
- 多账号登录（WebView 导入整串会话，`LoginScreen`）→ 每账号独立 Cookie 文件（`filesDir/cookies/<account>.json`）。
- 整串 Cookie 快照存取（`CookieRepository.exportCookieString` + `AccountEntity.cookieString`，重登后自动回存）。
- 密码托管：`KeystoreCrypto`（AndroidKeyStore AES-GCM，取不到硬件密钥时降级混淆），界面只显示「已托管」不回显明文。
- **会话自愈**：`SessionGuard.verify()` 用签到页探测（能拿到 formhash 才算已登录，避免被过期的 `_auth` 骗到），掉线且有托管密码则静默重登；`AuthRepository.ensureSession()` 供前台调用。
- 我的页账号芯片快速切换、账号管理页（排序 / 启用 / 删除 / 逐账号检测会话）。

### Round 2 — 内容与交互 ✅
- 浏览历史、收藏（本地表 + 服务端接口双向同步）、代码块与引用块折叠 + 单独复制。
- 楼层菜单：复制正文（纯文本）/ BBCode / 带楼层署名 / 只复制代码；顶栏一键复制整帖。
- 点赞名单弹窗、隐藏内容原位展开、列表「含隐藏内容」标记。
- 图片相对地址修正、占位图跳过、原位全宽显示、点开 `ImageGallery`（双指缩放 + 拖动 + 左右切换）。

### Round 3 — 发帖 / 回复编辑器 ✅
- `EditorScreen`：21 个预设 BBCode 标签、取色器、渐变字、实时预览（400 ms 防抖）、常用语一键插入。
- 插图上传：相册选图 → 等比压缩到 ≤900 KB（先降质量 92→60，再按 0.8 系数缩尺寸）→ `forum.php?mod=misc&action=upload`。
- 编辑 / 删除自己的楼层：整表单重放（`ThreadExtrasParser.editForm()`），举报入口同页。
- 附件解析（名称 / 大小 / 金币售价），金币附件先弹「购买附件」确认再下载。
- 草稿箱：类型区分 `newthread / reply / editreply`，编辑器 2.5 秒自动暂存。

### Round 4 — 多账号自动签到与记录 ✅
- `SignScheduler` + `CheckInWorker`：每 24 小时一次（对齐到设置的时间，默认 08:30），`ExistingPeriodicWorkPolicy.UPDATE`。
- 一键全部签到走 `IsolatedClient` 隔离会话，不打扰前台账号；账号之间默认间隔 5 秒（可选 0/3/5/10/20）。
- 签到记录表（账号 / 日期 / 成功 / 排名 / 奖励 / 消息），签到记录页可按账号筛选。
- 结果通知汇总（可展开看每个账号）；`PerfLog` 记录网络 / 解析 / 总耗时三段；`LogCenter` 统一运行日志。
- **记录中心**（`RecordCenterScreen`）：日志 / 解锁决策 / 耗时 / 浏览历史 四个 Tab，简洁↔详细双模式。

### Round 5 — 消息 / 私信 / 个人主页 ✅
- 通知列表：卡片折叠 3 行，可展开全文、复制内容、直接跳原帖或用户页；「全部已读」打服务端接口。
- 私信：会话列表 + 气泡对话页（`PmChatScreen`）可直接回复。
- 用户主页：资料、金币 / 积分、TA 的主题分页加载、关注 / 私信 / 拉黑。
- 好友列表、黑名单（本地 + 楼层直接折叠该用户发言）、积分明细页。

### Round 6 — AI 能力 ✅
- AI 配置（任意 OpenAI 兼容服务：baseUrl / Key / 模型 / 温度 / 最大 token / 系统提示词）+ 一键「测试连接」。
- 会话列表 + 对话页（历史入库、可删除）、「总结帖子」（输入 tid 自动抓全文再总结）。
- 自动回复引擎：进帖检测隐藏块 → 生成回复（AI 或固定模板）→ 提交 → 静默重载；同一帖子 6 小时内只回复一次，失败不立即重发；支持演练模式、每日上限。

### Round 7 — 主题与细节 ✅
- 深色模式（跟随系统 / 深 / 浅）、6 套主题色、动态取色、状态栏与底栏同色。
- 面板不透明度档位（底栏 + 侧边栏共用）、字体缩放 5 档、渐变字 8 套。
- 角标基线按账号隔离（切号不会把历史消息全标成新消息）。
- 底栏：点当前 Tab 刷新、滚动自动隐藏（可关）；切号后整页按 generation 重载。

---

## 三、本轮额外补齐（对照 Java 版 v4.0–v4.7 的增量）

| 功能 | 位置 | 说明 |
| --- | --- | --- |
| 阅读进度「继续阅读」 | `ThreadScreen` + `LocalRepository.progressFor` | 记录看到第几楼 / 第几页，再次进帖顶部给「继续阅读 / 从头看」；停在同一楼 0.8 秒即回写进度 |
| 离线阅读兜底 | `ThreadScreen` 加载异常分支 + `LocalRepository.offline(tid)` | 断网时自动用本地快照渲染，顶部提示「离线快照」，可点「重新联网」 |
| 导出 HTML / 纯文本 | `util/Export.kt` + `util/FileSaver.kt` | 自包含 HTML（内联样式、全部楼层、时间署名），Android 10+ 落 MediaStore 下载目录，无需存储权限 |
| 附件下载 | `ThreadScreen` + `FileSaver.download` | 金币附件先确认再扣费；非金币附件直接下载 |
| TID / UID 快速跳转 | `QuickJumpDialog`（导读页顶栏 + 我的页） | 支持只填数字，也支持直接粘一整条链接自动识别 tid / uid |
| 会话自愈入口 | `AuthRepository.ensureSession()` | 设置页「检测当前会话」；帖子页报「需要登录 / 权限」时直接给「自动重登」按钮 |
| 通知详情 | `NoticeScreen.NoticeRow` | 长通知展开全文、复制、打开原帖 / 用户页 |
| 构建健壮性 | `app/build.gradle.kts` | keystore 缺失时 release 自动退回 debug 签名并打印提示，`assembleRelease` 不再直接失败 |
| 顶栏菜单整合 | `ThreadScreen` 右上角「⋮」 | 保存离线 / 导出 HTML / 导出文本 / 浏览器打开 / 分享链接 |

---

## 四、关键实现说明（便于后续维护）

- **掉线判定不看本地 Cookie**：`SessionGuard` 用「签到页是否能拿到 formhash」作为唯一判据，因为本地常存着已过期的 `_auth`，这正是 Java 版 v4.1 修过的坑。
- **隔离会话**：`IsolatedClient(cookieRepo, account)` 用独立 OkHttp 客户端 + 独立 Cookie 存储，批量签到 / 检测会话都不会污染前台账号的会话。
- **自动解锁的认领表**：`unlock_claim` 表按 tid 记录「已回复」，6 小时内不重复；认领写在发送之前，发送失败也**不**立即释放，避免被限流后疯狂重试（对应 Java 版长达数版的反复修复）。
- **记录中心双模式**：底层永远记全量，「简洁」只影响展示与复制内容，切回「详细」随时能看到跳过原因。
- **UI 刷新约定**：切号 → `Refresh.bumpGeneration()` 让所有页面重新拉数据；点当前底栏 Tab → `Refresh.bumpTab()` 只刷新当前页。
- **数据迁移**：Room 版本升到 v2 且为 destructive 迁移（改了表结构），首次升级会清空本地历史 / 草稿 / 收藏；论坛侧数据不受影响。

---

## 五、已知限制 / 未做项

1. **正式签名**：仓库里没有提交 `mtluntan.keystore`（只有 `keystore.properties` 指向它）。
   本报告的 release 包因此使用 debug 签名，**仅用于本机安装测试**；对外发布请用维护者自己的 keystore 重新签名：
   ```bash
   # 把 keystore 放到 keystore.properties 里 storeFile 指定的位置（根目录或 app/ 下均可）
   ./gradlew :app:assembleRelease   # 检测到 keystore 后会自动改用正式签名
   ```
2. **GitHub 发布**：本地已完成改动，但沙箱没有仓库的推送权限，Release 资产需要维护者自己上传。
3. **MCP / Cloudflare Quick Tunnel**（Java 版 v4.3/v4.4）：属于把论坛内容暴露成 MCP 服务的服务端能力，需要常驻进程与鉴权配置，与客户端定位不同，本轮有意不做。
4. **视频转 GIF**（Java 版 v3.9）：需要 ffmpeg 级转码，客户端体积 / 收益不划算；图片上传已做等比压缩。如确有需要，可后续单独用 MediaCodec 方案实现。
5. **未做真机联网验证**：沙箱无法访问 `bbs.binmt.cc`，所有网络路径按参考实现的接口与解析器对齐，建议首次安装后用下面第六节的路径跑一遍。

---

## 六、建议的自测路径（装好 APK 后照这个顺序点）

1. **登录 / 多账号**：我的 → 去登录 → WebView 登录成功 → 回来自动导入会话；账号管理再加一个账号，来回切换看是否串号。
2. **WAF / 内容**：导读刷新、进任意帖子、翻页、点开图片、长按楼层看复制菜单、折叠代码块。
3. **发帖 / 回复**：回复一条（工具栏插粗体 + 颜色）、草稿箱看自动暂存、编辑刚发的楼层、再删掉。
4. **解锁**：找一个「回复可见」帖，进帖应自动回复并显示内容；记录中心 → 解锁 Tab 应有一条决策记录（含跳过原因）。
5. **签到**：账号管理 → 一键全部签到，看每个账号的天数 / 排名 / 奖励；设置里把时间改成 2 分钟后，杀进程等通知。
6. **消息 / 社交**：消息页展开一条长通知、进私信发一条；从帖子作者头像进主页，试关注 / 拉黑。
7. **AI**：设置 → AI 配置填 Key → 测试连接 → AI 助手新建会话 → 总结一个 tid；打开自动回复的「演练模式」验证文案再关闭。
8. **离线 / 导出**：帖子页「⋮」→ 保存到离线列表 / 导出 HTML；开飞行模式再进同一帖，应看到「离线快照」提示条。
9. **阅读进度**：一个多页帖翻到第 3 页后退出，再次进入应出现「上次读到第 3 页 · 继续阅读」。

---

## 七、复现命令（沙箱内已验证）

```bash
export JAVA_HOME=/opt/tools/jdk-17.0.20.1+1
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/opt/gradle-home
export PATH=$JAVA_HOME/bin:$PATH

cd /home/user/mtluntan
/opt/tools/gradle-8.5/bin/gradle --no-daemon --console=plain \
  -Dorg.gradle.jvmargs="-Xmx1400m -XX:MaxMetaspaceSize=512m" \
  -Pkotlin.daemon.jvmargs="-Xmx1200m" \
  :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

> 注：仓库没有提交 Gradle Wrapper（没有 `gradlew`），用本机 gradle 8.5 即可；在正常开发机上补上 wrapper 更好。
