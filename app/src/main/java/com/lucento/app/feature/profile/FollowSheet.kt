package com.lucento.app.feature.profile

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import com.lucento.app.core.LucentoLinks
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * Lucento's social channels (owner, 2026-11-23): the Facebook Page, Instagram and TikTok (@lucentoapp) and the YouTube
 * channel, each opened in its own app or the browser on a tap. The app itself sends nothing and shows no feed: these are
 * links, like the website and the Community group above them in the profile menu. When nothing on the phone can open
 * one, the sheet says so and gives the address.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf<LucentoLinks.Channel?>(null) }
    fun browse(channel: LucentoLinks.Channel) {
        failed = if (runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, channel.url.toUri())) }.isSuccess) null else channel
    }
    LucentoBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter)
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
        ) {
            SheetHeader(
                title = "Follow Lucento",
                subtitle = "Tips, updates and short videos from Lucento.",
                icon = Icons.Outlined.Campaign,
            )
            val inset = Sizes.iconTile + Spacing.md + Spacing.xs
            LucentoLinks.FOLLOW.forEachIndexed { index, channel ->
                NavRow(
                    icon = ImageVector.vectorResource(channel.icon),
                    title = channel.name,
                    summary = channel.subtitle,
                    // One colour, the text's ink, on a neutral tile: the platforms allow their marks in plain black or
                    // white, not in another brand's colour (owner, 2026-11-23).
                    tone = com.lucento.app.core.designsystem.theme.TonePair(
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurface,
                    ),
                    onClick = { browse(channel) },
                    external = true,
                    onClickLabel = "Open in ${channel.name}",
                )
                if (index < LucentoLinks.FOLLOW.lastIndex) Hairline(inset = inset)
            }
            failed?.let { channel ->
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Couldn’t open ${channel.name} on this phone. Its address is ${displayAddress(channel.url)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                "Each opens in its own app, or in your browser. Lucento sends nothing to them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "instagram.com/lucentoapp": a link as a person would type it. */
internal fun displayAddress(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
