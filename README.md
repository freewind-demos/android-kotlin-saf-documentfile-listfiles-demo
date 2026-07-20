# android-kotlin-saf-documentfile-listfiles-demo

## 简介

演示 SAF 下四步耗时：`listFiles` → fetch uris → fetch names → fetch sizes。每大步前先 append「开始…」；完成后 append 前 5 条；块之间空行。

`listFiles()` 子项（TreeDocumentFile）字段来源：

- 纯内存：`uri` / `getUri()`
- 会查 ContentResolver：`name`、`type`、`isDirectory`、`isFile`、`lastModified`、`length`、`exists`、`canRead`、`canWrite`

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

- `listFiles()` 子项内存里几乎只有 Uri；`name` / `length()` 等会再查 ContentResolver。
- 每步输出：`操作: N items, X ms` + `sample (first 5)`。
- Logcat 过滤 tag：`SafListFiles`。

## 教程

1. **背景**：对比「列目录」与「再读 uri/name/size」的成本。
2. **原理**：四段 `SystemClock.elapsedRealtime()`；结果进 `ArrayList`；每步后 append 前 5。
3. **关键代码**：见 `MainActivity.runTimedScan`。
