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
import android.util.TypedValue
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

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
    return supportedPackages.keys.any { key -> packageName == key || packageName.startsWith("$key:") }
  }

  fun sourceLabel(packageName: String): String {
    return supportedPackages.entries.firstOrNull { packageName == it.key || packageName.startsWith("${it.key}:") }
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
          status = payload.optString("status", "RECUSAR"),
          statusColor = payload.optString("statusColor", "#DC2626"),
          perKm = payload.optDouble("perKm", Double.NaN),
          perHour = if (payload.has("perHour") && !payload.isNull("perHour")) payload.optDouble("perHour") else null,
          perMinute = if (payload.has("perMinute") && !payload.isNull("perMinute")) payload.optDouble("perMinute") else null,
          minimumPerKm = payload.optDouble("minimumPerKm", 1.5),
          sourceApp = payload.optString("sourceApp", "Desconhecido"),
          rawText = payload.optString("rawText", ""),
          distanceKm = if (payload.has("distanceKm")) payload.optDouble("distanceKm") else null
        )
      } catch (_: Throwable) {
        null
      }
    }
  }
}

object KmCertoOfferParser {
  fun fromJsonPayload(payload: String?, minimumPerKm: Double): OfferDecisionData? {
    if (payload.isNullOrBlank()) return null
    return try {
      val json = JSONObject(payload)
      val fare = json.optDouble("totalFare", 0.0)
      val distance = json.optDouble("totalDistance", 0.0)
      val minutes = json.optDouble("totalMinutes", 0.0)
      val source = json.optString("sourceApp", "Manual")
      val raw = json.optString("rawText", "")

      calculate(fare, distance, minutes, minimumPerKm, source, raw)
    } catch (_: Throwable) {
      null
    }
  }

  fun parseFromText(text: String, minimumPerKm: Double, sourceApp: String): OfferDecisionData? {
    if (text.isBlank()) return null

    val fare = findFare(text) ?: return null
    val distance = findDistance(text) ?: return null
    val minutes = findMinutes(text)

    return calculate(fare, distance, minutes, minimumPerKm, sourceApp, text)
  }

  private fun calculate(
    fare: Double,
    distance: Double,
    minutes: Double?,
    minimumPerKm: Double,
    sourceApp: String,
    rawText: String
  ): OfferDecisionData {
    val perKm = if (distance > 0) fare / distance else 0.0
    val perHour = if (minutes != null && minutes > 0) (fare / minutes) * 60 else null
    val perMinute = if (minutes != null && minutes > 0) fare / minutes else null

    val isAccepted = perKm >= minimumPerKm
    val status = if (isAccepted) "ACEITAR" else "RECUSAR"
    val statusColor = if (isAccepted) "#16A34A" else "#DC2626"

    return OfferDecisionData(
      totalFare = fare,
      totalFareLabel = "R$ ${String.format("%.2f", fare)}",
      status = status,
      statusColor = statusColor,
      perKm = perKm,
      perHour = perHour,
      perMinute = perMinute,
      minimumPerKm = minimumPerKm,
      sourceApp = sourceApp,
      rawText = rawText,
      distanceKm = distance
    )
  }

  private fun findFare(text: String): Double? {
    val regex = Regex("""(?:R\$|RS|S|R)\s*(\d+[\.,]\d{2})""", RegexOption.IGNORE_CASE)
    return regex.find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
  }

  private fun findDistance(text: String): Double? {
    val regex = Regex("""(\d+[\.,]\d+)\s*(?:km|k\s*m)""", RegexOption.IGNORE_CASE)
    return regex.find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
  }

  private fun findMinutes(text: String): Double? {
    val regex = Regex("""(\d+)\s*(?:min|m\s*i\s*n)""", RegexOption.IGNORE_CASE)
    return regex.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
  }
}

class KmCertoAccessibilityService : AccessibilityService() {
  private var wakeLock: PowerManager.WakeLock? = null

  // =====================================================================
  // CONTROLE DE FLOOD: Impede que o OCR seja chamado centenas de vezes
  // por segundo. O Accessibility Service recebe eventos a cada ~10ms,
  // mas o OCR só precisa rodar a cada 10 segundos no máximo.
  // =====================================================================
  private var lastOcrAttemptTime: Long = 0L
  private var ocrNoPermissionLogged: Boolean = false

