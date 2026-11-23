package com.pravera.flutter_foreground_task.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.util.UUID
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
 * A tabela usada pelo sistema só muda com reboot ou atualização do app. Por isso basta
 * verificar o primeiro post com o layout customizado de cada instalação e boot: ele fica marcado
 * como pendente até o processo sobreviver a ele por [CONFIRM_DELAY_MILLIS], e a partir daí a
 * combinação fica confirmada e os posts seguintes não são marcados. Uma marca pendente deixada
 * por outro processo, na mesma instalação e no mesmo boot, significa que o post derrubou o app:
 * o layout customizado fica desligado até o aparelho reiniciar ou o app ser reinstalado.
 *
 * A partir do Android 11 a marca só desliga o layout quando o sistema registra que o processo
 * morreu por crash: fechar o app em Recentes ou ser encerrado por falta de memória não conta.
 * Antes disso não há esse registro, e qualquer morte dentro da janela do primeiro post desliga o
 * layout até o próximo boot.
 */
class CustomLayoutGuard(context: Context) {
    companion object {
        private val TAG = CustomLayoutGuard::class.java.simpleName

        /** O crash chega cerca de 1 s depois do post. */
        private const val CONFIRM_DELAY_MILLIS = 5_000L

        /**
         * Sem BOOT_COUNT, o boot é identificado pela hora em que aconteceu, derivada de dois
         * relógios lidos em sequência, que oscila alguns ms.
         */
        private const val BOOT_TOLERANCE_MILLIS = 60_000L

        /**
         * Identifica o processo atual. Fica no companion para ser o mesmo em todas as instâncias
         * do serviço dentro do processo; o PID não serve porque um processo novo pode recebê-lo
         * de volta.
         */
        private val PROCESS_ID = UUID.randomUUID().toString()
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        PrefsKey.CUSTOM_LAYOUT_GUARD_PREFS, Context.MODE_PRIVATE
    )
    private val handler = Handler(Looper.getMainLooper())
    private var isConfirmScheduled = false

    // Atualizar o app ou reiniciar o aparelho encerra o processo, então os valores são fixos
    // durante a vida dele.
    private val installTime by lazy { readInstallTime() }
    private val bootCount by lazy { readBootCount() }
    private val bootId by lazy { bootCount ?: readBootTime() }

    /**
     * Indica se o layout customizado está desligado na instalação e no boot atuais.
     *
     * @return true quando a notificação deve usar o template padrão.
     */
    fun isBlocked(): Boolean {
        val pendingProcess = prefs.getString(PrefsKey.CUSTOM_LAYOUT_PENDING_PROCESS, null)
        if (pendingProcess != null && pendingProcess != PROCESS_ID) {
            val crashedOnPost = isCurrentSession(
                PrefsKey.CUSTOM_LAYOUT_PENDING_INSTALL,
                PrefsKey.CUSTOM_LAYOUT_PENDING_BOOT
            ) && diedFromCrash(
                prefs.getInt(PrefsKey.CUSTOM_LAYOUT_PENDING_PID, 0),
                prefs.getLong(PrefsKey.CUSTOM_LAYOUT_PENDING_AT, 0L)
            )
            with(prefs.edit()) {
                if (crashedOnPost) {
                    Log.w(TAG, "O processo anterior morreu logo após postar o layout " +
                            "customizado; a notificação usará o template padrão até o próximo " +
                            "boot ou atualização.")
                    putLong(PrefsKey.CUSTOM_LAYOUT_BLOCKED_INSTALL, installTime)
                    putLong(PrefsKey.CUSTOM_LAYOUT_BLOCKED_BOOT, bootId)
                }
                remove(PrefsKey.CUSTOM_LAYOUT_PENDING_PROCESS)
                remove(PrefsKey.CUSTOM_LAYOUT_PENDING_PID)
                remove(PrefsKey.CUSTOM_LAYOUT_PENDING_AT)
                remove(PrefsKey.CUSTOM_LAYOUT_PENDING_INSTALL)
                remove(PrefsKey.CUSTOM_LAYOUT_PENDING_BOOT)
                commit()
            }
        }

        return isCurrentSession(
            PrefsKey.CUSTOM_LAYOUT_BLOCKED_INSTALL,
            PrefsKey.CUSTOM_LAYOUT_BLOCKED_BOOT
        )
    }

