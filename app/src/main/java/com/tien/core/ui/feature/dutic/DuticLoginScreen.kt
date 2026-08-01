package com.tien.core.ui.feature.dutic

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.tien.core.ui.designsystem.theme.TienTextStyles
import com.tien.core.ui.designsystem.theme.TienTheme
import com.tien.dutic.auth.DuticLogin
import com.tien.dutic.auth.LoginCapture

/**
 * Signing in to the aula virtual.
 *
 * ### Why a WebView and not an HTTP form post
 * The UNSA logs in through Google SSO. That flow is several redirects across two
 * domains, sets cookies on both, and finishes with JavaScript — there is no
 * request to replay. A browser is the only thing that can complete it, which is
 * exactly why the CLI drives Playwright. On a phone, the WebView *is* the
 * browser.
 *
 * The user's Google credentials are typed into Google's own page inside that
 * WebView. This app never sees them; what it keeps is the resulting Moodle
 * session cookie.
 *
 * All the logic — is this URL the finish line, which cookie matters, is the
 * capture complete — lives in `DuticLogin` in the :dutic module, as pure
 * functions with tests. This file only drives the browser.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DuticLoginScreen(
    loginUrl: String,
    onCaptured: (LoginCapture) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf(INITIAL_STATUS) }

    // The callback can change between recompositions; the WebViewClient below
    // outlives them, so it must read the current one rather than capture the
    // first.
    val currentOnCaptured by rememberUpdatedState(onCaptured)

    // Guards against firing twice: Moodle's dashboard triggers onPageFinished
    // more than once as its own scripts settle.
    var captured by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Text(
            text = statusText,
            style = TienTextStyles.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(TienTheme.spacing.base)
        )

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        // Google's sign-in is a JavaScript application; without
                        // this the page never renders.
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        // A desktop Chrome UA, matching the CLI. Google answers
                        // 403 to WebView User-Agents, so the default would fail
                        // before the user could type anything.
                        settings.userAgentString = CHROME_USER_AGENT

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView,
                                url: String,
                                favicon: Bitmap?
                            ) {
                                isLoading = true
                                statusText = when {
                                    DuticLogin.isLoginUrl(url) -> LOGIN_STATUS
                                    url.contains("google") -> GOOGLE_STATUS
                                    else -> WORKING_STATUS
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                isLoading = false
                                if (captured || !DuticLogin.isSignedInUrl(url)) return

                                // Landing on the dashboard means Moodle issued a
                                // session. The sesskey only exists in the page's
                                // own JavaScript, so it has to be read from
                                // there — see DuticLogin.SESSKEY_EXTRACTION_JS.
                                view.evaluateJavascript(
                                    DuticLogin.SESSKEY_EXTRACTION_JS
                                ) { rawSesskey ->
                                    val cookies = CookieManager.getInstance().getCookie(url)
                                    captured = true
                                    statusText = DONE_STATUS
                                    currentOnCaptured(
                                        LoginCapture(
                                            cookieHeader = cookies,
                                            sesskey = rawSesskey,
                                            currentUrl = url
                                        )
                                    )
                                }
                            }
                        }

                        loadUrl(loginUrl)
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // The session cookie is already persisted by the time this screen
            // closes; flushing makes sure the WebView's own copy reached disk
            // too, so a cold start does not force the SSO round trip again.
            CookieManager.getInstance().flush()
        }
    }
}

private const val CHROME_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val INITIAL_STATUS = "Abriendo el aula virtual…"
private const val LOGIN_STATUS = "Toca «Ingresar con Correo UNSA»"
private const val GOOGLE_STATUS = "Elige tu cuenta de la UNSA"
private const val WORKING_STATUS = "Conectando…"
private const val DONE_STATUS = "Sesión capturada"
