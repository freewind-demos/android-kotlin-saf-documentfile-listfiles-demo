package com.freewind.saf.listfiles

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SAF DocumentFile 耗时对比 Demo。
 *
 * 选目录后扫描：分别计时 listFiles / fetch all names / fetch all sizes。
 * 不打印 name/size 细节，只 append 动作名与耗时。
 */
class MainActivity : ComponentActivity() {

    // Logcat 过滤用
    private val tag = "SafListFiles"

    // 用户选中的目录树 Uri；未选为 null
    private var treeUri by mutableStateOf<Uri?>(null)

    // 耗时日志（只 append 动作行，无细节）
    private var outputText by mutableStateOf("先点「选择目录」，再点「扫描」看耗时。")

    // 扫描中禁用按钮，避免重复点
    private var scanning by mutableStateOf(false)

    // 后台扫描用的 Job，便于 Activity 销毁时取消
    private var scanJob: Job? = null

    // 系统目录选择器：ACTION_OPEN_DOCUMENT_TREE
    private val openTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                // 用户取消：明确提示，不假装成功
                outputText = "未选择目录（用户取消）。"
                Log.i(tag, "OpenDocumentTree cancelled")
                return@registerForActivityResult
            }
            // 持久授权：重启后仍可用该 Uri
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
            treeUri = uri
            outputText = "已选目录（不打印 Uri 细节）。\n点「扫描」开始计时。"
            Log.i(tag, "treeUri selected")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "android-kotlin-saf-documentfile-listfiles-demo",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "对比耗时：listFiles → fetch all names → fetch all sizes。" +
                                "不打印细节，只看每步 ms。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { openTree.launch(null) },
                            enabled = !scanning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("选择目录")
                        }
                        Button(
                            onClick = { startScan() },
                            // 没选目录或正在扫 → 不可点
                            enabled = treeUri != null && !scanning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (scanning) "扫描中…" else "扫描")
                        }
                        Text(
                            text = outputText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Activity 销毁时取消未完成的扫描，避免泄漏回调
        scanJob?.cancel()
        super.onDestroy()
    }

    /** 主线程启动；重活在 IO；每完成一步就 append 一行耗时。 */
    private fun startScan() {
        val uri = treeUri
            ?: error("未选择目录却触发了扫描")
        scanning = true
        outputText = "扫描开始…"
        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                runTimedScan(uri)
                appendLine("done")
            } catch (e: Exception) {
                // 失败必须可见
                val msg = "扫描失败: ${e.message}"
                appendLine(msg)
                Log.e(tag, msg, e)
            } finally {
                scanning = false
            }
        }
    }

    /**
     * 三步计时：
     * 1) listFiles
     * 2) 遍历取 name（每项会查 ContentResolver）
     * 3) 遍历取 length/size（每项再查）
     *
     * 细节不输出；用 sink 防止编译器把读取优化掉。
     */
    private suspend fun runTimedScan(treeUri: Uri) {
        val root = withContext(Dispatchers.IO) {
            DocumentFile.fromTreeUri(this@MainActivity, treeUri)
                ?: error("DocumentFile.fromTreeUri 返回 null")
        }

        // —— 1) listFiles ——
        val listStarted = SystemClock.elapsedRealtime()
        val children = withContext(Dispatchers.IO) {
            root.listFiles()
        }
        val listMs = SystemClock.elapsedRealtime() - listStarted
        appendLine("listFiles: ${children.size} items, ${listMs} ms")

        // —— 2) fetch all names ——
        val namesStarted = SystemClock.elapsedRealtime()
        val nameSink = withContext(Dispatchers.IO) {
            // sink：累加，避免 JIT 认为读取无副作用而消除
            var sink = 0
            for (child in children) {
                sink += child.name?.length ?: 0
            }
            sink
        }
        val namesMs = SystemClock.elapsedRealtime() - namesStarted
        appendLine("fetch all names: ${namesMs} ms (sink=$nameSink)")

        // —— 3) fetch all sizes ——
        val sizesStarted = SystemClock.elapsedRealtime()
        val sizeSink = withContext(Dispatchers.IO) {
            var sink = 0L
            for (child in children) {
                sink += child.length()
            }
            sink
        }
        val sizesMs = SystemClock.elapsedRealtime() - sizesStarted
        appendLine("fetch all sizes: ${sizesMs} ms (sink=$sizeSink)")
    }

    /** 往界面与 Logcat append 一行（已在主协程上下文时可直接调）。 */
    private fun appendLine(line: String) {
        outputText = outputText + "\n" + line
        Log.i(tag, line)
    }
}