    /**
     * Marca o post com o layout customizado como pendente, quando a instalação e o boot atuais
     * ainda não foram confirmados. Chamar antes de entregar a notificação ao sistema.
     */
    fun markPending() {
        if (isConfirmScheduled) {
            return
        }

        if (isCurrentSession(
                PrefsKey.CUSTOM_LAYOUT_CONFIRMED_INSTALL,
                PrefsKey.CUSTOM_LAYOUT_CONFIRMED_BOOT
            )
        ) {
            return
        }

        // commit() e não apply(): a marca precisa estar no disco antes de o processo morrer.
        with(prefs.edit()) {
            putString(PrefsKey.CUSTOM_LAYOUT_PENDING_PROCESS, PROCESS_ID)
            putInt(PrefsKey.CUSTOM_LAYOUT_PENDING_PID, Process.myPid())
            putLong(PrefsKey.CUSTOM_LAYOUT_PENDING_AT, System.currentTimeMillis())
            putLong(PrefsKey.CUSTOM_LAYOUT_PENDING_INSTALL, installTime)
            putLong(PrefsKey.CUSTOM_LAYOUT_PENDING_BOOT, bootId)
            commit()
        }

        isConfirmScheduled = true
        handler.postDelayed({ confirm() }, CONFIRM_DELAY_MILLIS)
    }

    private fun confirm() {
        isConfirmScheduled = false

        with(prefs.edit()) {
            remove(PrefsKey.CUSTOM_LAYOUT_PENDING_PROCESS)
            remove(PrefsKey.CUSTOM_LAYOUT_PENDING_PID)
            remove(PrefsKey.CUSTOM_LAYOUT_PENDING_AT)
            remove(PrefsKey.CUSTOM_LAYOUT_PENDING_INSTALL)
            remove(PrefsKey.CUSTOM_LAYOUT_PENDING_BOOT)
            putLong(PrefsKey.CUSTOM_LAYOUT_CONFIRMED_INSTALL, installTime)
            putLong(PrefsKey.CUSTOM_LAYOUT_CONFIRMED_BOOT, bootId)
            apply()
        }
    }

    /**
     * @param installKey chave da hora da instalação gravada.
     * @param bootKey chave da identificação do boot gravada.
     * @return true quando os valores gravados nas chaves são da instalação e do boot atuais.
     */
    private fun isCurrentSession(installKey: String, bootKey: String): Boolean {
        val install = prefs.getLong(installKey, 0L)
        val boot = prefs.getLong(bootKey, 0L)
        val isSameBoot = if (bootCount != null) {
            boot == bootId
        } else {
            abs(boot - bootId) < BOOT_TOLERANCE_MILLIS
        }

        return install != 0L && install == installTime && isSameBoot
    }

    /**
     * Consulta o motivo da morte do processo que deixou a marca. Sem registro do sistema
     * (Android 10 ou anterior, ou registro já descartado), assume crash: deixar o layout ligado
     * repetiria o crash a cada corrida.
     *
     * @param pid PID do processo que deixou a marca.
     * @param markedAt hora em que a marca foi gravada, em ms.
     * @return false quando o sistema registra que o processo morreu por outro motivo.
     */
    private fun diedFromCrash(pid: Int, markedAt: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || pid == 0) {
            return true
        }

        val exitInfo = try {
            appContext.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(appContext.packageName, pid, 1)
                ?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
            null
        }

        // Registro anterior à marca é de outro processo que teve o mesmo PID.
        if (exitInfo == null || exitInfo.timestamp < markedAt) {
            return true
        }

        return exitInfo.reason == ApplicationExitInfo.REASON_CRASH
    }

    /**
     * Muda a cada instalação ou atualização, inclusive de builds com o mesmo versionCode.
     *
     * @return hora da última instalação do app, em ms.
     */
    private fun readInstallTime(): Long {
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

    /**
     * Contador de boots do sistema. Não depende do relógio de parede, que pode saltar por
     * sincronização de rede ou ajuste manual.
     *
     * @return número de boots do aparelho, ou null quando o sistema não o informa.
     */
    private fun readBootCount(): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null
        }

        val count = Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
        return if (count >= 0) count.toLong() else null
    }

    /** @return hora do último boot do aparelho, em ms. */
    private fun readBootTime(): Long {
        return System.currentTimeMillis() - SystemClock.elapsedRealtime()
    }
}
