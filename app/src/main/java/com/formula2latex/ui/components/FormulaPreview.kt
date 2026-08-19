package com.formula2latex.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import org.json.JSONObject

@Composable
// The callback is implemented below; lint currently misses it on this anonymous client.
@SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
fun FormulaPreview(
    latex: String,
    displayMode: Boolean,
    modifier: Modifier = Modifier,
    onRenderError: (String?) -> Unit = {},
) {
    var loaded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    fun render(view: WebView) {
        if (!loaded) return
        view.evaluateJavascript(
            "renderLatex(${JSONObject.quote(latex)}, ${if (displayMode) "true" else "false"})"
        ) { encoded ->
            val message = runCatching {
                if (encoded == "null") null
                else JSONObject("{\"v\":$encoded}").optString("v").takeIf { it.isNotBlank() }
            }.getOrNull()
            onRenderError(message)
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White),
        factory = { context ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            WebView(context).apply {
                setBackgroundColor(AndroidColor.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)
                            ?: WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        loaded = false
                        if (webView === view) webView = null
                        onRenderError("The local preview renderer stopped. The raw LaTeX is still available.")
                        view.destroy()
                        return true
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        loaded = true
                        render(view)
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/katex/index.html")
                webView = this
            }
        },
        update = { render(it) },
    )
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }
}
