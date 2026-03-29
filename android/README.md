# 攻略UP管理员 - Android 原生版

将原 iApp 项目转换为标准 Android 原生应用，新增本地大模型（MNN）离线推理能力。

## 功能特性

| 功能 | 说明 |
|------|------|
| 📱 原生 Android | 标准 Gradle 项目，可用 Android Studio 直接打开 |
| 🌐 CORS 代理 | 所有 API 请求通过原生 OkHttp 代理，彻底解决跨域问题 |
| ☁️ 在线 API | 支持原有联网 API（默认模式），可在设置中自定义端点/Key/模型 |
| 🤖 本地模型 | 集成 MNN-LLM 引擎，支持 0.5B～7B 模型本地离线推理 |
| 🔄 自由切换 | 菜单一键切换在线 API / 本地模型，UI 全部由 H5 实现 |
| 📥 内置下载 | 内置 6 个 MNN 格式模型下载链接（Qwen2.5 / Llama 3.2）|

## 项目结构

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/me/chat/ai/up/admin/
│   │   │   ├── App.kt                        # Application 类（通知渠道）
│   │   │   ├── MainActivity.kt               # WebView 宿主 + CORS 拦截
│   │   │   ├── bridge/
│   │   │   │   └── AppJsBridge.kt            # JS ↔ Android 桥接
│   │   │   ├── mnn/
│   │   │   │   ├── ModelInfo.kt              # 模型元数据 + 内置下载目录
│   │   │   │   ├── ModelManager.kt           # 模型文件管理
│   │   │   │   └── MNNLLMEngine.kt           # MNN JNI 封装
│   │   │   └── download/
│   │   │       └── ModelDownloadService.kt   # 前台下载服务
│   │   ├── assets/web/                       # H5 页面（原 iApp res/）
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/libs.versions.toml
├── settings.gradle.kts
└── build.gradle.kts
```

## 快速开始

### 1. 克隆 / 下载

```bash
git clone https://github.com/kssssxg/upadmin.git
cd upadmin/android
```

### 2. 配置 MNN 原生库（必须）

本地模型功能需要 MNN 动态链接库（`.so`）。请按以下步骤获取：

1. 前往 [MNN Releases](https://github.com/alibaba/MNN/releases)，下载最新 `MNN-Android-*.zip`。
2. 解压后将以下文件复制到项目的 `jniLibs` 目录：

```
android/app/src/main/jniLibs/
├── arm64-v8a/
│   ├── libMNN.so
│   ├── libMNN_Express.so
│   └── libmnn_llm.so
└── armeabi-v7a/
    ├── libMNN.so
    ├── libMNN_Express.so
    └── libmnn_llm.so
```

> **注意**：若不配置 `.so` 文件，应用仍可正常运行，但「本地模型」功能将被禁用，
> 只能使用在线 API 模式。

### 3. 用 Android Studio 打开

1. 打开 Android Studio → **Open** → 选择 `upadmin/android` 目录
2. 等待 Gradle sync 完成
3. 点击 **Run** 或 `Shift+F10`

### 4. 使用本地模型

1. 打开 App → 点击右上角菜单 ≡
2. 点击「🤖 本地模型 (MNN)」
3. 选择一个模型，点击「下载 xxxMB」
4. 下载完成后点击「加载」
5. 开启「启用本地模型推理」开关 → 保存设置
6. 返回主界面，现在对话将在本地运行！

## 内置模型列表

| 模型 | 大小 | 文件大小 | 特点 |
|------|------|----------|------|
| Qwen2.5 0.5B (INT4) | 0.5B | ~450 MB | 超轻量，速度最快 |
| Qwen2.5 1.5B (INT4) | 1.5B | ~1.1 GB | 轻量级，平衡速度与效果 |
| Qwen2.5 3B (INT4)   | 3B   | ~2.1 GB | 中型，效果较好 |
| Qwen2.5 7B (INT4)   | 7B   | ~4.8 GB | 旗舰，效果最佳 |
| Llama 3.2 1B (INT4) | 1B   | ~800 MB | Meta 模型，英文支持强 |
| Llama 3.2 3B (INT4) | 3B   | ~2.0 GB | Meta 模型，推理能力强 |

## CORS 解决方案

应用通过两层机制解决跨域问题：

1. **WebView 配置层**：设置 `allowUniversalAccessFromFileURLs = true` 允许 `file://` 页面发起跨域请求。
2. **原生代理层**：`AppJsBridge.proxyPost()` 方法将 API 请求通过原生 OkHttp 发送，完全绕开浏览器 CORS 限制，并在响应头中添加 `Access-Control-Allow-Origin: *`。

## JS ↔ Android 通信接口

H5 页面通过 `window.Android` 对象与原生层通信：

```javascript
// 获取模型列表（JSON）
Android.getModelList()

// 下载模型
Android.downloadModel(modelId)

// 加载模型
Android.loadModel(modelId)

// 卸载模型
Android.unloadModel()

// 流式推理（结果通过 window.onMNNToken 回调）
Android.chat(callbackId, messagesJson)

// 代理 POST 请求（结果通过 window.onProxyResponse 回调）
Android.proxyPost(callbackId, url, headersJson, bodyJson)
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 在线 API 调用 |
| `FOREGROUND_SERVICE` | 后台下载模型时显示通知 |
| `WAKE_LOCK` | 推理期间保持 CPU 活跃 |
| `WRITE_EXTERNAL_STORAGE` | Android 9 及以下存储模型文件 |
