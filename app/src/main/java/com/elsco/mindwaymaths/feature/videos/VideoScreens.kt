package com.elsco.mindwaymaths.feature.videos

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.elsco.mindwaymaths.BuildConfig
import com.elsco.mindwaymaths.domain.model.VideoLesson
import com.elsco.mindwaymaths.feature.*
import org.json.JSONObject

@Composable fun VideoListScreen(vm: AppViewModel, open: (String) -> Unit) {
    val videos by vm.videos.collectAsStateWithLifecycle()
    val loading by vm.loadingContent.collectAsStateWithLifecycle()
    val errors by vm.contentErrors.collectAsStateWithLifecycle()
    val more by vm.moreAvailable.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh("videos") }
    Page {
        item { Heading("Understand the why.", "Clear explanations for the concepts that count.") }
        item { ContentStatus("videos" in loading, videos.isEmpty(), "videos" in errors) { vm.refresh("videos") } }
        items(videos, key = { it.id }) { video -> ActionCard(video.title, "${video.topicId.label()} · ${video.duration / 60} min", Icons.Rounded.PlayCircle) { open(video.id) } }
        if ("videos" in more) item { TextButton({ vm.refresh("videos", true) }) { Text("Load more") } }
    }
}
@Composable fun VideoPlayerScreen(video: VideoLesson?, practice: (String) -> Unit, pdfs: () -> Unit) {
    if (video == null) { Notice("This lesson is no longer available. Return to the lessons list and refresh."); return }
    var error by remember { mutableStateOf<String?>(null) }
    var ended by remember { mutableStateOf(false) }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    val id = video.youtubeVideoId
    val origin = BuildConfig.YOUTUBE_ORIGIN
    if (!id.matches(Regex("[A-Za-z0-9_-]{11}")) || video.visibility != "unlisted" || !origin.startsWith("https://") || origin.contains(".invalid")) {
        Notice("This lesson is not configured for embedded playback yet."); return
    }
    Page {
        item { Heading(video.title, video.topicId.label()) }
        item { key(id, retry) { YouTubePlayer(id, origin, onError = { error = it }, onEnded = { ended = true }) } }
        error?.let { item { Notice(it) { error = null; retry++ } } }
        item { Text(video.description) }
        item { ActionCard(if (ended) "Now put it into practice" else "Related practice", "Turn this explanation into a stronger skill") { practice(video.topicId) } }
        item { ActionCard("Related study material", "Review the key concepts") { pdfs() } }
    }
}
@Composable private fun YouTubePlayer(id: String, origin: String, onError: (String) -> Unit, onEnded: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    val webView = remember(id) { WebView(context) }
    val currentError by rememberUpdatedState(onError)
    val currentEnded by rememberUpdatedState(onEnded)
    DisposableEffect(webView, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_PAUSE -> { webView.evaluateJavascript("if(window.player){player.pauseVideo();}", null); webView.onPause() }
            Lifecycle.Event.ON_RESUME -> webView.onResume()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); customCallback?.onCustomViewHidden(); webView.stopLoading(); webView.loadUrl("about:blank"); webView.removeAllViews(); webView.destroy() }
    }
    AndroidView(factory = {
        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = true
            settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Keep standard player links within trusted HTTPS YouTube navigation only.
                    val host = request.url.host.orEmpty()
                    return request.url.scheme != "https" || !(host == "www.youtube.com" || host == "www.youtube-nocookie.com" || host == Uri.parse(origin).host)
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) currentError("Video couldn’t connect. Check your internet connection and retry.")
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView, title: String) {
                    when {
                        title == "mindway-ended" -> currentEnded()
                        title.startsWith("mindway-error:") -> currentError(when (title.substringAfter(':')) {
                            "100" -> "This video has been removed or made private."
                            "101", "150" -> "The publisher has disabled embedded playback for this lesson."
                            else -> "Video playback is unavailable. Please retry later."
                        })
                    }
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) { customView = view; customCallback = callback }
                override fun onHideCustomView() { customView = null; customCallback?.onCustomViewHidden(); customCallback = null }
            }
            val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>html,body,#player{margin:0;width:100%;height:100%;background:#000}</style></head><body><div id="player"></div><script src="https://www.youtube.com/iframe_api"></script><script>var player;function onYouTubeIframeAPIReady(){player=new YT.Player('player',{host:'https://www.youtube-nocookie.com',videoId:${JSONObject.quote(id)},playerVars:{controls:1,playsinline:1,origin:${JSONObject.quote(origin)}},events:{onError:function(e){document.title='mindway-error:'+e.data},onStateChange:function(e){if(e.data===0)document.title='mindway-ended'}}});}</script></body></html>"""
            loadDataWithBaseURL("$origin/", html, "text/html", "UTF-8", null)
        }
    }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
    if (customView != null) Dialog(onDismissRequest = { customView = null; customCallback?.onCustomViewHidden(); customCallback = null },
        properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        AndroidView(factory = { requireNotNull(customView) }, modifier = Modifier.fillMaxSize())
    }
}
