package com.focuslock.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.focuslock.data.model.YouTubeFilterConfig
import com.focuslock.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeStudyScreen(
    filterConfig: YouTubeFilterConfig = YouTubeFilterConfig(),
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("Calculus 3Blue1Brown") }
    var currentUrl by remember { mutableStateOf("https://www.youtube.com/results?search_query=calculus+3blue1brown") }

    val studyChannels = listOf(
        "3Blue1Brown" to "https://www.youtube.com/c/3blue1brown/videos",
        "MIT OCW" to "https://www.youtube.com/c/mitocw/videos",
        "Fireship" to "https://www.youtube.com/c/Fireship/videos",
        "Khan Academy" to "https://www.youtube.com/c/khanacademy/videos",
        "Google Devs" to "https://www.youtube.com/c/GoogleDevelopers/videos"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "YouTube Study Mode",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentEmerald.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SHORTS & COMMENTS STRIPPED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    color = AccentEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSlateBg,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkSlateBg
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search & Channel Shortcuts
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search academic lectures or topics...", fontSize = 12.sp) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val encoded = java.net.URLEncoder.encode(searchQuery, "UTF-8")
                            currentUrl = "https://www.youtube.com/results?search_query=$encoded"
                        }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = ElectricBlue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = BorderHighlight
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(studyChannels) { (name, url) ->
                        Button(
                            onClick = { currentUrl = url },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardBg),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, BorderHighlight, RoundedCornerShape(8.dp))
                        ) {
                            Text(text = name, fontSize = 11.sp, color = ElectricBlue)
                        }
                    }
                }
            }

            // Distraction-Free Embedded YouTube Browser
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            // Study-only navigation: search, watch pages, channels, playlists. No home feed, no Shorts.
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return true
                                val host = uri.host ?: return true
                                val path = uri.path ?: "/"
                                if (host == "consent.youtube.com") return false
                                val onYouTube = host == "youtube.com" || host.endsWith(".youtube.com")
                                val allowedPath = path.startsWith("/results") || path.startsWith("/watch") ||
                                    path.startsWith("/@") || path.startsWith("/c/") ||
                                    path.startsWith("/channel/") || path.startsWith("/playlist")
                                return !(onYouTube && allowedPath && !path.startsWith("/shorts"))
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Inject aggressive CSS to strip Shorts, comments, and recommendations
                                val cssInjection = """
                                    javascript:(function() {
                                        var css = 'ytd-reel-shelf-renderer, #shorts-container, ytd-shorts, #comments, ytd-comments, #related, ytd-watch-next-secondary-results-renderer { display: none !important; }';
                                        var head = document.head || document.getElementsByTagName('head')[0];
                                        var style = document.createElement('style');
                                        style.type = 'text/css';
                                        style.appendChild(document.createTextNode(css));
                                        head.appendChild(style);
                                    })()
                                """.trimIndent()
                                view?.loadUrl(cssInjection)
                            }
                        }
                        loadUrl(currentUrl)
                    }
                },
                update = { webView ->
                    if (webView.url != currentUrl) {
                        webView.loadUrl(currentUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
