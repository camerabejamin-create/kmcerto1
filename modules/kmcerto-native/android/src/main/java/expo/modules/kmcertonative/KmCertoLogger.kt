package expo.modules.kmcertonative

import android.content.Context

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KmCertoLogger {
    private var logFile: File? = null
    private val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss.SSS", Locale.getDefault())
    private const val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB
    private const val MAX_LOG_FILES = 100

    fun init(context: Context) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val logDir = File(dir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        // Limpar logs antigos se houver muitos
        val existingLogs = logDir.listFiles { _, name -> name.startsWith("kmcerto_debug_") && name.endsWith(".txt") }
        existingLogs?.sortByDescending { it.lastModified() }
        existingLogs?.drop(MAX_LOG_FILES - 1)?.forEach { it.delete() }

        // Usar o log mais recente ou criar um novo
        logFile = existingLogs?.firstOrNull() ?: File(logDir, "kmcerto_debug_${sdf.format(Date())}.txt")

        // Se o arquivo atual for muito grande, criar um novo
        if (logFile?.exists() == true && logFile!!.length() > MAX_LOG_SIZE_BYTES) {
            logFile = File(logDir, "kmcerto_debug_${sdf.format(Date())}.txt")
        }
    }

    fun log(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val line = "[$time] $message\n"
        Log.d("KmCerto", message)
        try {
            logFile?.appendText(line)
        } catch (e: Throwable) {
            Log.e("KmCertoLogger", "Erro ao escrever no arquivo de log: ${e.message}")
        }
    }

    fun getLogPath(): String = logFile?.absolutePath ?: "N/A"
}