  companion object {
    // Intervalo mínimo entre tentativas de OCR (10 segundos)
    private const val OCR_COOLDOWN_MS = 10_000L
    // Intervalo mínimo entre processamentos de texto por acessibilidade (1 segundo)
    private const val TEXT_COOLDOWN_MS = 1_000L

    fun isEnabled(context: Context): Boolean {
      val expectedComponentName = android.content.ComponentName(context, KmCertoAccessibilityService::class.java)
      val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
      val colonSplitter = TextUtils.SimpleStringSplitter(':')
      colonSplitter.setString(enabledServices)
      while (colonSplitter.hasNext()) {
        val componentName = colonSplitter.next()
        if (componentName.equals(expectedComponentName.flattenToString(), ignoreCase = true)) return true
      }
      return false
    }
  }

  private var lastTextProcessTime: Long = 0L

  override fun onServiceConnected() {
    super.onServiceConnected()
    val info = AccessibilityServiceInfo().apply {
      eventTypes = AccessibilityEvent.TYPES_ALL_MASK
      feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
      flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
              AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
              AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
      notificationTimeout = 100
    }
    this.serviceInfo = info
    
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KmCerto::WakeLock")
    
    KmCertoLogger.init(this)
    KmCertoLogger.log("Serviço de Acessibilidade Conectado")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val channelId = "kmcerto_monitoring"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "KmCerto Monitoramento", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("KmCerto Ativo")
            .setContentText("Monitorando ofertas em tempo real")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1001, notification)
        }
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (!KmCertoRuntime.isMonitoringEnabled(this)) return

    val packageName = event.packageName?.toString() ?: return
    if (!KmCertoRuntime.supportsPackage(packageName)) return

    val now = System.currentTimeMillis()

    // Cooldown para processamento de texto (1 segundo)
    if (now - lastTextProcessTime < TEXT_COOLDOWN_MS) return
    lastTextProcessTime = now

    wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)

    val allText = StringBuilder()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      windows.forEach { window ->
        collectTextRecursive(window.root, allText)
      }
    }

    if (allText.isEmpty()) {
      collectTextRecursive(rootInActiveWindow, allText)
    }

    if (allText.isEmpty()) {
      event.text.forEach { allText.append(it).append(" ") }
    }

    val text = allText.toString()
    if (text.isNotBlank()) {
      processText(text, packageName)
    }
    
    // =====================================================================
    // OCR via MediaProjection: Só tenta se:
    // 1. O pacote é Uber ou 99 (que podem precisar de OCR)
    // 2. O texto da acessibilidade está vazio (fallback)
    // 3. O cooldown de 10 segundos passou
    // 4. O mediaProjection está realmente disponível em memória
    // =====================================================================
    val needsOcr = packageName.contains("uber") || packageName.contains("app99")
    if (needsOcr && text.isBlank()) {
      if (now - lastOcrAttemptTime >= OCR_COOLDOWN_MS) {
        lastOcrAttemptTime = now
        
        if (KmCertoScreenCapture.isProjectionAlive()) {
          // Token de MediaProjection está vivo, pode capturar
          ocrNoPermissionLogged = false
          KmCertoScreenCapture.captureAndProcess(this, packageName)
        } else {
          // Token não está disponível — logar apenas UMA VEZ para não flood
          if (!ocrNoPermissionLogged) {
            KmCertoLogger.log("OCR_INFO: MediaProjection não disponível. O texto será lido apenas via acessibilidade. Para ativar OCR, conceda a permissão de captura de tela no app.")
            ocrNoPermissionLogged = true
          }
        }
      }
    }
  }

  private fun collectTextRecursive(node: AccessibilityNodeInfo?, out: StringBuilder) {
    if (node == null) return
    
    val text = node.text?.toString()
    val contentDesc = node.contentDescription?.toString()
    val viewId = node.viewIdResourceName
    
    if (!text.isNullOrBlank()) {
      out.append(text).append(" ")
    }
    if (!contentDesc.isNullOrBlank()) {
      out.append(contentDesc).append(" ")
    }

    for (i in 0 until node.childCount) {
      collectTextRecursive(node.getChild(i), out)
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

// =====================================================================
// SERVIÇO DEDICADO PARA CAPTURA DE TELA (mediaProjection)
// 
// REGRA DO ANDROID 14+ (documentação oficial):
// 1. O usuário aceita a permissão (onActivityResult)
// 2. PRIMEIRO: iniciar este Service e chamar startForeground() com MEDIA_PROJECTION
// 3. SÓ DEPOIS: chamar getMediaProjection() para obter o token
//
// Se a ordem for invertida, o Android CANCELA o token silenciosamente.
//
// Este service usa START_STICKY para que o Android o reinicie se for morto.
// Porém, o token de MediaProjection NÃO sobrevive a reinícios — o usuário
// precisará conceder a permissão novamente se o service for destruído.
// =====================================================================
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KmCerto Captura de Tela",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação para captura de tela OCR"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // PASSO 2: Chamar startForeground() ANTES de getMediaProjection()
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

        // PASSO 3: Agora que o foreground service está rodando, obter o MediaProjection token
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
                    KmCertoLogger.log("CAPTURA DE TELA: Permissão concedida e serviço ativo com sucesso")
                } else {
                    KmCertoLogger.init(this)
                    KmCertoLogger.log("CAPTURA DE TELA: getMediaProjection retornou null")
                    KmCertoRuntime.setScreenCaptureGranted(this, false)
                    stopSelf()
                }
            } catch (e: Exception) {
                KmCertoLogger.init(this)
                KmCertoLogger.log("CAPTURA DE TELA ERRO: ${e.message}")
                KmCertoRuntime.setScreenCaptureGranted(this, false)
                stopSelf()
            }
        } else {
            // Se não tem resultCode/data (ex: service reiniciado pelo Android após ser morto),
            // não temos como recriar o token. Apenas manter o service vivo.
            if (intent == null || !intent.hasExtra(EXTRA_RESULT_CODE)) {
                KmCertoLogger.init(this)
                KmCertoLogger.log("CAPTURA DE TELA: Service reiniciado sem token. OCR indisponível até nova permissão.")
                // Marcar como não concedido já que o token morreu
                KmCertoRuntime.setScreenCaptureGranted(this, false)
                stopSelf()
            } else {
                KmCertoLogger.init(this)
                KmCertoLogger.log("CAPTURA DE TELA: Usuário negou a permissão")
                KmCertoRuntime.setScreenCaptureGranted(this, false)
                stopSelf()
            }
        }

        // START_STICKY: O Android reinicia o service se for morto, mas sem o token original
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        KmCertoScreenCapture.releaseProjection()
    }
}

