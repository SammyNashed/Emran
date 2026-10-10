package io.github.sammynashed.emran.glance

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "D" or "E"
    val message: String,
    val throwable: Throwable? = null
) {
    fun format(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val timeStr = sdf.format(Date(timestamp))
        val levelStr = if (level == "E") "ERR" else "DBG"
        val exceptionStr = throwable?.let { "\n" + Log.getStackTraceString(it) } ?: ""
        return "[$timeStr] [$levelStr] $message$exceptionStr"
    }
}

object WidgetDebugLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun log(level: String, msg: String, throwable: Throwable? = null) {
        if (level == "E") {
            Log.e("EmranWidgetDebug", msg, throwable)
        } else {
            Log.d("EmranWidgetDebug", msg, throwable)
        }

        synchronized(this) {
            val currentList = _logs.value.toMutableList()
            currentList.add(LogEntry(level = level, message = msg, throwable = throwable))
            if (currentList.size > 500) {
                currentList.removeAt(0)
            }
            _logs.value = currentList
        }
    }

    fun d(msg: String, throwable: Throwable? = null) {
        log("D", msg, throwable)
    }

    fun e(msg: String, throwable: Throwable? = null) {
        log("E", msg, throwable)
    }

    fun clear() {
        synchronized(this) {
            _logs.value = emptyList()
        }
    }
}
