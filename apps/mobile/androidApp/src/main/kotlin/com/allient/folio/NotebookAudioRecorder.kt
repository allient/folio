package com.allient.folio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** MediaRecorder is confined to one worker; UI callbacks are always delivered on main. */
internal class NotebookAudioRecorder(
    context: Context,
    private val onRecorded: (notebookId: String, attachment: NoteAttachment) -> Unit,
) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "folio-audio") }
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var notebookId: String? = null
    @Volatile private var startedAtMillis = 0L
    @Volatile private var recordedMillis = 0L
    @Volatile private var paused = false
    @Volatile private var closing = false

    fun currentRecordingElapsedMillis(): Long =
        recordedMillis + if (startedAtMillis != 0L && !paused) (SystemClock.elapsedRealtime() - startedAtMillis).coerceAtLeast(0L) else 0L

    fun start(targetNotebookId: String, onResult: (Result<Unit>) -> Unit) {
        if (closing) { main.post { onResult(Result.failure(IllegalStateException("Recorder is closing."))) }; return }
        worker.execute {
            val result = runCatching { startOnWorker(targetNotebookId) }
            main.post { onResult(result) }
        }
    }

    fun stop(onResult: (Result<Unit>) -> Unit) {
        if (closing) return
        worker.execute {
            val result = runCatching { stopOnWorker() }
            main.post { onResult(result) }
        }
    }

    fun pause(onResult: (Result<Unit>) -> Unit) {
        if (closing) { main.post { onResult(Result.failure(IllegalStateException("Recorder is closing."))) }; return }
        worker.execute {
            val result = runCatching { pauseOnWorker() }
            main.post { onResult(result) }
        }
    }

    fun resume(onResult: (Result<Unit>) -> Unit) {
        if (closing) { main.post { onResult(Result.failure(IllegalStateException("Recorder is closing."))) }; return }
        worker.execute {
            val result = runCatching { resumeOnWorker() }
            main.post { onResult(result) }
        }
    }

    /** Drains earlier start/stop requests and finalizes any remaining recording before onClosed. */
    fun close(onClosed: () -> Unit) {
        if (closing) return
        closing = true
        worker.execute {
            try { stopOnWorker() }
            catch (error: Exception) { android.util.Log.e("Folio", "Could not finish recording while closing", error) }
            finally { onClosed() }
        }
        worker.shutdown()
    }

    @Suppress("DEPRECATION")
    private fun startOnWorker(targetNotebookId: String) {
        check(recorder == null) { "A recording is already running." }
        val directory = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.m4a")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(44100)
            next.setAudioEncodingBitRate(96000)
            next.setOutputFile(file.absolutePath)
            next.prepare()
            next.start()
            output = file; notebookId = targetNotebookId; recorder = next
            recordedMillis = 0L
            startedAtMillis = SystemClock.elapsedRealtime()
            paused = false
        } catch (error: Throwable) {
            runCatching { next.release() }; file.delete(); throw error
        }
    }

    private fun pauseOnWorker() {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { "Pausing recordings requires Android 7 or newer." }
        val active = checkNotNull(recorder) { "No recording is in progress." }
        check(!paused) { "Recording is already paused." }
        active.pause()
        recordedMillis += (SystemClock.elapsedRealtime() - startedAtMillis).coerceAtLeast(0L)
        paused = true
        startedAtMillis = 0L
    }

    private fun resumeOnWorker() {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { "Resuming recordings requires Android 7 or newer." }
        val active = checkNotNull(recorder) { "No recording is in progress." }
        check(paused) { "Recording is not paused." }
        active.resume()
        startedAtMillis = SystemClock.elapsedRealtime()
        paused = false
    }

    private fun stopOnWorker() {
        val active = recorder ?: return
        val file = checkNotNull(output)
        val target = checkNotNull(notebookId)
        recorder = null; output = null; notebookId = null
        startedAtMillis = 0L
        recordedMillis = 0L
        paused = false
        try {
            active.stop()
            require(file.length() > 0) { "Recording is empty." }
        } catch (error: Throwable) { file.delete(); throw error }
        finally { active.release() }
        val time = SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.US).format(Date())
        onRecorded(target, NoteAttachment(name = "Recording $time.m4a", path = file.absolutePath, mimeType = "audio/mp4"))
    }
}
