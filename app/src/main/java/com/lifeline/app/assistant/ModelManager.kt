package com.lifeline.app.assistant

import android.app.ActivityManager
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** An open model that LiteRT-LM can run on the phone. */
data class AiModel(
    val id: String,
    val name: String,
    val summary: String,
    val url: String,
    val fileName: String,
    val sizeBytes: Long,
    val minRamGb: Int,
    /** Reasoning models get thinking turned off so answers come straight away. */
    val canThink: Boolean = false
)

object ModelCatalog {
    val models = listOf(
        AiModel(
            id = "lfm2.5-1.2b",
            name = "Standard · LFM 2.5 1.2B",
            summary = "Recommended. Quick, clear answers on most phones.",
            url = "https://huggingface.co/litert-community/LFM2.5-1.2B-Instruct/resolve/main/LFM2.5-1.2B-Instruct_int4.litertlm",
            fileName = "LFM2.5-1.2B-Instruct_int4.litertlm",
            sizeBytes = 736_000_000L,
            minRamGb = 4
        ),
        AiModel(
            id = "gemma4-e2b",
            name = "Best quality · Gemma 4 E2B",
            summary = "More detailed answers. Needs a recent phone with 8 GB RAM.",
            url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-E2B-it.litertlm",
            sizeBytes = 2_588_147_712L,
            minRamGb = 8
        )
    )

    fun byId(id: String?): AiModel? = models.firstOrNull { it.id == id }
}

enum class ModelStatus { NOT_DOWNLOADED, DOWNLOADING, READY, FAILED }

data class ModelState(
    val status: ModelStatus = ModelStatus.NOT_DOWNLOADED,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
) {
    val progress: Float get() = if (totalBytes <= 0) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

/**
 * Downloads models once (with Android's DownloadManager, so it survives the app closing) into
 * app storage, and remembers which one the assistant should use.
 */
object ModelManager {
    private const val TAG = "ModelManager"
    private const val PREFS = "offline_ai_models"
    private const val KEY_SELECTED = "selected"
    private fun keyDownload(id: String) = "download_$id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pollers = mutableMapOf<String, Job>()

    private val _states = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    @Volatile private var initialized = false

    fun file(context: Context, model: AiModel): File =
        File(context.getExternalFilesDir("models") ?: File(context.filesDir, "models"), model.fileName)

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _selected.value = prefs.getString(KEY_SELECTED, null)
        ModelCatalog.models.forEach { model ->
            val downloadId = prefs.getLong(keyDownload(model.id), -1L)
            when {
                downloadId >= 0 -> poll(app, model, downloadId)
                isComplete(app, model) -> setState(model.id, ModelState(ModelStatus.READY, model.sizeBytes, model.sizeBytes))
                else -> setState(model.id, ModelState())
            }
        }
    }

    /** The model the assistant should use, if it is downloaded. */
    fun activeModel(context: Context): AiModel? {
        init(context)
        val model = ModelCatalog.byId(_selected.value) ?: return null
        return model.takeIf { _states.value[it.id]?.status == ModelStatus.READY }
    }

    fun deviceRamGb(context: Context): Double {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info.totalMem / 1_073_741_824.0
    }

    fun download(context: Context, model: AiModel) {
        val app = context.applicationContext
        val target = file(app, model)
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("Offline AI model")
            .setDescription(model.name)
            .setDestinationUri(Uri.fromFile(target))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val id = app.getSystemService(DownloadManager::class.java).enqueue(request)
        prefs(app).edit().putLong(keyDownload(model.id), id).apply()
        setState(model.id, ModelState(ModelStatus.DOWNLOADING, 0, model.sizeBytes))
        poll(app, model, id)
    }

    fun cancel(context: Context, model: AiModel) {
        val app = context.applicationContext
        val id = prefs(app).getLong(keyDownload(model.id), -1L)
        if (id >= 0) app.getSystemService(DownloadManager::class.java).remove(id)
        pollers.remove(model.id)?.cancel()
        prefs(app).edit().remove(keyDownload(model.id)).apply()
        file(app, model).delete()
        setState(model.id, ModelState())
    }

    fun delete(context: Context, model: AiModel) {
        if (_selected.value == model.id) OnDeviceLlm.unload()
        cancel(context, model)
        if (_selected.value == model.id) select(context, null)
    }

    fun select(context: Context, id: String?) {
        _selected.value = id
        prefs(context.applicationContext).edit().putString(KEY_SELECTED, id).apply()
    }

    private fun poll(context: Context, model: AiModel, downloadId: Long) {
        pollers.remove(model.id)?.cancel()
        pollers[model.id] = scope.launch {
            val dm = context.getSystemService(DownloadManager::class.java)
            while (isActive) {
                val row = dm.query(DownloadManager.Query().setFilterById(downloadId))
                if (row == null || !row.moveToFirst()) {
                    row?.close()
                    finish(context, model, success = isComplete(context, model), error = "Download was removed")
                    return@launch
                }
                val status = row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val reason = row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                row.close()
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        finish(context, model, success = true); return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        finish(context, model, success = false, error = "Download failed (code $reason)"); return@launch
                    }
                    else -> setState(
                        model.id,
                        ModelState(ModelStatus.DOWNLOADING, done, if (total > 0) total else model.sizeBytes)
                    )
                }
                delay(700)
            }
        }
    }

    private fun finish(context: Context, model: AiModel, success: Boolean, error: String? = null) {
        prefs(context).edit().remove(keyDownload(model.id)).apply()
        if (success) {
            setState(model.id, ModelState(ModelStatus.READY, model.sizeBytes, model.sizeBytes))
            if (activeModel(context) == null) select(context, model.id)
            Log.i(TAG, "Model ${model.id} ready")
        } else {
            file(context, model).delete()
            setState(model.id, ModelState(ModelStatus.FAILED, error = error))
        }
    }

    private fun isComplete(context: Context, model: AiModel): Boolean =
        file(context, model).let { it.exists() && it.length() >= model.sizeBytes * 99 / 100 }

    private fun setState(id: String, state: ModelState) = _states.update { it + (id to state) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
