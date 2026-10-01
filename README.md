# MT论坛 客户端 (MT Luntan)

第三方 Android 客户端，用于浏览论坛 `https://bbs.binmt.cc`（Discuz + Comiis 模板）。

> 声明：本项目为个人学习用途的第三方客户端，与论坛官方无关。请遵守站点规则，合理使用。

## 功能

- 导读：最新 / 新增 / 热门 / 精华
- 社区：板块分类浏览、帖子列表
- 帖子详情：PC 模板解析、分页浏览、图片 / 代码 / 引用 BBCode 渲染
- 发帖 / 回复 / 编辑
- 消息：通知 + 私信
- 签到（k_misign 插件）、每日自动签到
- 账号管理：多账号、WebView 登录导入会话、账号隔离 cookie
- 浏览历史、草稿箱、帖子收藏、阅读进度
- 深色模式、动态取色、桌面模式、字体缩放
- 自动通过 WAF 验证（acw_sc__v2）

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- Room（账号 / 历史 / 草稿 / 收藏 / 阅读进度 / 帖子缓存）
- DataStore（设置）
- OkHttp + jsoup（网络 / 解析）
- Rhino（WAF 挑战脚本执行）
- WorkManager（定时签到）
- Coil（图片加载）

## 构建

环境要求：JDK 17、Android SDK（minSdk 26 / target 34）、Android Gradle Plugin 8.x。

```bash
# 拷贝 SDK 路径（可选，也可用环境变量 ANDROID_HOME）
echo "sdk.dir=/path/to/android-sdk" > local.properties

# debug 包
./gradlew :app:assembleDebug

# 正式包（需在 keystore.properties 提供签名信息）
./gradlew :app:assembleRelease

# 单测
./gradlew :app:testDebugUnitTest
```

产出 APK 位于 `app/build/outputs/apk/`。

## 下载

最新正式版 APK 见仓库 [Releases](https://github.com/yz8023/mtluntan/releases) 页面。

## License

Apache-2.0