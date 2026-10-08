package com.elsco.mindwaymaths.feature.pdfs

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import androidx.lifecycle.*
import androidx.navigation.toRoute
import com.elsco.mindwaymaths.navigation.PdfRoute
import com.elsco.mindwaymaths.security.PrivatePdfCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel class PdfViewModel @Inject constructor(private val savedState: SavedStateHandle, private val cache: PrivatePdfCache) : ViewModel() {
    private val id = savedState.toRoute<PdfRoute>().id
    val bitmap = MutableStateFlow<Bitmap?>(null)
    val page = MutableStateFlow(savedState.get<Int>("page") ?: 0)
    val count = MutableStateFlow(0)
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val searchResult = MutableStateFlow<String?>(null)
    private val mutex = Mutex()
    private var renderer: PdfRenderer? = null
    private var openJob: Job? = null
    init { open() }
    fun open() {
        if (openJob?.isActive == true) return
        openJob = viewModelScope.launch(Dispatchers.IO) {
            busy.value = true; error.value = null
            try {
                val fd = cache.open(id)
                val opened = try { PdfRenderer(fd) } catch (e: Exception) { fd.close(); throw e }
                mutex.withLock { renderer = opened; count.value = opened.pageCount }
                render(page.value)
                awaitCancellation()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = when (e.message) {
                "This study material is no longer available." -> e.message
                "Your session expired. Please sign in again." -> e.message
                else -> "Study material is temporarily unavailable. Check your connection and try again."
            } }
            finally {
                busy.value = false
                withContext(NonCancellable + Dispatchers.IO) { mutex.withLock { renderer?.close(); renderer = null } }
            }
        }
    }
    private suspend fun render(index: Int) = mutex.withLock {
        val reader = renderer ?: return@withLock
        if (reader.pageCount == 0) error("Empty PDF")
        busy.value = true
        try {
            val actual = index.coerceIn(0, reader.pageCount - 1)
            reader.openPage(actual).use { p ->
                val scale = minOf(2f, kotlin.math.sqrt(2_500_000f / (p.width.toFloat() * p.height)))
                val image = Bitmap.createBitmap((p.width * scale).toInt().coerceAtLeast(1), (p.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                image.eraseColor(Color.WHITE)
                p.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.value = image; page.value = actual
                withContext(Dispatchers.Main) { savedState["page"] = actual }
            }
        } finally { busy.value = false }
    }
    fun go(index: Int) { if (busy.value) return; viewModelScope.launch(Dispatchers.IO) {
        try { render(index) } catch (e: CancellationException) { throw e } catch (_: Exception) { error.value = "This page couldn’t be rendered." }
    } }
    fun search(query: String) {
        if (query.isBlank() || busy.value) return
        if (Build.VERSION.SDK_INT < 35) { searchResult.value = "Text search requires Android 15 or newer. Scanned PDFs may not contain searchable text."; return }
        viewModelScope.launch(Dispatchers.IO) {
            busy.value = true
            try {
                val found = mutex.withLock {
                    val reader = renderer ?: return@withLock -1
                    var match = -1
                    for (offset in 1..reader.pageCount) {
                        ensureActive()
                        val i = (page.value + offset) % reader.pageCount
                        val matches = reader.openPage(i).use { p -> p.textContents.any { it.text.contains(query, ignoreCase = true) } }
                        if (matches) { match = i; break }
                    }
                    match
                }
                if (found >= 0) { render(found); searchResult.value = "Match on page ${found + 1}" }
                else searchResult.value = "No text match. Scanned pages may not be searchable."
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { searchResult.value = "Text search isn’t available for this document." }
            finally { busy.value = false }
        }
    }
}
