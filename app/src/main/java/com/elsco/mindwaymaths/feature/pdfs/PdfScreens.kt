package com.elsco.mindwaymaths.feature.pdfs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elsco.mindwaymaths.R
import com.elsco.mindwaymaths.feature.*

@Composable fun PdfListScreen(vm: AppViewModel, open: (String) -> Unit) {
    val pdfs by vm.pdfs.collectAsStateWithLifecycle()
    val loading by vm.loadingContent.collectAsStateWithLifecycle()
    val errors by vm.contentErrors.collectAsStateWithLifecycle()
    val more by vm.moreAvailable.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh("pdfs") }
    Page {
        item { Heading("Your revision shelf.", "Focused notes. Key formulas. A clearer way forward.") }
        item { ContentStatus("pdfs" in loading, pdfs.isEmpty(), "pdfs" in errors) { vm.refresh("pdfs") } }
        items(pdfs, key = { it.id }) { pdf -> ActionCard(pdf.title, "${pdf.topic.label()} · ${pdf.pageCount} pages", Icons.Rounded.Description) { open(pdf.id) } }
        if ("pdfs" in more) item { TextButton({ vm.refresh("pdfs", true) }) { Text("Load more") } }
    }
}
@Composable fun PdfViewerScreen(vm: PdfViewModel, title: String) {
    val bitmap by vm.bitmap.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val searchResult by vm.searchResult.collectAsStateWithLifecycle()
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var query by rememberSaveable { mutableStateOf("") }
    var full by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(page) { zoom = 1f; offset = Offset.Zero }
    val image: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().clipToBounds().background(Color(0xFFDEE2DB)).pointerInput(page) {
            detectTransformGestures { _, pan, change, _ ->
                zoom = (zoom * change).coerceIn(1f, 4f)
                val maxX = size.width * (zoom - 1) / 2
                val maxY = size.height * (zoom - 1) / 2
                offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
            }
        }, contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.page_position, page + 1, count),
                Modifier.fillMaxSize().graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit) }
            if (busy) CircularProgressIndicator()
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it.take(100) }, Modifier.weight(1f), label = { Text("Search document") }, singleLine = true)
            TextButton({ vm.search(query) }, enabled = !busy && bitmap != null) { Text("Find") }
        }
        searchResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        error?.let { Notice(it, vm::open) }
        Box(Modifier.weight(1f)) { image() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton({ zoom = 1f; offset = Offset.Zero }) { Text("Fit width") }
            TextButton({ zoom = (zoom + .5f).coerceAtMost(4f) }) { Text("Zoom +") }
            TextButton({ full = true }) { Text("Fullscreen") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ vm.go(page - 1) }, enabled = page > 0 && !busy) { Text("Previous") }
            Text(if (count == 0) "—" else "${page + 1} / $count")
            TextButton({ vm.go(page + 1) }, enabled = page < count - 1 && !busy) { Text("Next") }
        }
    }
    if (full) Dialog({ full = false }, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize()) { Column { Box(Modifier.weight(1f)) { image() }; TextButton({ full = false }) { Text("Exit fullscreen") } } }
    }
}
