# mtluntan 1.1.0 更新说明

> 本轮针对反馈的 8 个问题逐一修复，并按 `yz8023/ui` 的风格补齐**多主题引擎**与**交互动效反馈**。
> 构建说明：源码编译 + 打包全部在本地沙箱完成（未使用 GitHub Actions）。

## 一、修掉的 8 个问题

### 1. 账号管理处读取异常（数据被清空）
- **根因**：数据库 v1→v2 用的是 `fallbackToDestructiveMigration()`，只要版本号一变就**删库重建**，账号 / 历史 / 草稿 / 收藏全没了。
- **修复**：改成真实迁移，并且是「收敛式」的——所有 DDL 都从 Room 导出的 schema（`app/schemas/.../3.json`）原样取出，与实体定义一字不差：
  - `v1 → v2 → v3` 都走 `AppMigrations`，补列 + 补表，**数据全部保留**；
  - `CREATE TABLE IF NOT EXISTS` + `PRAGMA table_info` 判重，重复执行无副作用（已在真实 SQLite 上验证幂等）；
  - 只有「降级」这种无解情况才允许清库。
- **顺带**：v2 那时 `@Database(entities=…)` 只写了 6 张表，另外 7 张（签到记录 / 解锁认领 / 黑名单 / 关注 / AI 会话与消息 / 离线帖）在库里根本不存在，现在补全为 13 张。

### 2. 添加第二个账号时把第一个「读取」了（串号）
- **根因**：旧实现从 Cookie **名字**里猜用户名。Discuz 的 `xxx_auth` 前缀是随机盐（形如 `a1b2_2132_auth`），两个账号很容易猜成同一个 → 第二个账号直接覆盖了第一个。
- **修复**（与 Java 参考版 `LoginBottomSheet.doCookieLogin` 同思路）：
  1. 登录产生的整串 Cookie 先挂到**临时会话** `__pending_login__`；
  2. 请求 `home.php?mod=space&do=profile&mobile=2`（失败重试一次），由服务端返回真实 uid / 用户名 / 昵称 / 头像；
  3. 用**服务端确认的身份**入库，再导入 Cookie；
  4. 识别不到身份时不再瞎猜，改为提示手填名字（`用 Cookie 添加账号` 对话框，也支持手动指定昵称）。
- 账号列表新增 `displayName` / `subtitle()`，卡片一眼能分清是哪个号。

### 3. 底部「社区」闪退
- **根因**：`LazyColumn` 的 key 撞车（解析出的分组 `id` 常常是 0，多个分组 key 相同 → Compose 直接抛异常）。
- **修复**：分组 key 用 `"cat-$下标-$名字"`，版块 key 用 `"forum-$下标-$id-$名字"`；`load()` 抽出来可重试，新增「重新加载」按钮与空态提示，不再一进页面就崩。

### 4. 评论区自动下一页
- **修复**：帖子页新增 `extraPosts`（第 2 页起的回复累积展示）；当某页解析出 0 条回复而服务端还有下一页时，**自动请求下一页**（设置里可关：`自动加载下一页`）；
- 楼层支持 `RevealItem` 交错入场；黑名单楼层折叠成 `CollapsedFloor`（可一键恢复显示）；
- 记录「最远读到第几页」，回头翻旧页不会把阅读进度改小。

### 5. 主题配色没有覆盖全组件
- 新的 12 套调色板（+ 自定义种子色）：冰蓝 / 薄荷 / 落日 / 珊瑚 / 樱花 / 星夜 / 竹青 / 暖沙 / 素灰 / 鎏金 / 深海 / 石墨；
- 配色深浅用 `Slider` 连续调节，玻璃强度分「关 / 柔 / 强」三档；
- 统一组件层 `UiKit`：`MtCard` / `MtButton` / `MtSegmented` / `MtSectionHeader` / `MtScreen` / `AuroraBackground`；
- 列表卡片（帖子卡、版块卡）、底栏、抽屉、设置页全部走统一组件，换主题是**整体**变，不再有残留的旧色；
- 旧版 0..5 的配色会**就近映射**到新色板（0 MT蓝→冰蓝、1 森野绿→竹青、2 暖阳橙→落日、3 葡萄紫→星夜、4 樱花粉→樱花、5 青碧→深海），只做一次，不会「换主题反而变了个色」。

