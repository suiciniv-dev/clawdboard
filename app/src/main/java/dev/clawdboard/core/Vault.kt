package dev.clawdboard.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Vault(context: Context, prefsName: String = "vault", private val alias: String = DEFAULT_ALIAS) {
    private val sp = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    sealed interface Unlock {
        data class Ok(val token: String, val key: SecretKey) : Unlock
        data class Wrong(val remaining: Int) : Unlock
        data object Wiped : Unlock
        data object NotProvisioned : Unlock
        data class Broken(val reason: String) : Unlock
    }

    val isProvisioned: Boolean get() = sp.contains("blob") && sp.contains("salt")
    val failures: Int get() = sp.getInt("failures", 0)
    val remaining: Int get() = (MAX_ATTEMPTS - failures).coerceAtLeast(0)

    @Synchronized
    fun provision(pin: String, token: String): SecretKey {
        val salt = Crypto.random(16)
        val key = Crypto.derive(pin, salt)
        store(salt, key, token)
        sp.edit().putInt("failures", 0).commit()
        return key
    }

    @Synchronized
    fun reseal(key: SecretKey, token: String) {
        val salt = Crypto.unb64(sp.getString("salt", null) ?: error(txt.emptyVault))
        store(salt, key, token)
    }

    @Synchronized
    fun unlock(pin: String): Unlock {
        if (!isProvisioned) return Unlock.NotProvisioned
        val salt = Crypto.unb64(sp.getString("salt", null)!!)
        val inner = try {
            keystoreOpen(Crypto.unb64(sp.getString("blob", null)!!))
        } catch (e: Exception) {
            return Unlock.Broken(txt.keystoreUnavailable(e.javaClass.simpleName))
        }
        val key = Crypto.derive(pin, salt)
        return try {
            val token = String(Crypto.open(key, inner), Charsets.UTF_8)
            sp.edit().putInt("failures", 0).commit()
            Unlock.Ok(token, key)
        } catch (e: GeneralSecurityException) {
            val f = failures + 1
            if (f >= MAX_ATTEMPTS) {
                wipe()
                Unlock.Wiped
            } else {
                sp.edit().putInt("failures", f).commit()
                Unlock.Wrong(MAX_ATTEMPTS - f)
            }
        }
    }

    @Synchronized
    fun wipe() {
        sp.edit().clear().commit()
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun store(salt: ByteArray, key: SecretKey, token: String) {
        val inner = Crypto.seal(key, token.toByteArray(Charsets.UTF_8))
        val outer = keystoreSeal(inner)
        sp.edit().putString("salt", Crypto.b64(salt)).putString("blob", Crypto.b64(outer)).commit()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun hwKey(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun keystoreSeal(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, hwKey())
        return c.iv + c.doFinal(plain)
    }

    private fun keystoreOpen(data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, hwKey(), GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }

    companion object {
        const val MAX_ATTEMPTS = 10
        const val DEFAULT_ALIAS = "clawdboard.vault"

        fun pinProblem(pin: String): String? = when {
            pin.length !in 4..8 -> txt.pinLength
            !pin.all { it.isDigit() } -> txt.pinDigits
            else -> null
        }
    }
}
