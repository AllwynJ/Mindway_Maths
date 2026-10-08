package com.elsco.mindwaymaths.security

import android.content.Context
import android.os.ParcelFileDescriptor
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.elsco.mindwaymaths.data.local.PreferencesStore
import com.elsco.mindwaymaths.domain.repository.AuthRepository
import com.elsco.mindwaymaths.network.NetworkClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton class PrivatePdfCache @Inject constructor(
    @ApplicationContext private val context: Context, private val network: NetworkClient,
    private val auth: AuthRepository, private val preferences: PreferencesStore,
) {
    private val mutex = Mutex()
    private val directory get() = File(context.noBackupFilesDir, "pdfs").apply { mkdirs() }
    private val maxFileBytes = 40L * 1024 * 1024
    private val maxCacheBytes = 120L * 1024 * 1024
    private fun key(): java.security.Key {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey("mindway_pdf_v1", null) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("mindway_pdf_v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    suspend fun open(id: String): ParcelFileDescriptor = withContext(Dispatchers.IO) { mutex.withLock {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        val uid = requireNotNull(auth.uid)
        prune()
        val hash = MessageDigest.getInstance("SHA-256").digest("$uid:$id".toByteArray()).joinToString("") { "%02x".format(it) }
        val encrypted = File(directory, "$hash.enc")
        if (!encrypted.exists()) download(id, uid, encrypted)
        val temporary = File.createTempFile("render-", ".private", context.cacheDir)
        try {
            encrypted.inputStream().buffered().use { input ->
                val ivSize = input.read()
                require(ivSize == 12)
                val iv = ByteArray(ivSize); DataInputStream(input).readFully(iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
                CipherInputStream(input, cipher).use { decrypted -> temporary.outputStream().use { decrypted.copyTo(it) } }
            }
            check(auth.uid == uid)
            val descriptor = ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY)
            // PdfRenderer retains an open descriptor; the plaintext path is removed immediately.
            temporary.delete()
            descriptor
        } catch (e: Exception) { encrypted.delete(); throw e } finally { temporary.delete() }
    } }
    private suspend fun download(id: String, uid: String, target: File) {
        val response = network.pdfApi.open(id)
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw IOException(when (response.code()) {
                401 -> "Your session expired. Please sign in again."
                403 -> "This device or account could not be verified."
                404 -> "This study material is no longer available."
                429, 503 -> "Study material is temporarily unavailable. Please try again later."
                else -> "Study material could not be loaded."
            })
        }
        val body = response.body() ?: throw IOException("Empty document")
        val partial = File(target.parentFile, "${target.name}.partial")
        try {
            body.use {
                if (body.contentType()?.let { "${it.type}/${it.subtype}" } != "application/pdf" || body.contentLength() > maxFileBytes) throw IOException("Invalid document")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
                partial.outputStream().buffered().use { output ->
                    output.write(cipher.iv.size); output.write(cipher.iv)
                    CipherOutputStream(output, cipher).use { secured ->
                        body.byteStream().buffered().use { input ->
                            val header = ByteArray(5); DataInputStream(input).readFully(header)
                            if (!header.contentEquals("%PDF-".toByteArray())) throw IOException("Invalid document")
                            secured.write(header)
                            var total = 5L; val buffer = ByteArray(16 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer); if (read == -1) break
                                total += read; if (total > maxFileBytes) throw IOException("Document exceeds device limit")
                                secured.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
            check(auth.uid == uid)
            check(partial.renameTo(target))
        } finally { partial.delete() }
    }
    suspend fun prune() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - preferences.flow.first().pdfCacheDays * 86_400_000L
        directory.listFiles()?.filter { it.lastModified() < cutoff || it.extension == "partial" }?.forEach { it.delete() }
        val files = directory.listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()
        var size = 0L
        files.forEach { size += it.length(); if (size > maxCacheBytes) it.delete() }
        context.cacheDir.listFiles()?.filter { it.name.startsWith("render-") }?.forEach { it.delete() }
    }
    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock { directory.deleteRecursively(); Unit } }
}
