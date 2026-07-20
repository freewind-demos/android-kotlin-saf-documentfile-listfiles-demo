# android-kotlin-saf-documentfile-listfiles-demo

## 简介

演示 SAF 下四步耗时：`listFiles` → fetch uris → fetch names → fetch sizes。每步结果放进数组；界面只报操作名、条数、ms，不打印细节。

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

- `listFiles()` 只装子项；`uri` 读内存字段通常很快；`name` / `length()` 会再查 ContentResolver。
- 输出示例：`listFiles: N items, X ms` → `fetch all uris: N items, Y ms` → `fetch all names: …` → `fetch all sizes: …`。
- Logcat 过滤 tag：`SafListFiles`。

## 教程

1. **背景**：对比「列目录」与「再读 uri/name/size」的成本。
2. **原理**：四段 `SystemClock.elapsedRealtime()`；结果进 `ArrayList`，打印只用 `.size`。
3. **关键代码**：见 `MainActivity.runTimedScan`。
