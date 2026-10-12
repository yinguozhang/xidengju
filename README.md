# 熄灯局

帮助大学生在自己约定的时间结束手机娱乐。Android 8.0及以上，当前0.8.1测试版。提前30分钟收尾、娱乐应用门禁、明日计划与90秒钢琴休息帮助用户下线；不判断用户是否睡着。休息时可反复切换音乐与静音，不重置倒计时。

## 功能

- 用户选择已安装娱乐应用，必要应用受保护，可补充手动保护名单。
- 收尾任务与5分钟刻度的具体提醒时刻，不越过下线时间。
- 每晚一次15分钟延迟，紧急临时解锁或结束当晚计划。
- 跨午夜保留夜间状态，延迟后尝试恢复尚未结束的提醒。
- 前台提示、系统通知与用户授权的无障碍覆盖页。
- 明日计划与90秒舒缓钢琴，离开页面或音频被抢占时停止。
- 本机睡眠日记、起床不早于醒来、七日柱形与折线周报。

## 安装与限制

普通用户请前往[0.8.1 测试版下载页](https://github.com/yinguozhang/xidengju/releases/tag/v0.8.1)，下载附件 APK 后在 Android 手机上安装。已有官方测试版请直接覆盖安装，不要先卸载。

遇到问题可在[Issues](https://github.com/yinguozhang/xidengju/issues)反馈，说明手机型号、Android 版本、应用版本与复现步骤；请勿上传私人日记、账号信息或签名密钥。

APK放GitHub Releases。0.8测试包沿用0.7开发签名，可覆盖安装保留本机数据。不同电脑自行构建的debug签名通常不同，不能直接覆盖发行测试包。

开启通知、准时提醒和所需无障碍服务，在运行保障页完成后台测试。强行停止后必须重新打开。Doze与厂商后台限制影响到达，锁屏和必要应用上仅依赖通知，不能保证覆盖所有系统界面。应用不终止其他应用。参考[Android省电说明](https://developer.android.com/training/monitoring-device-state/doze-standby)。

日记仅在本机，暂不提供同步和导出，卸载/清除数据会删除记录。详见[隐私说明](PRIVACY.md)、[版本说明](RELEASE_NOTES.md)和[手机回归](TESTING.md)。

## 构建

准备JDK 17、Android SDK Platform 35、Build Tools 35.0.0。Gradle Wrapper固定8.9，Android Gradle Plugin固定8.7.3。必需素材已包含在app/src/main/assets，不依赖项目外的个人目录。Wrapper JAR已核对[Gradle官方校验值](https://gradle.org/release-checksums/)，分发包SHA256也已固定。

Android Studio直接打开项目、配置SDK后构建debug。命令行设置JAVA_HOME、ANDROID_HOME，在项目目录运行：

```powershell
./gradlew.bat :app:assembleDebug
```

Linux/macOS执行`sh ./gradlew :app:assembleDebug`。首次构建会下载Gradle和依赖，输出app/build/outputs/apk/debug/app-debug.apk。

Windows另提供离线构建入口，需要Node.js和上述JDK/SDK：

```powershell
./build-local.ps1 -JdkRoot 'JDK17目录' -SdkRoot 'Android SDK目录'
```

只生成本机开发签名测试包，私钥存于被忽略的.local-signing目录，不是正式发布签名配置。原开发环境也可传-ToolsRoot指定工具目录。

## 验证

Windows规则与接收器测试见TESTING.md，可传-JdkRoot。测试使用Android接口替身，不模拟厂商省电、前台窗口或手机音频，不能代替手机回归。

## 权利与素材

源码公开供查看与反馈，保留所有权利；暂不授权修改、再发布或商用，见[LICENSE](LICENSE)。当前不是采用开放许可证的开源项目。

音乐Vaporware由The Cynic Project发布，适用CC0；图像及构建入口说明见[素材清单](THIRD_PARTY_NOTICES.md)。第三方许可独立于项目源码许可。

上传只使用整理后的项目目录。不要提交work工具包、.local-signing、.p12/.jks/.keystore私钥、local.properties、本机日志和build缓存。APK作为Release附件发布。
