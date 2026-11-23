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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.core.net.toUri
import com.lucento.app.core.LucentoLinks
import com.lucento.app.core.designsystem.component.Hairline
import com.lucento.app.core.designsystem.component.LucentoBottomSheet
import com.lucento.app.core.designsystem.component.NavRow
import com.lucento.app.core.designsystem.component.SheetHeader
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.core.designsystem.theme.Spacing

/**
 * Lucento's social channels (owner, 2026-11-23): Facebook, Instagram, TikTok and YouTube, all @lucentoapp, each opened in
 * its own app or the browser on a tap. The app itself sends nothing and shows no feed: these are links, like the website
 * and the Community group above them in the profile menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun browse(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
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
                    onClick = { browse(channel.url) },
                )
                if (index < LucentoLinks.FOLLOW.lastIndex) Hairline(inset = inset)
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
