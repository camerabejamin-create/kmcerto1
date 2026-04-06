package expo.modules.kmcertonative

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class KmCertoNativeModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("KmCertoNative")
    Events("KmCertoOverlayData", "KmCertoPermissionStatus")

    AsyncFunction("isOverlayPermissionGranted") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      Settings.canDrawOverlays(context)
    }

    AsyncFunction("isAccessibilityServiceEnabled") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      KmCertoAccessibilityService.isEnabled(context)
    }

    AsyncFunction("openOverlaySettings") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      try {
        val intent = Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}"),
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
      } catch (_: Throwable) {
        false
      }
    }

    AsyncFunction("openAccessibilitySettings") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
      } catch (_: Throwable) {
        false
      }
    }

    AsyncFunction("isBatteryOptimizationIgnored") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
      } else {
        true
      }
    }

    AsyncFunction("openBatteryOptimizationSettings") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      try {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        } else {
          Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          }
        }
        context.startActivity(intent)
        true
      } catch (_: Throwable) {
        false
      }
    }

    AsyncFunction("isMonitoringActive") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.isMonitoringEnabled(context)
    }

    AsyncFunction("hasScreenCapturePermission") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      KmCertoScreenCapture.hasPermission(context)
    }

    AsyncFunction("requestScreenCapturePermission") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      try {
        KmCertoScreenCapture.requestPermission(context)
        true
      } catch (_: Throwable) { false }
    }

    AsyncFunction("startMonitoring") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.setMonitoringEnabled(context, true)
      true
    }

    AsyncFunction("stopMonitoring") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.setMonitoringEnabled(context, false)
      KmCertoOverlayService.stop(context)
      true
    }

    AsyncFunction("hideOverlay") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      KmCertoOverlayService.stop(context)
      true
    }

    AsyncFunction("setMinimumPerKm") { value: Double ->
      val context = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.setMinimumPerKm(context, value)
      true
    }

    AsyncFunction("getMinimumPerKm") {
      val context = appContext.reactContext ?: return@AsyncFunction KmCertoRuntime.DEFAULT_MINIMUM_PER_KM
      KmCertoRuntime.getMinimumPerKm(context)
    }

    AsyncFunction("getLogPath") {
      KmCertoLogger.getLogPath()
    }

    AsyncFunction("clearLog") {
      val context = appContext.reactContext ?: return@AsyncFunction false
      KmCertoLogger.init(context)
      true
    }

    AsyncFunction("showTestOverlay") { payload: String? ->
      val context = appContext.reactContext ?: return@AsyncFunction false
      val parsed = KmCertoOfferParser.fromJsonPayload(
        payload = payload,
        minimumPerKm = KmCertoRuntime.getMinimumPerKm(context),
      ) ?: return@AsyncFunction false

      this@KmCertoNativeModule.sendEvent("KmCertoOverlayData", mapOf(
        "totalFare" to parsed.totalFare,
        "totalFareLabel" to parsed.totalFareLabel,
        "status" to parsed.status,
        "statusColor" to parsed.statusColor,
        "perKm" to parsed.perKm,
        "perHour" to (parsed.perHour ?: 0.0),
        "perMinute" to (parsed.perMinute ?: 0.0),
        "minimumPerKm" to parsed.minimumPerKm,
        "sourceApp" to parsed.sourceApp,
        "rawText" to parsed.rawText
      ))
      KmCertoOverlayService.show(context, parsed)
      true
    }
  }
}

object KmCertoRuntime {
  const val DEFAULT_MINIMUM_PER_KM = 1.5
  private const val PREFERENCES_NAME = "kmcerto_native_preferences"
  private const val KEY_MINIMUM_PER_KM = "minimum_per_km"
  private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
  private const val KEY_SCREEN_CAPTURE_GRANTED = "screen_capture_granted"

