package com.myapp.fastshare_plugin

import android.util.Log

object Logger {
    private const val TAG = "FastShare"

    // ANSI Color codes
    private const val COLOR_ERROR = "\u001B[31m"      // Dark red
    private const val COLOR_SUCCESS = "\u001B[32m"    // Dark green
    private const val COLOR_WARNING = "\u001B[37m"    // White/gray
    private const val COLOR_DEBUG = "\u001B[34m"      // Blue
    private const val COLOR_VERBOSE = "\u001B[35m"    // Magenta
    private const val COLOR_RESET = "\u001B[0m"       // Reset

    // Callback to send log events to Flutter
    var logEventCallback: ((LogEvent) -> Unit)? = null

    enum class LogLevel {
        ERROR, SUCCESS, WARNING, DEBUG, VERBOSE
    }

    fun error(code: String, message: String) {
        val formatted = formatLog(LogLevel.ERROR, code, message)
        Log.e(TAG, formatted)
        logEventCallback?.invoke(createLogEvent(LogLevel.ERROR, code, message))
    }

    fun success(code: String, message: String) {
        val formatted = formatLog(LogLevel.SUCCESS, code, message)
        Log.i(TAG, formatted)
        logEventCallback?.invoke(createLogEvent(LogLevel.SUCCESS, code, message))
    }

    fun warning(code: String, message: String) {
        val formatted = formatLog(LogLevel.WARNING, code, message)
        Log.w(TAG, formatted)
        logEventCallback?.invoke(createLogEvent(LogLevel.WARNING, code, message))
    }

    fun debug(code: String, message: String) {
        val formatted = formatLog(LogLevel.DEBUG, code, message)
        Log.d(TAG, formatted)
        logEventCallback?.invoke(createLogEvent(LogLevel.DEBUG, code, message))
    }

    fun verbose(code: String, message: String) {
        val formatted = formatLog(LogLevel.VERBOSE, code, message)
        Log.v(TAG, formatted)
        logEventCallback?.invoke(createLogEvent(LogLevel.VERBOSE, code, message))
    }

    private fun formatLog(level: LogLevel, code: String, message: String): String {
        val color = when (level) {
            LogLevel.ERROR -> COLOR_ERROR
            LogLevel.SUCCESS -> COLOR_SUCCESS
            LogLevel.WARNING -> COLOR_WARNING
            LogLevel.DEBUG -> COLOR_DEBUG
            LogLevel.VERBOSE -> COLOR_VERBOSE
        }
        val levelStr = when (level) {
            LogLevel.ERROR -> "ERROR"
            LogLevel.SUCCESS -> "SUCCESS"
            LogLevel.WARNING -> "WARN"
            LogLevel.DEBUG -> "DEBUG"
            LogLevel.VERBOSE -> "VERBOSE"
        }
        return "[$levelStr-FastShare-$code] $color$message$COLOR_RESET"
    }

    // For sending logs to Flutter events
    data class LogEvent(
        val level: String,
        val code: String,
        val coloredMessage: String,
        val rawMessage: String
    )

    fun createLogEvent(level: LogLevel, code: String, message: String): LogEvent {
        val levelStr = when (level) {
            LogLevel.ERROR -> "ERROR"
            LogLevel.SUCCESS -> "SUCCESS"
            LogLevel.WARNING -> "WARN"
            LogLevel.DEBUG -> "DEBUG"
            LogLevel.VERBOSE -> "VERBOSE"
        }
        val color = when (level) {
            LogLevel.ERROR -> COLOR_ERROR
            LogLevel.SUCCESS -> COLOR_SUCCESS
            LogLevel.WARNING -> COLOR_WARNING
            LogLevel.DEBUG -> COLOR_DEBUG
            LogLevel.VERBOSE -> COLOR_VERBOSE
        }
        return LogEvent(
            level = levelStr,
            code = code,
            coloredMessage = "[$levelStr-FastShare-$code] $color$message$COLOR_RESET",
            rawMessage = message
        )
    }
}