### 6. 消息部分与账号不同步
- 新增 `util/UnreadState`：未读快照**按账号**隔离（`clear` / `markAllRead`）；
- `AuthRepository.activate` 切号时 `Refresh.bumpGeneration()`，消息页监听账号 + generation 变化：先清空列表再拉取，切号瞬间不会还显示上一个账号的通知；
- 未读基线按账号记录，切号不会把历史消息全标成新消息；底部「消息」Tab 的角标跟着未读数走。

### 7. 签到失败
- 按 Java 参考版 `MtSignApi.doSign` 的顺序重写：
  1. 先 GET 伪静态页 `k_misign-sign.html` 拿 formhash（被 WAF/ESA 拦 → 明确提示，不再误判「签到成功」）；
  2. 已经是「今日已签」就直接返回，不重复提交；
  3. `plugin.php?id=k_misign:sign&operation=qiandao&format=text&formhash=…`；
  4. 失败回退伪静态按钮接口 `k_misign-sign.html?operation=qiandao&format=button&…&inajax=1`；
  5. **重新读页面才算数**（服务端为权威），奖励 / 排名解析补了 5 组 `lxreward` 正则 + 7 条文本兜底。
- 移动端 UA + `Referer: index.php`，失败重试保持幂等。

## 二、参考 `yz8023/ui` 补的多主题引擎与动效

- **主题引擎** `ui/theme/ThemeEngine.kt`：`MtPalette`（12 套 + 自定义）+ `Shade` 深浅 + `seedSchemeFor` 生成完整 `ColorScheme`（含 surfaceContainer 系列，避免各页面自己拼颜色）；
- **动效层** `ui/motion/`：
  - `pressScale`（按压缩放 + 阻尼）、`impactShake`（操作失败/命中反馈）、`idleBreathing`（底栏图标微呼吸）、`StaggeredReveal`（列表交错入场）、`mtSpecular`（玻璃高光）；
  - 全局开关 + 阻尼滑杆放在设置页，`Motion.enabled` / `dampingKnob` 由主题统一写入；
  - **关掉动效时直接渲染最终状态**，不会出现「内容永远不显示」。
- **液态玻璃**：`mtGlassSurface` 自绘（不依赖 `com.kyant.backdrop`，任何环境都能编）；
- **导航**：胶囊式 `LiquidBottomBar`（加权滑块指示器 + 消息角标）、`AuroraBackground` 极光背景、`ModalNavigationDrawer` 抽屉（签名头 + 全部入口），列表可自动隐藏底栏（可在设置里关）。

## 三、设置项新增
`主题深浅`、`玻璃强度`、`自定义种子色`、`动效开关`、`动效阻尼`、`自动加载下一页`、`底栏自动隐藏`、`隐藏黑名单楼层` 等，与 12 色板一起集中在「主题与外观 / 动效」两组。

## 四、构建产物

| 文件 | 大小 | SHA-256 |
| --- | --- | --- |
| `mtluntan-1.1.0-release-debugsigned.apk` | 3.60 MB | `3720a23c0dc85b839ddaa59462d3faf34a6ca9203980bbf06b7038631de55ad7` |
| `mtluntan-1.1.0-debug.apk` | 20.8 MB | `e3f5987c53755b8519045267995373bdef09b40aaac68b9cf9dbe895210f15c0` |

- `versionName 1.1.0` / `versionCode 2`，minSdk 26 / targetSdk 34；
- release 包因仓库内**没有** `mtluntan.keystore`，按 `resolvedKeystore` 规则回退用 debug 签名（与 1.0.0 一致，可直接覆盖安装）；
- 单元测试 10/10 通过（解析器 + WAF 挑战求解），`:app:assembleDebug`、`:app:assembleRelease` 均在本沙箱完成。