  val supportedPackages: Map<String, String> = mapOf(
    "br.com.ifood.driver.app" to "iFood",
    "com.app99.driver" to "99Food",
    "com.ubercab.driver" to "Uber",
    "com.app99.driver.motorista" to "99",
    "com.app99.driver.motorista.partner" to "99"
  )

  fun setMinimumPerKm(context: Context, value: Double) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .putFloat(KEY_MINIMUM_PER_KM, value.toFloat())
      .apply()
  }

  fun getMinimumPerKm(context: Context): Double {
    val stored = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getFloat(KEY_MINIMUM_PER_KM, DEFAULT_MINIMUM_PER_KM.toFloat())
    return stored.toDouble()
  }

  fun setMonitoringEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_MONITORING_ENABLED, enabled)
      .apply()
  }

  fun isMonitoringEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_MONITORING_ENABLED, true)
  }

  fun setScreenCaptureGranted(context: Context, granted: Boolean) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_SCREEN_CAPTURE_GRANTED, granted)
      .apply()
  }

  fun isScreenCaptureGranted(context: Context): Boolean {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_SCREEN_CAPTURE_GRANTED, false)
  }

  fun supportsPackage(packageName: String): Boolean {
    return supportedPackages.keys.any { key -> packageName == key || packageName.startsWith("$key:") || packageName.contains(key) }
  }

  fun sourceLabel(packageName: String): String {
    return supportedPackages.entries.firstOrNull { packageName == it.key || packageName.startsWith("${it.key}:") || packageName.contains(it.key) }
      ?.value
      ?: packageName.substringAfterLast('.')
  }
}

data class OfferDecisionData(
  val totalFare: Double,
  val totalFareLabel: String,
  val status: String,
  val statusColor: String,
  val perKm: Double,
  val perHour: Double?,
  val perMinute: Double?,
  val minimumPerKm: Double,
  val sourceApp: String,
  val rawText: String,
  val distanceKm: Double? = null,
) {
  fun toJson(): String {
    return JSONObject().apply {
      put("totalFare", totalFare)
      put("totalFareLabel", totalFareLabel)
      put("status", status)
      put("statusColor", statusColor)
      put("perKm", perKm)
      put("perHour", perHour)
      put("perMinute", perMinute)
      put("minimumPerKm", minimumPerKm)
      put("sourceApp", sourceApp)
      put("rawText", rawText)
      if (distanceKm != null) put("distanceKm", distanceKm)
    }.toString()
  }

  companion object {
    fun fromJson(json: String?): OfferDecisionData? {
      if (json.isNullOrBlank()) return null
      return try {
        val payload = JSONObject(json)
        OfferDecisionData(
          totalFare = payload.optDouble("totalFare", Double.NaN),
          totalFareLabel = payload.optString("totalFareLabel", ""),
          status = payload.optString("status", ""),
          statusColor = payload.optString("statusColor", "#FFFFFF"),
          perKm = payload.optDouble("perKm", 0.0),
          perHour = if (payload.has("perHour")) payload.getDouble("perHour") else null,
          perMinute = if (payload.has("perMinute")) payload.getDouble("perMinute") else null,
          minimumPerKm = payload.optDouble("minimumPerKm", 1.5),
          sourceApp = payload.optString("sourceApp", "Desconhecido"),
          rawText = payload.optString("rawText", ""),
          distanceKm = if (payload.has("distanceKm")) payload.getDouble("distanceKm") else null
        )
      } catch (_: Throwable) { null }
    }
  }
}

object KmCertoOfferParser {
  fun fromJsonPayload(payload: String?, minimumPerKm: Double): OfferDecisionData? {
    if (payload.isNullOrBlank()) return null
    return try {
      val json = JSONObject(payload)
      val totalFare = json.optDouble("totalFare", 0.0)
      val distanceKm = json.optDouble("distanceKm", 0.0)
      val perKm = if (distanceKm > 0) totalFare / distanceKm else 0.0
      val isGood = perKm >= minimumPerKm

      OfferDecisionData(
        totalFare = totalFare,
        totalFareLabel = "R$ %.2f".format(totalFare),
        status = if (isGood) "ACEITAR" else "RECUSAR",
        statusColor = if (isGood) "#4CAF50" else "#F44336",
        perKm = perKm,
        perHour = null,
        perMinute = null,
        minimumPerKm = minimumPerKm,
        sourceApp = json.optString("sourceApp", "Teste"),
        rawText = payload,
        distanceKm = distanceKm
      )
    } catch (_: Throwable) { null }
  }