object KmCertoScreenCapture {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    @Volatile
    private var isCapturing = false

    /**
     * Verifica se o token de MediaProjection está realmente vivo em memória.
     * Diferente de hasPermission(), que verifica o SharedPreferences.
     */
    fun isProjectionAlive(): Boolean {
        return mediaProjection != null
    }

    fun hasPermission(context: Context): Boolean {
        // Verificar se o token está vivo OU se foi concedido anteriormente
        return mediaProjection != null || KmCertoRuntime.isScreenCaptureGranted(context)
    }

    fun requestPermission(context: Context) {
        val intent = Intent(context, KmCertoPermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Chamado pelo KmCertoScreenCaptureService DEPOIS que startForeground() já foi executado.
     * Neste ponto o token é válido e pode ser usado.
     */
    fun onProjectionReady(projection: MediaProjection, context: Context) {
        // Liberar projeção anterior se existir
        if (mediaProjection != null) {
            try { mediaProjection?.stop() } catch (_: Throwable) {}
        }
        mediaProjection = projection
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                KmCertoLogger.log("CAPTURA DE TELA: MediaProjection encerrada pelo sistema")
                mediaProjection = null
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader?.close()
                imageReader = null
                isCapturing = false
                // Marcar como não concedido já que o token morreu
                KmCertoRuntime.setScreenCaptureGranted(context, false)
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun releaseProjection() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            isCapturing = false
        } catch (_: Throwable) {}
    }

    fun captureAndProcess(context: Context, packageName: String) {
        // Proteção dupla: verificar se não está capturando E se o token existe
        if (isCapturing) return
        val projection = mediaProjection ?: return
        isCapturing = true

        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "KmCertoCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width
                        
                        val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()
                        
                        processBitmap(bitmap, context, packageName)
                    }
                } catch (e: Exception) {
                    KmCertoLogger.log("CAPTURA ERRO: ${e.message}")
                } finally {
                    virtualDisplay?.release()
                    virtualDisplay = null
                    imageReader?.close()
                    imageReader = null
                    isCapturing = false
                }
            }, 500)
        } catch (e: Exception) {
            KmCertoLogger.log("CAPTURA ERRO INIT: ${e.message}")
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            isCapturing = false
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
                    if (offer != null) {
                        KmCertoOverlayService.show(context, offer)
                    }
                }
            }
            .addOnFailureListener { e ->
                KmCertoLogger.log("OCR_FALHA: ${e.message}")
            }
    }
}

