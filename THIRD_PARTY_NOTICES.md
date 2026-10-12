# 第三方与素材说明

## 睡前音乐

Vaporware / Calm Piano 1，作者The Cynic Project（cynicmusic）。作者发布页标注CC0。

- 来源：https://opengameart.org/content/calm-piano-1-vaporware
- 原录音：https://opengameart.org/sites/default/files/003_Vaporware_2.mp3
- CC0说明：https://creativecommons.org/publicdomain/zero/1.0/
- 修改：截取90秒、统一音量、开头3秒淡入、结尾8秒淡出、128kbps MP3。
- 文件：app/src/main/assets/audio/moonlight.mp3。
- SHA256：ce282ebbf3a0ff07dc15589a30543d93f9dd009a1c7181d87f710c07ac32bb46。

音乐原许可独立于项目代码许可。

## 图像

app/src/main/assets/art中的五张水獭和应用图像在本项目开发过程中生成，未采用外部水獭照片或现成游戏角色。项目公开源码不自动授权这些插画给其他项目。生成过程不等于法律权利审核。

## 构建入口

Gradle Wrapper JAR来源为Gradle官方v8.9.0版本，适用Apache License 2.0。完整许可证已包含在JAR内META-INF/LICENSE；原文：https://github.com/gradle/gradle/blob/v8.9.0/LICENSE 。本项目的简化启动脚本调用该JAR。

Wrapper JAR SHA256：498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17，已与https://gradle.org/release-checksums/核对。

JDK、Android SDK和Gradle工具本体由构建者自行安装，不放入本发布目录。
