package io.github.radumariusi.akvodhub.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Criptează datele sensibile (link-uri cu parolă, user, parolă) înainte de a fi salvate în baza de date. */
interface SecretCipher {
    fun encrypt(plain: String): String

    fun decrypt(stored: String): String
}

/**
 * AES-GCM cu o cheie din Android Keystore (cheia nu părăsește dispozitivul).
 *
 * Unele box-uri TV ieftine au un Keystore defect; atunci salvăm textul marcat ca necriptat,
 * ca importul să funcționeze totuși (baza de date e oricum privată aplicației și exclusă din backup).
 */
class KeystoreSecretCipher : SecretCipher {
    override fun encrypt(plain: String): String =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            ENCRYPTED + Base64.encodeToString(iv + data, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Keystore indisponibil, salvez necriptat", e)
            PLAIN + plain
        }

    override fun decrypt(stored: String): String =
        when {
            stored.startsWith(ENCRYPTED) -> {
                val bytes = Base64.decode(stored.removePrefix(ENCRYPTED), Base64.NO_WRAP)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
                String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
            }

            else -> {
                stored.removePrefix(PLAIN)
            }
        }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecretCipher"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "akvodhub_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val ENCRYPTED = "k1:"
        const val PLAIN = "p0:"
    }
}