// =====================================================================
// ACTIVITY DE PERMISSÃO
//
// FLUXO CORRETO (Android 14+):
// 1. onCreate: Pede permissão ao usuário via createScreenCaptureIntent()
// 2. onActivityResult: Recebe o resultado
// 3. Se OK: Inicia o KmCertoScreenCaptureService passando resultCode e data
//    O SERVICE é quem vai chamar startForeground() e getMediaProjection()
//    na ORDEM CORRETA exigida pelo Android 14+
// =====================================================================
class KmCertoPermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpManager.createScreenCaptureIntent(), 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            // NÃO chamar getMediaProjection() aqui!
            // Passar o resultCode e data para o Service, que vai:
            // 1. Chamar startForeground() com MEDIA_PROJECTION
            // 2. SÓ DEPOIS chamar getMediaProjection()
            val serviceIntent = Intent(this, KmCertoScreenCaptureService::class.java).apply {
                putExtra(KmCertoScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(KmCertoScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
            // Usuário negou ou cancelou
            KmCertoLogger.init(this)
            KmCertoLogger.log("CAPTURA DE TELA: Usuário cancelou a permissão")
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
    if (logFile?.exists() == true) {
        if (logFile!!.length() > 1024 * 1024) logFile?.delete()
    }
  }

  fun log(message: String) {
    val time = sdf.format(Date())
    val line = "[$time] $message\n"
    Log.d("KmCerto", message)
    try {
      logFile?.appendText(line)
    } catch (_: Throwable) {}
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
                        val shape = GradientDrawable().apply {
                            setColor(Color.parseColor("#1D2026"))
                            cornerRadius = 40f
                            setStroke(4, Color.parseColor("#2D313A"))
                        }
                        background = shape
                    }

                    val header = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    
                    val sourceTxt = TextView(context).apply {
                        text = data.sourceApp
                        setTextColor(Color.parseColor("#9CA3AF"))
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    
                    val statusTxt = TextView(context).apply {
                        text = data.status
                        setTextColor(Color.WHITE)
                        textSize = 12f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(20, 5, 20, 5)
                        val bg = GradientDrawable().apply {
                            setColor(Color.parseColor(data.statusColor))
                            cornerRadius = 12f
                        }
                        background = bg
                    }
                    
                    header.addView(sourceTxt, LinearLayout.LayoutParams(0, -2, 1f))
                    header.addView(statusTxt)
                    view.addView(header)

                    val fareTxt = TextView(context).apply {
                        text = data.totalFareLabel
                        setTextColor(Color.WHITE)
                        textSize = 32f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(0, 10, 0, 10)
                    }
                    view.addView(fareTxt)

                    val metrics = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 10, 0, 0)
                    }
                    
                    val perKmTxt = TextView(context).apply {
                        text = "R$ ${String.format("%.2f", data.perKm)}/km"
                        setTextColor(Color.parseColor("#F5D400"))
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    metrics.addView(perKmTxt)
                    view.addView(metrics)

                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP
                        y = 100
                        horizontalMargin = 0.05f
                        width = (context.resources.displayMetrics.widthPixels * 0.9).toInt()
                    }

                    wm.addView(view, params)
                    overlayView = view
                    Handler(Looper.getMainLooper()).postDelayed({ stop(context) }, 15000)
                } catch (e: Exception) {
                    KmCertoLogger.log("ERRO OVERLAY: ${e.message}")
                }
            }
        }

        fun stop(context: Context) {
            Handler(Looper.getMainLooper()).post {
                try {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    overlayView?.let {
                        wm.removeView(it)
                        overlayView = null
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class KmCertoFloatingBubbleService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
