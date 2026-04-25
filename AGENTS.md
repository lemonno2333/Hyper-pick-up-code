# AGENTS.md

## 项目简介

**澎湃记（Hyper Note）** — Android 应用，使用本地 OCR（PaddleOCR ncnn）和 ML Kit 条码扫描，从截图中自动识别餐饮外卖取餐码和快递单号。

## 构建命令

```bash
./gradlew assembleDebug       # 调试版构建
./gradlew assembleRelease     # 发布版构建（混淆 + ProGuard）
./gradlew installDebug        # 安装调试版 APK 到已连接设备
./gradlew test                # 单元测试（JUnit 4）
./gradlew connectedAndroidTest # 仪器化测试
./gradlew clean               # 清理构建
```

签名配置（`local.properties` 或环境变量）：`KEY_STORE_PATH`、`STORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`

## 架构

**MVVM 架构，单模块**，包名 `com.Badnng.moe`

```
Compose UI → StateFlow ← OrderViewModel → Repository → DAO → Room DB
```

- **Room** v5，含迁移路径 2→3→4→5：`OrderEntity`（orders 表）、`OrderGroup`（order_groups 表）
- **PaddleOcrHelper**：单例（双重检查锁定），中文 OCR
- **TextRecognitionHelper**：`recognizeMultipleCodes()` 返回多个取餐码
- **前台服务**：`ScreenCaptureService`（MediaProjection）、`ShareRecognitionService`（分享图片）、`ProcessTextRecognitionService`（文本选择）
- **Shizuku**：`ShizukuScreenshotHelper` 特权截图，`RootHelper` 备用

## 关键约定

- **100% Compose UI** — 无 XML 布局
- **Kotlin 2.0.21**，Java 11 兼容
- **minSdk 35**（Android 15+），compileSdk/targetSdk 36
- **NDK**：仅 arm64-v8a
- **版本目录**：`gradle/libs.versions.toml`
- **KSP** 用于 Room 注解处理
- **Compose 状态**：本地 `var ... by mutableStateOf()`，共享 `StateFlow`
- **Compose 函数命名**：PascalCase
- **按钮圆角**：15dp
- **交互反馈**：需带震动模块

## ProGuard

Keep 规则（运行时关键）：`com.google.mlkit.**`、`com.equationl.ncnnandroidppocr.**`、`org.ncnn.**`

## CI

GitHub Actions（`.github/workflows/Build and Release.yml`）：推送 tag（`v*`）触发构建，使用 JDK 11，未配置签名不运行测试。

## 常见问题

- **数据库迁移**：实体字段类型须匹配——`OrderEntity.groupId` 为 `Long?`，`OrderGroup.id` 为 `Long`
- **通知权限**：Android 14+ 需 `POST_PROMOTED_NOTIFICATIONS`
- **Shizuku**：需用户已安装并授权
- **布局问题**：底部导航栏被遮挡时，检查外层 `Column` 是否有 `windowInsetsPadding`，改由 `LazyColumn.contentPadding` 处理