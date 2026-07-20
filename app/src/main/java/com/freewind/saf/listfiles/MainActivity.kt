package com.freewind.saf.listfiles

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
 * SAF + DocumentFile.listFiles() 最小 Demo。
 *
 * 关键点：listFiles() 返回的每个 DocumentFile 在内存里只带了 Uri；
 * getName / isDirectory / length 等都会再查 ContentResolver，属于额外访问。
 * 本 Demo 只打印「不额外访问」就能拿到的信息：数组长度 + 每项 Uri。
 */
class MainActivity : ComponentActivity() {

    // Logcat 过滤用
    private val tag = "SafListFiles"

    // 用户选中的目录树 Uri；未选为 null
    private var treeUri by mutableStateOf<Uri?>(null)

    // 扫描结果文案，展示在界面上
    private var outputText by mutableStateOf("先点「选择目录」，再点「扫描」。")

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
            outputText = "已选目录：\n$uri\n\n点「扫描」调用 listFiles()。"
            Log.i(tag, "treeUri=$uri")
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
                            text = "listFiles() 后只打印不额外访问 FS 的信息：数组长度 + 每项 Uri。" +
                                "（name/type/length/isDirectory 等会再查 provider，本步不做）",
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

    /** 在 IO 线程调用 DocumentFile.listFiles()，再回到主线程刷新 UI。 */
    private fun startScan() {
        val uri = treeUri
            ?: error("未选择目录却触发了扫描")
        scanning = true
        outputText = "扫描中…"
        scanJob?.cancel()
        scanJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            try {
                val report = withContext(Dispatchers.IO) {
                    listFilesUrisOnly(uri)
                }
                outputText = report
                Log.i(tag, report)
            } catch (e: Exception) {
                // 失败必须可见：写 UI + Log，不吞异常语义（错误态展示）
                val msg = "扫描失败: ${e.message}"
                outputText = msg
                Log.e(tag, msg, e)
            } finally {
                scanning = false
            }
        }
    }

    /**
     * 对选中目录做一层 listFiles()。
     *
     * DocumentFile.listFiles() 实现里只收集子项 Uri，再包装成 DocumentFile[]。
     * 因此数组上「不额外访问文件系统」能拿到的只有：
     * 1) 数组长度
     * 2) 每个元素的 getUri()
     *
     * 若再调 getName() / isDirectory() / length() 等，都会对 ContentResolver 再查询。
     */
    private fun listFilesUrisOnly(treeUri: Uri): String {
        val root = DocumentFile.fromTreeUri(this, treeUri)
            ?: error("DocumentFile.fromTreeUri 返回 null，uri=$treeUri")
        // listFiles 本身是一次 provider 列举；阻塞，须在 Dispatchers.IO
        val children = root.listFiles()
        val lines = ArrayList<String>(children.size + 4)
        lines += "listFiles() 返回数组长度: ${children.size}"
        lines += "（每项仅打印 Uri；未调 name/type/length/isDirectory）"
        lines += ""
        children.forEachIndexed { index, child ->
            // getUri() 读内存字段，不再访问 FS
            val childUri = child.uri
            lines += "[$index] $childUri"
        }
        return lines.joinToString("\n")
    }
}
