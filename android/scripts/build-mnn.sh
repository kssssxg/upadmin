#!/usr/bin/env bash
set -euo pipefail

# Build the MNN + MNN-LLM shared libraries from source for Android.
# Usage: ./scripts/build-mnn.sh [version] [abi...]
# Defaults: version=3.4.1, abi list = "arm64-v8a armeabi-v7a"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${1:-3.4.1}"
shift || true
ABIS=("$@")
if [ ${#ABIS[@]} -eq 0 ]; then
  ABIS=("arm64-v8a" "armeabi-v7a")
fi

NDK_ROOT="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [ -z "$NDK_ROOT" ]; then
  echo "ANDROID_NDK_HOME/ANDROID_NDK_ROOT is not set." >&2
  exit 1
fi

SRC_DIR="$ROOT_DIR/.mnn-src"
BUILD_ROOT="$ROOT_DIR/.mnn-build"
JNI_LIBS_DIR="$ROOT_DIR/app/src/main/jniLibs"

rm -rf "$BUILD_ROOT"
mkdir -p "$BUILD_ROOT" "$JNI_LIBS_DIR"

if [ ! -d "$SRC_DIR/.git" ]; then
  rm -rf "$SRC_DIR"
  git clone --depth 1 --branch "$VERSION" https://github.com/alibaba/MNN.git "$SRC_DIR"
else
  (cd "$SRC_DIR" && git fetch --depth 1 origin "$VERSION" && git checkout "$VERSION")
fi

for ABI in "${ABIS[@]}"; do
  echo "▶ Building MNN for $ABI (version $VERSION)"
  BUILD_DIR="$BUILD_ROOT/$ABI"
  rm -rf "$BUILD_DIR"
  mkdir -p "$BUILD_DIR"
  pushd "$BUILD_DIR" >/dev/null
  cmake "$SRC_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release \
    -DMNN_BUILD_LLM=ON \
    -DMNN_SUPPORT_TRANSFORMER_FUSE=ON \
    -DMNN_ARM82=ON \
    -DMNN_USE_LOGCAT=ON \
    -DMNN_BUILD_SHARED_LIBS=ON \
    -DMNN_BUILD_BENCHMARK=OFF \
    -DMNN_BUILD_TEST=OFF \
    -DMNN_BUILD_DEMO=OFF \
    -DMNN_BUILD_TOOLS=OFF \
    -DMNN_WIN_RUNTIME_MT=OFF
  cmake --build . --target MNN llm -j"$(nproc)"

  OUT_DIR="$JNI_LIBS_DIR/$ABI"
  mkdir -p "$OUT_DIR"
  find . -name "*.so" -maxdepth 4 -print -exec cp {} "$OUT_DIR"/ \;
  echo "✅ Copied libs to $OUT_DIR"
  popd >/dev/null
done

echo "MNN build finished. Libraries are under app/src/main/jniLibs/"
