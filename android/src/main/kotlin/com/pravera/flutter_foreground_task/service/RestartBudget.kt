package com.pravera.flutter_foreground_task.service

import android.content.Context
import android.util.Log
import com.pravera.flutter_foreground_task.PreferencesKey as PrefsKey

/**
 * Orçamento de reinícios automáticos do foreground service.
 *
 * Utilizado para controlar o ciclo "onDestroy -> setRestartAlarm(5s) -> RestartReceiver ->
 * startForegroundService -> startForeground() -> stopForegroundService ->
 * onDestroy" que se repete indefinidamente a cada 5 segundos para tentar iniciar o
 * serviço novamente.
 *
 * Aqui cada tentativa consome uma fatia de um orçamento com backoff crescente e
 * teto. O contador deve zerar quando o servico volta ao foreground com sucesso,
 * quando o app pede um start explicito, ou quando a janela de observação expira.
 */
object RestartBudget {
    private val TAG = RestartBudget::class.java.simpleName

    /** Atraso de cada tentativa, em ms. */
    private val BACKOFF_DELAYS = intArrayOf(
        5_000,     // 5 s
        30_000,    // 30 s
        120_000,   // 2 min
        600_000,   // 10 min
        1_800_000  // 30 min
    )

    /** Passada esta janela sem nenhuma tentativa, o orçamento volta ao cheio. */
    private const val WINDOW_MILLIS = 60 * 60 * 1000L // 1h

    /**
     * Consome uma tentativa e devolve o atraso a usar no alarme, ou null quando o
     * orçamento acabou -- nesse caso o chamador NÃO deve agendar alarme algum.
     *
     * @param firstAttemptDelayMillis atraso da primeira tentativa da janela. Existe
     *   porque o onTaskRemoved quer reagir mais rapido (1s) que o onDestroy (5s).
     */
    fun consume(context: Context, firstAttemptDelayMillis: Int = BACKOFF_DELAYS[0]): Int? {
        val prefs = context.getSharedPreferences(PrefsKey.RESTART_BUDGET_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val windowStart = prefs.getLong(PrefsKey.RESTART_WINDOW_START, 0L)
        var attempts = prefs.getInt(PrefsKey.RESTART_ATTEMPT_COUNT, 0)

        // Janela expirada ou primeira tentativa: recomeça o orçamento.
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

    /** Zera o orçamento. Chamar quando o serviço voltou a rodar em foreground. */
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
