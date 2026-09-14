[![Build Status](https://github.com/connectbot/connectbot/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/connectbot/connectbot/actions/workflows/ci.yml)

# ConnectBot

ConnectBot 是一款面向 Android 的 [Secure Shell（SSH）](https://en.wikipedia.org/wiki/Secure_Shell)
客户端，让你通过加密的安全连接接入远程服务器。

## 如何安装

### Google Play

[![Get it on Google Play][2]][1]

  [1]: https://play.google.com/store/apps/details?id=org.connectbot
  [2]: https://developer.android.com/images/brand/en_generic_rgb_wo_60.png

最简单的方式就是从 [Google Play 商店安装][1]。
如果你是通过下载的 APK 安装的，Google Play 商店可以把你已安装的版本升级到最新版。
但一旦升级过，*你就不能再从 GitHub 的 releases 安装该应用了*
（因为密钥轮换把包签名升级到了更安全的算法）。

### 下载 release

ConnectBot 可以从 GitHub 的 [releases](
https://github.com/connectbot/connectbot/releases) 下载。有两个版本：

-  `google` —— 使用 Google Play Services 处理加密组件升级的版本
-  `oss` —— 把加密组件直接打进 APK 里的版本，APK 体积会大几 MB

## 架构

### 主要依赖

ConnectBot 依赖另外两个库来提供功能：
* [ConnectBot Terminal](https://github.com/connectbot/termlib) —— 应用使用的
  终端模拟器，同样由 ConnectBot 作者 Kenny Root 创建并维护。
* [ConnectBot 维护的 Trilead SSH-2 分支](https://github.com/connectbot/sshlib)
  —— 基于 Christian Plattner 编写的 Trilead SSH-2 Java 库重度改造的分支。

## 编译

### Android Studio

在 [Android Studio](https://developer.android.com/studio/) 中开发 ConnectBot
最方便。你可以在项目创建界面直接通过 GitHub URL 导入本项目。

### 命令行

要使用 `gradlew` 编译 ConnectBot，必须先通过 `ANDROID_SDK_HOME` 环境变量
指定 Android SDK 的位置。然后调用 Gradle wrapper 构建：

```sh
./gradlew build
```

### 持续集成

ConnectBot 使用 [GitHub Actions](https://github.com/connectbot/connectbot/actions)
做持续集成，工作流定义在 `.github/workflows/ci.yml`。

#### 用 act 在本地运行工作流

通常直接运行 `./gradlew build` 就能覆盖 GitHub Actions 持续集成流程里的所有
检查，但也可以用 [`nektos/act`](https://github.com/nektos/act) 在本地运行
GitHub Actions 工作流。这需要安装并运行 Docker。

运行主 CI 工作流（`ci.yml`）：

```sh
act -W .github/workflows/ci.yml
```

## MCP 服务器（AI 集成）

ConnectBot 可作为 [MCP（Model Context Protocol）](MCP_README.md) 服务器运行，
允许 AI 助手（如 AiCode）通过 MCP 协议访问 SSH 连接管理功能。详见
[MCP_README.md](MCP_README.md)。