  fun parseFromText(text: String, minimumPerKm: Double, sourceApp: String): OfferDecisionData? {
    val cleanText = text.replace("\n", " ").replace(",", ".")
    
    val fareRegex = Regex("""R\$\s?(\d+[\d.]*)""")
    val distanceRegex = Regex("""(\d+[\d.]*)\s?km""", RegexOption.IGNORE_CASE)
    
    val fareMatch = fareRegex.find(cleanText)
    val distanceMatch = distanceRegex.find(cleanText)
    
    if (fareMatch != null && distanceMatch != null) {
      val totalFare = fareMatch.groupValues[1].toDoubleOrNull() ?: return null
      val distanceKm = distanceMatch.groupValues[1].toDoubleOrNull() ?: return null
      
      if (distanceKm <= 0) return null
      
      val perKm = totalFare / distanceKm
      val isGood = perKm >= minimumPerKm
      
      return OfferDecisionData(
        totalFare = totalFare,
        totalFareLabel = "R$ %.2f".format(totalFare),
        status = if (isGood) "ACEITAR" else "RECUSAR",
        statusColor = if (isGood) "#4CAF50" else "#F44336",
        perKm = perKm,
        perHour = null,
        perMinute = null,
        minimumPerKm = minimumPerKm,
        sourceApp = sourceApp,
        rawText = text,
        distanceKm = distanceKm
      )
    }
    return null
  }
}

class KmCertoAccessibilityService : AccessibilityService() {
  private var wakeLock: PowerManager.WakeLock? = null
  private var lastProcessTime = 0L
  private var lastOcrTime = 0L
  private var lastOcrStatusLog = 0L

  companion object {
    fun isEnabled(context: Context): Boolean {
      val expected = "${context.packageName}/${KmCertoAccessibilityService::class.java.canonicalName}"
      val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
      return enabledServices?.contains(expected) == true
    }
  }

  override fun onServiceConnected() {
    val info = AccessibilityServiceInfo().apply {
      eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
      feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
      flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
      notificationTimeout = 100
    }
    this.serviceInfo = info
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KmCerto:AccessibilityWakeLock")
    wakeLock?.acquire(10 * 60 * 1000L)
    KmCertoLogger.init(this)
    KmCertoLogger.log("SERVIÇO ACESSIBILIDADE: Conectado")
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (!KmCertoRuntime.isMonitoringEnabled(this)) return
    val packageName = event.packageName?.toString() ?: return
    if (!KmCertoRuntime.supportsPackage(packageName)) return

    val now = System.currentTimeMillis()
    if (now - lastProcessTime < 1000) return
    lastProcessTime = now

    val rootNode = rootInActiveWindow ?: return
    val sb = StringBuilder()
    collectTextRecursive(rootNode, sb)
    val text = sb.toString()

    if (text.isNotBlank()) {
      processText(text, packageName)
    } else {
      // Se a acessibilidade falhar em ler o texto, tenta o OCR com cooldown
      if (now - lastOcrTime > 10000) {
        lastOcrTime = now
        if (KmCertoScreenCapture.isProjectionAlive()) {
            KmCertoLogger.log("OCR_TENTATIVA pkg=$packageName - janela vazia, iniciando captura de tela")
            KmCertoScreenCapture.captureAndProcess(this, packageName)
        } else {
            if (now - lastOcrStatusLog > 30000) {
                KmCertoLogger.log("OCR_SEM_PERMISSAO - peça permissão de gravação de tela no app")
                lastOcrStatusLog = now
            }
        }
      }
    }
  }

