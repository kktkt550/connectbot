#!/bin/sh
# ConnectBot 一键构建脚本（适配 ARM64 proot 容器环境）
#
# 环境问题与对策（均已实测验证）：
# 1. JVM java.io.File.delete() 对目录失效（proot 缺陷）→ 用 strace 包装构建修复
# 2. KSP 增量快照在本容器必然损坏 → ksp.incremental=false（gradle.properties），
#    KSP 每次全量执行，换取构建稳定
# 3. 不用 Gradle daemon：daemon 与 strace 的等待模型冲突（构建后 strace 挂住），
#    且 --no-daemon 行为可控；编译器冷启动成本约 2-3 分钟
#
# 用法：sh build.sh [其他 gradlew 参数原样透传]
set -e
cd "$(dirname "$0")"
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk

echo "==> [$(date +%H:%M:%S)] 打包 APK..."
strace -qq -f -e trace=none -e signal=none -o /dev/null \
    sh gradlew :app:assembleOssDebug -x :app:easylauncherOssDebug --no-daemon "$@"

echo "==> [$(date +%H:%M:%S)] 完成"
ls -lh app/build/outputs/apk/oss/debug/app-oss-debug.apk
