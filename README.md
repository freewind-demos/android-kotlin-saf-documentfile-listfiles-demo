# android-kotlin-saf-documentfile-listfiles-demo

## 简介

演示 SAF 下 `DocumentFile.listFiles()`、再逐项取 `name`、再逐项取 `length` 的耗时差异。界面只 append 动作名与 ms，不打印文件细节。

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

- `listFiles()` 只拿到子项 Uri；随后 `name` / `length()` 会对 ContentResolver 再查，通常更慢。
- 输出示例：`listFiles: N items, X ms` → `fetch all names: Y ms` → `fetch all sizes: Z ms`。
- Logcat 过滤 tag：`SafListFiles`。

## 教程

1. **背景**：想对比「只列目录」与「再读元数据」的成本。
2. **原理**：选目录 → 扫描 → 三段 `SystemClock.elapsedRealtime()` 计时；用 sink 累加防止读取被优化掉。
3. **关键代码**：见 `MainActivity.runTimedScan`。