  private fun collectTextRecursive(node: AccessibilityNodeInfo?, out: StringBuilder) {
    if (node == null) return
    val text = node.text?.toString()
    val contentDesc = node.contentDescription?.toString()
    if (!text.isNullOrBlank()) out.append(text).append(" ")
    if (!contentDesc.isNullOrBlank()) out.append(contentDesc).append(" ")
    for (i in 0 until node.childCount) {
      val child = node.getChild(i)
      if (child != null) {
        collectTextRecursive(child, out)
        child.recycle()
      }
    }
  }

  private fun processText(text: String, packageName: String) {
    val minimumPerKm = KmCertoRuntime.getMinimumPerKm(this)
    val sourceApp = KmCertoRuntime.sourceLabel(packageName)
    val offer = KmCertoOfferParser.parseFromText(text, minimumPerKm, sourceApp)
    if (offer != null) {
      KmCertoOverlayService.show(this, offer)
    }
  }

  override fun onInterrupt() {}

  override fun onDestroy() {
    super.onDestroy()
    wakeLock?.let { if (it.isHeld) it.release() }
  }
}

class KmCertoScreenCaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "kmcerto_capture"
        private const val NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "KmCerto Captura de Tela", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
            .setContentTitle("KmCerto Captura Ativa")
            .setContentText("Processando OCR em tempo real")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            try {
                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = mpManager.getMediaProjection(resultCode, resultData)
                if (projection != null) {
                    KmCertoScreenCapture.onProjectionReady(projection, this)
                    KmCertoRuntime.setScreenCaptureGranted(this, true)
                    KmCertoLogger.init(this)
                    KmCertoLogger.log("CAPTURA DE TELA: Token obtido com sucesso")
                } else {
                    KmCertoLogger.log("CAPTURA DE TELA: getMediaProjection retornou null")
                    stopSelf()
                }
            } catch (e: Exception) {
                KmCertoLogger.log("CAPTURA DE TELA ERRO: ${e.message}")
                stopSelf()
            }
        } else if (intent == null || !intent.hasExtra(EXTRA_RESULT_CODE)) {
            // Reiniciado pelo sistema
        } else {
            KmCertoLogger.log("CAPTURA DE TELA: Permissão negada")
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        KmCertoScreenCapture.releaseProjection()
    }
}

object KmCertoScreenCapture {
    @Volatile
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // =====================================================================
    // CAPTURA CONTÍNUA (ANDROID 14+): Agora mantemos um fluxo de imagens
    // ativo para evitar que o Android invalide o token por uso múltiplo.
    // =====================================================================
    private val lastBitmap = AtomicReference<Bitmap?>(null)

    fun isProjectionAlive(): Boolean = mediaProjection != null

    fun hasPermission(context: Context): Boolean {
        return mediaProjection != null || KmCertoRuntime.isScreenCaptureGranted(context)
    }

    fun requestPermission(context: Context) {
        val intent = Intent(context, KmCertoPermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun onProjectionReady(projection: MediaProjection, context: Context) {
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                KmCertoLogger.log("CAPTURA DE TELA: Token expirado")
                releaseProjection()
                KmCertoRuntime.setScreenCaptureGranted(context, false)
            }
        }, Handler(Looper.getMainLooper()))
        
