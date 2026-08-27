package com.agentos.shell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.View
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

@Composable
internal fun AgentAvatarView(
    avatar: AgentAvatar,
    expression: AvatarExpression,
    modifier: Modifier = Modifier,
    performance: AvatarPerformance = AvatarPerformance(),
) {
    if (avatar.styleFamily != AvatarStyleFamily.SYSTEM) {
        NativeAgentAvatarView(avatar, expression, modifier, performance)
        return
    }
    var rendererFailed by remember(avatar.styleFamily) {
        mutableStateOf(WebView.getCurrentWebViewPackage() == null)
    }
    if (rendererFailed) {
        NativeAgentAvatarView(avatar, expression, modifier, performance)
        return
    }
    AndroidView(
        factory = { context -> AgentAvatarWebView(context) { rendererFailed = true } },
        update = { it.updateAvatar(avatar, expression, performance) },
        modifier = modifier.semantics {
            contentDescription = "${avatar.name}，可旋转的实时 3D 角色，${expression.label}表情"
        },
    )
}

@SuppressLint("SetJavaScriptEnabled")
private class AgentAvatarWebView(
    context: Context,
    private val onRendererFailure: () -> Unit,
) : WebView(context) {
    private var ready = false
    private var failed = false
    private var pendingCommand: AvatarRenderCommand? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            domStorageEnabled = false
            databaseEnabled = false
            allowContentAccess = false
            allowFileAccess = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
        }
        webViewClient = AvatarContentClient(context, ::fail)
        webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                when {
                    title == READY_TITLE -> {
                        ready = true
                        dispatchPending()
                    }
                    title?.startsWith(ERROR_TITLE) == true -> fail()
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?,
            ) = false
        }
        loadUrl(ENTRY_URL)
    }

    fun updateAvatar(
        avatar: AgentAvatar,
        expression: AvatarExpression,
        performance: AvatarPerformance,
    ) {
        pendingCommand = AvatarRenderCommand.from(avatar, expression, performance)
        dispatchPending()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (ready) evaluateJavascript(
            "window.AgentOSAvatar.setActive(${visibility == VISIBLE})",
            null,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onResume()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }

    private fun dispatchPending() {
        val command = pendingCommand ?: return
        if (!ready || failed) return
        evaluateJavascript(command.toJavascript(), null)
        pendingCommand = null
    }

    private fun fail() {
        if (failed) return
        failed = true
        post { onRendererFailure() }
    }

    private companion object {
        const val ENTRY_URL = "https://avatar.agentos.local/index.html"
        const val READY_TITLE = "AGENTOS_AVATAR_READY"
        const val ERROR_TITLE = "AGENTOS_AVATAR_ERROR"
    }
}

private class AvatarContentClient(
    private val context: Context,
    private val onFailure: () -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
        request.url.toString() != ENTRY_URL

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse =
        content(request.url)

    override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError?) {
        if (request.isForMainFrame) onFailure()
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError?) {
        handler.cancel()
        onFailure()
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.destroy()
        onFailure()
        return true
    }

    private fun content(uri: Uri): WebResourceResponse {
        if (uri.scheme != "https" || uri.host != LOCAL_HOST) return notFound()
        // ponytail: Keep this fixed route table until the renderer needs enough assets to justify WebViewAssetLoader.
        return when (uri.path) {
            "/", "/index.html" -> asset("avatar/index.html", "text/html")
            "/runtime.js" -> asset("avatar/runtime.js", "application/javascript")
            "/shaders/thought_field_fragment.glsl" -> raw(R.raw.thought_field_fragment)
            "/shaders/thought_field_surface_vertex.glsl" -> raw(R.raw.thought_field_surface_vertex)
            "/shaders/thought_field_part_vertex.glsl" -> raw(R.raw.thought_field_part_vertex)
            "/shaders/thought_field_glass_fragment.glsl" -> raw(R.raw.thought_field_glass_fragment)
            "/shaders/tf_shared.glsl" -> raw(R.raw.tf_shared)
            "/shaders/tf_strand_vertex.glsl" -> raw(R.raw.tf_strand_vertex)
            "/shaders/tf_strand_fragment.glsl" -> raw(R.raw.tf_strand_fragment)
            "/shaders/tf_spark_vertex.glsl" -> raw(R.raw.tf_spark_vertex)
            "/shaders/tf_spark_fragment.glsl" -> raw(R.raw.tf_spark_fragment)
            "/shaders/tf_hand_vertex.glsl" -> raw(R.raw.tf_hand_vertex)
            "/shaders/tf_bokeh_vertex.glsl" -> raw(R.raw.tf_bokeh_vertex)
            "/shaders/tf_bokeh_fragment.glsl" -> raw(R.raw.tf_bokeh_fragment)
            "/shaders/tf_constellation_vertex.glsl" -> raw(R.raw.tf_constellation_vertex)
            "/shaders/tf_constellation_fragment.glsl" -> raw(R.raw.tf_constellation_fragment)
            "/shaders/tf_flare_fragment.glsl" -> raw(R.raw.tf_flare_fragment)
            "/shaders/tf_bright_fragment.glsl" -> raw(R.raw.tf_bright_fragment)
            "/shaders/tf_blur_fragment.glsl" -> raw(R.raw.tf_blur_fragment)
            "/shaders/tf_composite_fragment.glsl" -> raw(R.raw.tf_composite_fragment)
            "/shaders/tf_glass_vertex.glsl" -> raw(R.raw.tf_glass_vertex)
            "/shaders/tf_glass_fragment.glsl" -> raw(R.raw.tf_glass_fragment)
            else -> notFound()
        }
    }

    private fun asset(path: String, mimeType: String) = WebResourceResponse(
        mimeType,
        "UTF-8",
        200,
        "OK",
        RESPONSE_HEADERS,
        context.assets.open(path),
    )

    private fun raw(resourceId: Int) = WebResourceResponse(
        "text/plain",
        "UTF-8",
        200,
        "OK",
        RESPONSE_HEADERS,
        context.resources.openRawResource(resourceId),
    )

    private fun notFound() = WebResourceResponse(
        "text/plain",
        "UTF-8",
        404,
        "Not Found",
        RESPONSE_HEADERS,
        ByteArrayInputStream("Not found".toByteArray()),
    )

    private companion object {
        const val ENTRY_URL = "https://avatar.agentos.local/index.html"
        const val LOCAL_HOST = "avatar.agentos.local"
        val RESPONSE_HEADERS = mapOf(
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
        )
    }
}
