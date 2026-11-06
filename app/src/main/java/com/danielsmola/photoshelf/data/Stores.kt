package com.danielsmola.photoshelf.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Credentials(val address: String, val user: String, val password: String, val deviceId: String?)

/** Sign-in details, with the password and 2FA device token encrypted by the Android Keystore. */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("photoshelf_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(s: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(s.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(s: String?): String? = runCatching {
        val b = Base64.decode(s ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b.copyOfRange(0, 12)))
        String(c.doFinal(b.copyOfRange(12, b.size)))
    }.getOrNull()

    fun save(c: Credentials) {
        prefs.edit()
            .putString("address", c.address)
            .putString("user", c.user)
            .putString("pass", encrypt(c.password))
            .putString("did", c.deviceId?.let { encrypt(it) })
            .apply()
    }

    fun load(): Credentials? {
        val address = prefs.getString("address", null) ?: return null
        val user = prefs.getString("user", null) ?: return null
        val pass = decrypt(prefs.getString("pass", null)) ?: return null
        return Credentials(address, user, pass, decrypt(prefs.getString("did", null)))
    }

    fun lastAddress(): String = prefs.getString("address", "") ?: ""
    fun lastUser(): String = prefs.getString("user", "") ?: ""

    /** Signs out but keeps address/user so the form is pre-filled. */
    fun clearPassword() {
        prefs.edit().remove("pass").apply()
    }

    companion object { private const val ALIAS = "photoshelf_credentials" }
}

/** Plain app preferences, exposed as StateFlows so the UI reacts to changes. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("photoshelf_settings", Context.MODE_PRIVATE)

    private val _root = MutableStateFlow(p.getString("root", null))
    val root: StateFlow<String?> = _root
    private val _includeVideos = MutableStateFlow(p.getBoolean("videos", true))
    val includeVideos: StateFlow<Boolean> = _includeVideos
    private val _fullRes = MutableStateFlow(p.getBoolean("fullres", true))
    val fullRes: StateFlow<Boolean> = _fullRes
    private val _exifDates = MutableStateFlow(p.getBoolean("exif", true))
    val exifDates: StateFlow<Boolean> = _exifDates
    private val _exifOnMobile = MutableStateFlow(p.getBoolean("exif_mobile", false))
    val exifOnMobile: StateFlow<Boolean> = _exifOnMobile
    private val _columns = MutableStateFlow(p.getInt("columns", 4))
    val columns: StateFlow<Int> = _columns
    private val _grouping = MutableStateFlow(enumOr(p.getString("grouping", null), Grouping.MONTH))
    val grouping: StateFlow<Grouping> = _grouping
    private val _sort = MutableStateFlow(enumOr(p.getString("sort", null), SortOrder.NEWEST))
    val sort: StateFlow<SortOrder> = _sort

    var lastBase: String?
        get() = p.getString("last_base", null)
        set(v) { p.edit().putString("last_base", v).apply() }

    var lastIndexed: Long
        get() = p.getLong("last_indexed", 0L)
        set(v) { p.edit().putLong("last_indexed", v).apply() }

    fun setRoot(v: String?) { _root.value = v; p.edit().putString("root", v).apply() }
    fun setIncludeVideos(v: Boolean) { _includeVideos.value = v; p.edit().putBoolean("videos", v).apply() }
    fun setFullRes(v: Boolean) { _fullRes.value = v; p.edit().putBoolean("fullres", v).apply() }
    fun setExifDates(v: Boolean) { _exifDates.value = v; p.edit().putBoolean("exif", v).apply() }
    fun setExifOnMobile(v: Boolean) { _exifOnMobile.value = v; p.edit().putBoolean("exif_mobile", v).apply() }
    fun setColumns(v: Int) { _columns.value = v; p.edit().putInt("columns", v).apply() }
    fun setGrouping(v: Grouping) { _grouping.value = v; p.edit().putString("grouping", v.name).apply() }
    fun setSort(v: SortOrder) { _sort.value = v; p.edit().putString("sort", v.name).apply() }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T {
        if (name == null) return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }
}
