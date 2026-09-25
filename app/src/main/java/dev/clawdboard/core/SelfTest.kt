package dev.clawdboard.core

import android.content.Context
import android.util.Log

object SelfTest {
    private const val TAG = "ClawdSelfTest"

    fun run(context: Context) {
        val v = Vault(context, "vault-selftest", "clawdboard.selftest")
        val out = StringBuilder()
        fun check(name: String, ok: Boolean) {
            out.append(if (ok) "PASS " else "FAIL ").append(name).append('\n')
        }
        try {
            v.wipe()
            val t0 = System.currentTimeMillis()
            val key = v.provision("2468", "sk-ant-oat01-primeiro")
            val tProvision = System.currentTimeMillis() - t0
            check("provisionado", v.isProvisioned)

            val t1 = System.currentTimeMillis()
            val ok = v.unlock("2468")
            val tUnlock = System.currentTimeMillis() - t1
            check("PIN certo abre o token", ok is Vault.Unlock.Ok && ok.token == "sk-ant-oat01-primeiro")

            val wrong = v.unlock("1111")
            check("PIN errado recusado com 9 restantes", wrong is Vault.Unlock.Wrong && wrong.remaining == 9)

            v.reseal(key, "sk-ant-oat01-segundo")
            val rotated = v.unlock("2468")
            check("rotação mantém o PIN", rotated is Vault.Unlock.Ok && rotated.token == "sk-ant-oat01-segundo")
            check("acerto zera as falhas", v.failures == 0)

            var last: Vault.Unlock? = null
            repeat(10) { last = v.unlock("0000") }
            check("10 erros apagam o cofre", last == Vault.Unlock.Wiped && !v.isProvisioned)

            out.append("tempo provision=${tProvision}ms unlock=${tUnlock}ms\n")
        } catch (e: Exception) {
            out.append("FAIL exceção: ${e.javaClass.simpleName}: ${e.message}\n")
        } finally {
            v.wipe()
        }
        out.lines().filter { it.isNotBlank() }.forEach { Log.i(TAG, it) }
    }
}
