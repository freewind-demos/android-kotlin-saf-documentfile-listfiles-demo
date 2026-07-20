# android-kotlin-saf-documentfile-listfiles-demo

## 简介

演示 SAF 选目录后，用 `DocumentFile.listFiles()` 列出一层子项，并只打印**不额外访问文件系统**就能拿到的信息：数组长度 + 每项 `Uri`。

## 快速开始

### 环境要求

- JDK 17+
- Android SDK（compileSdk 35）
- 真机或模拟器

### 运行

```bash
# 生成 Gradle Wrapper（首次）
./android-gradle-wrapper.mts

# 只编译
./android-build.mts

# 编译并安装启动
./android-adb.mts
```

## 注意事项

- `DocumentFile.listFiles()` 内部只把子文档的 `Uri` 装进数组；`getName()` / `isDirectory()` / `length()` / `lastModified()` / `getType()` 等每次都会再查 `ContentResolver`，本 Demo 第一步不做这些。
- 需用户通过系统 UI 授权目录（`OpenDocumentTree`）。
- Logcat 过滤 tag：`SafListFiles`。

## 教程

1. **背景**：Android 用 SAF 访问用户授权的目录树，常用 `DocumentFile` 包一层 `content://` Uri。
2. **原理**：点「选择目录」拿 tree Uri → 点「扫描」→ `DocumentFile.fromTreeUri` → `listFiles()` → 打印 `children.size` 与每个 `child.uri`。
3. **关键代码**：见 `MainActivity.listFilesUrisOnly`；UI 两个按钮分别触发选目录与扫描。
