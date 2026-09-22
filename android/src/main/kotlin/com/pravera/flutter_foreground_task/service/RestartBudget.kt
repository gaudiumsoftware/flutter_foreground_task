package com.pravera.flutter_foreground_task.service

import android.content.Context
import android.util.Log
import com.pravera.flutter_foreground_task.PreferencesKey as PrefsKey

/**
 * [GAUDIUM] Orcamento de reinicios automaticos do foreground service.
 *
 * Sem isso o ciclo "onDestroy -> setRestartAlarm(5s) -> RestartReceiver ->
 * startForegroundService -> startForeground() lanca -> stopForegroundService ->
 * onDestroy" se repete indefinidamente a cada 5 segundos enquanto a causa raiz
 * persistir (cota de FGS esgotada, start de FGS nao permitido em background, etc),
 * drenando bateria e acordando o device sem limite.
 *
 * Aqui cada tentativa consome uma fatia de um orcamento com backoff crescente e
 * teto. O contador zera quando o servico volta ao foreground com sucesso, quando
 * o app pede um start explicito, ou quando a janela de observacao expira.
 */
object RestartBudget {
    private val TAG = RestartBudget::class.java.simpleName

    /** Atraso de cada tentativa, em ms. O tamanho do array e o teto de tentativas. */
    private val BACKOFF_DELAYS = intArrayOf(
        5_000,     // 5s
        30_000,    // 30s
        120_000,   // 2min
        600_000,   // 10min
        1_800_000  // 30min
    )

    /** Passada esta janela sem nenhuma tentativa, o orcamento volta ao cheio. */
    private const val WINDOW_MILLIS = 60 * 60 * 1000L // 1h

    /**
     * Consome uma tentativa e devolve o atraso a usar no alarme, ou null quando o
     * orcamento acabou -- nesse caso o chamador NAO deve agendar alarme algum.
     *
     * @param firstAttemptDelayMillis atraso da primeira tentativa da janela. Existe
     *   porque o onTaskRemoved quer reagir mais rapido (1s) que o onDestroy (5s).
     */
    fun consume(context: Context, firstAttemptDelayMillis: Int = BACKOFF_DELAYS[0]): Int? {
        val prefs = context.getSharedPreferences(PrefsKey.RESTART_BUDGET_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val windowStart = prefs.getLong(PrefsKey.RESTART_WINDOW_START, 0L)
        var attempts = prefs.getInt(PrefsKey.RESTART_ATTEMPT_COUNT, 0)

        // Janela expirada (ou primeira tentativa): recomeca o orcamento.
        val isNewWindow = windowStart == 0L || (now - windowStart) > WINDOW_MILLIS
        if (isNewWindow) {
            attempts = 0
        }

        if (attempts >= BACKOFF_DELAYS.size) {
            Log.e(TAG, "Auto-restart budget exhausted (${BACKOFF_DELAYS.size} attempts). " +
                    "No further restart alarm will be scheduled until the service starts " +
                    "successfully or ${WINDOW_MILLIS / 60000} minutes elapse.")
            return null
        }

        val delay = if (attempts == 0) firstAttemptDelayMillis else BACKOFF_DELAYS[attempts]

        with(prefs.edit()) {
            putInt(PrefsKey.RESTART_ATTEMPT_COUNT, attempts + 1)
            putLong(PrefsKey.RESTART_WINDOW_START, if (isNewWindow) now else windowStart)
            commit()
        }

        Log.w(TAG, "Auto-restart attempt ${attempts + 1}/${BACKOFF_DELAYS.size} scheduled in ${delay}ms.")
        return delay
    }

    /** Zera o orcamento. Chamar quando o servico voltou a rodar em foreground. */
    fun reset(context: Context) {
        val prefs = context.getSharedPreferences(PrefsKey.RESTART_BUDGET_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(PrefsKey.RESTART_ATTEMPT_COUNT, 0) == 0) {
            return
        }

        with(prefs.edit()) {
            remove(PrefsKey.RESTART_ATTEMPT_COUNT)
            remove(PrefsKey.RESTART_WINDOW_START)
            commit()
        }
        Log.i(TAG, "Auto-restart budget reset.")
    }
}
