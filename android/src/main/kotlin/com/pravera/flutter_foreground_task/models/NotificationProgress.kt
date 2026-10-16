package com.pravera.flutter_foreground_task.models

import org.json.JSONObject

/// Barra de progresso exibida na notificação do serviço.
data class NotificationProgress(
        val max: Int,
        val current: Int,
        val indeterminate: Boolean
) {
    companion object {
        fun fromJsonString(jsonString: String): NotificationProgress? {
            return try {
                fromJSONObject(JSONObject(jsonString))
            } catch (e: Exception) {
                null
            }
        }

        fun fromJSONObject(jsonObj: JSONObject): NotificationProgress {
            return NotificationProgress(
                max = jsonObj.optInt("max", 0),
                current = jsonObj.optInt("current", 0),
                indeterminate = jsonObj.optBoolean("indeterminate", false)
            )
        }
    }
}
