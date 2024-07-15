#!/bin/bash

# NDK路径和工具链配置
NDK_PATH=/Users/yanzhang/Library/Android/sdk/ndk/27.0.11902837
TOOLCHAIN=$NDK_PATH/toolchains/llvm/prebuilt/darwin-x86_64
API=28 # 设置API等级，根据需要调整

# 编译器
CC=$TOOLCHAIN/bin/aarch64-linux-android$API-clang
CXX=$TOOLCHAIN/bin/aarch64-linux-android$API-clang++

# FFmpeg源代码目录
FFMPEG_SRC=./ # 修改为FFmpeg源代码的实际路径

# 输出目录
PREFIX=./android/arm64-v8a

# 进入FFmpeg源代码目录
cd $FFMPEG_SRC

# 配置FFmpeg
./configure \
    --prefix=$PREFIX \
    --target-os=android \
    --arch=aarch64 \
    --cpu=armv8-a \
    --enable-cross-compile \
    --cross-prefix="$TOOLCHAIN/bin/aarch64-linux-android-" \
    --cc=$CC \
    --cxx=$CXX \
    --sysroot="$TOOLCHAIN/sysroot" \
    --extra-cflags="-Os -fPIC" \
    --extra-ldflags="" \
    --disable-static \
    --enable-shared \
    --disable-doc \
    --disable-programs \
    --disable-everything \
    --enable-small

# 编译并安装
make clean
make -j$(sysctl -n hw.ncpu)
make install

# 输出完成信息
echo "FFmpeg build completed"
