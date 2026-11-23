package com.pravera.flutter_foreground_task.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import com.pravera.flutter_foreground_task.PreferencesKey as PrefsKey

/**
 * Desliga o layout customizado da notificação quando ele derruba o app.
 *
 * O sistema desenha o RemoteViews com a tabela de recursos do app que tem em cache. Depois de
 * uma atualização, alguns aparelhos seguem com a tabela já substituída até reiniciar: o ID do
 * layout customizado passa a apontar para outro layout, a inflação falha e o sistema derruba
 * o processo com RemoteServiceException. Esse crash é entregue pelo sistema na thread principal
 * e não pode ser capturado.
 *
 * Cada post com o layout customizado fica marcado como pendente até o processo sobreviver a
 * ele por [CONFIRM_DELAY_MILLIS]. Uma marca deixada por outro processo, na mesma instalação e no
 * mesmo boot, significa que o post derrubou o app: o layout customizado fica desligado até o
 * aparelho reiniciar ou o app ser reinstalado. Um processo encerrado por outro motivo dentro
 * dessa janela também desliga o layout; o custo é exibir o template padrão até o próximo boot.
 */
class CustomLayoutGuard(context: Context) {
    companion object {
        private val TAG = CustomLayoutGuard::class.java.simpleName

        /** O crash chega cerca de 1 s depois do post. */
        private const val CONFIRM_DELAY_MILLIS = 5_000L

        /** A hora do boot vem de dois relógios lidos em sequência e oscila alguns ms. */
        private const val BOOT_TOLERANCE_MILLIS = 60_000L
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        PrefsKey.CUSTOM_LAYOUT_GUARD_PREFS, Context.MODE_PRIVATE
    )
    private val handler = Handler(Looper.getMainLooper())
    private val confirmRunnable = Runnable { clearPending() }

    /**
     * Indica se o layout customizado está desligado na instalação e no boot atuais.
     *
     * @return true quando a notificação deve usar o template padrão.
     */
    fun isBlocked(): Boolean {
        val installTime = installTime()
        val bootTime = bootTime()

        val pendingPid = prefs.getInt(PrefsKey.CUSTOM_LAYOUT_PENDING_PID, 0)
        if (pendingPid != 0 && pendingPid != Process.myPid()) {
            val crashedOnPost = isSameSession(
                prefs.getLong(PrefsKey.CUSTOM_LAYOUT_PENDING_INSTALL, 0L),
                prefs.getLong(PrefsKey.CUSTOM_LAYOUT_PENDING_BOOT, 0L),
                installTime,
                bootTime
            )
            if (crashedOnPost) {
                Log.w(TAG, "O processo anterior morreu logo após postar o layout customizado; " +
                        "a notificação usará o template padrão até o próximo boot ou atualização.")
                with(prefs.edit()) {
                    putLong(PrefsKey.CUSTOM_LAYOUT_BLOCKED_INSTALL, installTime)
                    putLong(PrefsKey.CUSTOM_LAYOUT_BLOCKED_BOOT, bootTime)
                    commit()
                }
            }
            clearPending()
        }

        return isSameSession(
            prefs.getLong(PrefsKey.CUSTOM_LAYOUT_BLOCKED_INSTALL, 0L),
            prefs.getLong(PrefsKey.CUSTOM_LAYOUT_BLOCKED_BOOT, 0L),
            installTime,
            bootTime
        )
    }

    /**
     * Marca o post com o layout customizado como pendente. Chamar antes de entregar a
     * notificação ao sistema.
     */
    fun markPending() {
        // commit() e não apply(): a marca precisa estar no disco antes de o processo morrer.
        // Com a marca deste processo já gravada, basta reagendar a confirmação.
        if (prefs.getInt(PrefsKey.CUSTOM_LAYOUT_PENDING_PID, 0) != Process.myPid()) {
            with(prefs.edit()) {
                putInt(PrefsKey.CUSTOM_LAYOUT_PENDING_PID, Process.myPid())
                putLong(PrefsKey.CUSTOM_LAYOUT_PENDING_INSTALL, installTime())
                putLong(PrefsKey.CUSTOM_LAYOUT_PENDING_BOOT, bootTime())
                commit()
            }
        }

        // A confirmação conta a partir do último post: uma atualização seguida não pode limpar
        // a marca de um post que ainda não teve tempo de falhar.
        handler.removeCallbacks(confirmRunnable)
        handler.postDelayed(confirmRunnable, CONFIRM_DELAY_MILLIS)
    }

    private fun clearPending() {
        with(prefs.edit()) {
            remove(PrefsKey.CUSTOM_LAYOUT_PENDING_PID)
            remove(PrefsKey.CUSTOM_LAYOUT_PENDING_INSTALL)
            remove(PrefsKey.CUSTOM_LAYOUT_PENDING_BOOT)
            apply()
        }
    }

    /**
     * @param install hora da instalação gravada.
     * @param boot hora do boot gravada.
     * @param currentInstall hora da instalação atual.
     * @param currentBoot hora do boot atual.
     * @return true quando os valores gravados são da instalação e do boot atuais.
     */
    private fun isSameSession(
        install: Long,
        boot: Long,
        currentInstall: Long,
        currentBoot: Long
    ): Boolean {
        return install != 0L &&
                install == currentInstall &&
                abs(boot - currentBoot) < BOOT_TOLERANCE_MILLIS
    }

    /**
     * Muda a cada instalação ou atualização, inclusive de builds com o mesmo versionCode.
     *
     * @return hora da última instalação do app, em ms.
     */
    private fun installTime(): Long {
        return try {
            val pm = appContext.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(appContext.packageName, 0)
            }
            info.lastUpdateTime
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, e.message, e)
            0L
        }
    }

    /** @return hora do último boot do aparelho, em ms. */
    private fun bootTime(): Long {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }
}