        // Inicia o fluxo contínuo de captura
        startContinuousCapture(context)
    }

    private fun startContinuousCapture(context: Context) {
        val projection = mediaProjection ?: return
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            // Criamos o VirtualDisplay APENAS UMA VEZ (Regra do Android 14)
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "KmCertoCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width
                        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        
                        // Guardamos o último frame para o OCR usar quando precisar
                        val old = lastBitmap.getAndSet(bitmap)
                        old?.recycle()
                        
                        image.close()
                    }
                } catch (_: Exception) {}
            }, Handler(Looper.getMainLooper()))
            
            KmCertoLogger.log("CAPTURA DE TELA: Fluxo contínuo iniciado")
        } catch (e: Exception) {
            KmCertoLogger.log("CAPTURA DE TELA ERRO FLUXO: ${e.message}")
        }
    }

    fun releaseProjection() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            val old = lastBitmap.getAndSet(null)
            old?.recycle()
        } catch (_: Throwable) {}
    }

    fun captureAndProcess(context: Context, packageName: String) {
        // No Android 14+, apenas pegamos o último frame do fluxo contínuo
        val bitmap = lastBitmap.get()
        if (bitmap != null) {
            processBitmap(bitmap, context, packageName)
        } else {
            KmCertoLogger.log("OCR_ERRO: Nenhum frame disponível no fluxo")
        }
    }

    private fun processBitmap(bitmap: Bitmap, context: Context, packageName: String) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                if (text.isNotBlank()) {
                    val minimumPerKm = KmCertoRuntime.getMinimumPerKm(context)
                    val sourceApp = KmCertoRuntime.sourceLabel(packageName)
                    val offer = KmCertoOfferParser.parseFromText(text, minimumPerKm, sourceApp)
                    if (offer != null) KmCertoOverlayService.show(context, offer)
                }
            }
            .addOnFailureListener { e -> KmCertoLogger.log("OCR_FALHA: ${e.message}") }
    }
}

class KmCertoPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpManager.createScreenCaptureIntent(), 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, KmCertoScreenCaptureService::class.java).apply {
                putExtra(KmCertoScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(KmCertoScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        }
        finish()
    }
}

object KmCertoLogger {
  private var logFile: File? = null
  private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
  fun init(context: Context) {
    val dir = context.getExternalFilesDir(null) ?: context.filesDir
    logFile = File(dir, "kmcerto_debug.txt")
    if (logFile?.exists() == true && logFile!!.length() > 1024 * 1024) logFile?.delete()
  }
  fun log(message: String) {
    val time = sdf.format(Date())
    val line = "[$time] $message\n"
    Log.d("KmCerto", message)
    try { logFile?.appendText(line) } catch (_: Throwable) {}
  }
  fun getLogPath(): String = logFile?.absolutePath ?: "N/A"
}

class KmCertoOverlayService : Service() {
    companion object {
        private var overlayView: LinearLayout? = null
        fun show(context: Context, data: OfferDecisionData) {
            Handler(Looper.getMainLooper()).post {
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    stop(context)
                    val view = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(40, 30, 40, 30)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1D2026"))
                            cornerRadius = 40f
                            setStroke(4, Color.parseColor(data.statusColor))
                        }
                        gravity = Gravity.CENTER_HORIZONTAL
                    }

                    val title = TextView(context).apply {
                        text = data.sourceApp
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        alpha = 0.7f
                    }
                    view.addView(title)

                    val fare = TextView(context).apply {
                        text = data.totalFareLabel
                        setTextColor(Color.WHITE)
                        textSize = 32f
                        setTypeface(null, Typeface.BOLD)
                    }
                    view.addView(fare)

                    val perKm = TextView(context).apply {
                        text = "R$ %.2f/km".format(data.perKm)
                        setTextColor(Color.parseColor(data.statusColor))
                        textSize = 18f
                        setTypeface(null, Typeface.BOLD)
                    }
                    view.addView(perKm)

                    val status = TextView(context).apply {
                        text = data.status
                        setTextColor(Color.WHITE)
                        textSize = 20f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(0, 20, 0, 0)
                    }
                    view.addView(status)

                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        y = 100
                    }

                    wm.addView(view, params)
                    overlayView = view

                    Handler(Looper.getMainLooper()).postDelayed({ stop(context) }, 8000)
                } catch (_: Throwable) {}
            }
        }

        fun stop(context: Context) {
            Handler(Looper.getMainLooper()).post {
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    overlayView?.let { wm.removeView(it) }
                    overlayView = null
                } catch (_: Throwable) {}
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
