package com.blackandblue.justshare.data.billing

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.android.billingclient.api.Purchase
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Purchase tokens stay encrypted with a non-exportable device key; never log this payload. */
internal class ProtectedPurchase {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun seal(purchase: Purchase, user: String, product: String, receipt: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key()); updateAAD("$user|$product|$receipt".toByteArray(Charsets.UTF_8))
        }
        val payload = JSONObject().put("json", purchase.originalJson).put("signature", purchase.signature).toString()
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(payload.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun open(attempt: SupportAttempt): Purchase? = runCatching {
        val parts = checkNotNull(attempt.protectedPurchase).split('.')
        check(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            updateAAD("${attempt.user}|${attempt.product}|${attempt.receipt}".toByteArray(Charsets.UTF_8))
        }
        val payload = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
        Purchase(payload.getString("json"), payload.getString("signature")).also {
            check(it.products.singleOrNull() == attempt.product && it.purchaseToken.receiptHash() == attempt.receipt)
        }
    }.getOrNull()
    private companion object {
        const val ALIAS = "justshare_pending_purchase_v1"
    }
}
internal fun String.receiptHash(): String = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
