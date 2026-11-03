package com.lucento.app.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucento.app.core.banks.CardLogo
import com.lucento.app.core.banks.Institution
import com.lucento.app.core.banks.InstitutionArt
import com.lucento.app.core.banks.InstitutionRegistry
import com.lucento.app.core.banks.Monogram
import com.lucento.app.core.designsystem.theme.Sizes
import com.lucento.app.domain.Wallet

/**
 * The bundled institution registry, provided once at the app root. Starts empty while it loads, so a wallet briefly
 * shows its type tile before its institution — never a wrong institution.
 */
val LocalInstitutions = staticCompositionLocalOf { InstitutionRegistry.EMPTY }

/** The institution a wallet is linked to, if the bundled registry knows it. */
@Composable
fun Wallet.institution(): Institution? = LocalInstitutions.current[institutionId]

/** "#RRGGBB" from the registry. Registry colours are validated when it is generated; anything else draws as graphite. */
fun registryColor(hex: String?): Color {
    val digits = hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16) ?: return Graphite
    return Color(0xFF000000 or digits)
}

private val Graphite = Color(0xFF5E687D)

/** Navy plate for white logos on light faces (design system: Midnight Navy). */
internal val LogoNavy = Color(0xFF17233F)

/**
 * A wallet's leading tile: its institution's square icon when it has one, otherwise the wallet-type tile. Decorative —
 * the row beside it names the wallet and its institution.
 */
@Composable
fun WalletIdentityTile(wallet: Wallet, modifier: Modifier = Modifier, size: Dp = Sizes.iconTile) {
    val institution = wallet.institution()
    if (institution != null) InstitutionAvatar(institution, modifier, size) else WalletTypeTile(wallet.type, modifier, size)
}

/**
 * An institution's square icon (lists, pickers, rows): rounded square with radius 25% of [size], never a circle.
 * Official app icons fill the square with a hairline edge; vector marks sit on their tile; otherwise a monogram.
 */
@Composable
fun InstitutionAvatar(institution: Institution, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val shape = RoundedCornerShape(size * 0.25f)
    val spec = institution.icon
    val vector = InstitutionArt.vectorIcons[institution.id]
    val bitmap = InstitutionArt.bitmapIcons[institution.id]
    when {
        spec.kind == "vector" && vector != null -> Box(
            modifier
                .size(size)
                .clip(shape)
                .background(registryColor(spec.tileBg ?: "#FFFFFF"))
                .then(spec.tileBorder?.let { Modifier.border(1.dp, registryColor(it), shape) } ?: Modifier)
                .padding(maxOf(2.dp, size * (spec.paddingPct.coerceIn(0, 30) / 100f))),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(vector), contentDescription = null, contentScale = ContentScale.Fit)
        }
        spec.kind == "bitmap" && bitmap != null -> {
            val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Image(
                painterResource(bitmap),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .border(1.dp, if (dark) Color.White.copy(alpha = 0.12f) else Color(0xFF0F172A).copy(alpha = 0.08f), shape),
            )
        }
        else -> MonogramTile(spec.monogram ?: institution.card.monogram, size, shape, modifier)
    }
}

@Composable
internal fun MonogramTile(monogram: Monogram, size: Dp, shape: RoundedCornerShape, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(shape).background(registryColor(monogram.bg)), contentAlignment = Alignment.Center) {
        Text(
            monogram.text.take(if (size < 28.dp) 1 else 2),
            color = registryColor(monogram.fg),
            fontWeight = FontWeight.ExtraBold,
            fontSize = when {
                size < 28.dp -> 10.sp
                size < 36.dp -> 12.sp
                else -> 14.sp
            },
            maxLines = 1,
        )
    }
}

/** True when the card mark is a wide wordmark that already spells the name; square marks and monograms get the name beside them. */
val Institution.markSpellsName: Boolean get() = card.logo?.slot == "wide" && InstitutionArt.logos[id] != null

/** The institution's mark on a wallet card face: its logo (on a plate where the design says so, never recoloured) or its monogram. */
@Composable
fun InstitutionCardMark(institution: Institution, modifier: Modifier = Modifier) {
    val logo: CardLogo? = institution.card.logo
    val res = InstitutionArt.logos[institution.id]
    if (logo == null || res == null) {
        MonogramTile(institution.card.monogram, 36.dp, RoundedCornerShape(10.dp), modifier)
        return
    }
    val wide = logo.slot == "wide"
    val plate = when (logo.plate) {
        "light" -> if (logo.white) LogoNavy else Color.White // a white wordmark is never put on a white plate
        "dark" -> LogoNavy
        else -> null
    }
    val tint = logo.tint?.let { ColorFilter.tint(registryColor(it)) }
    val image = @Composable { m: Modifier ->
        Image(painterResource(res), contentDescription = null, contentScale = ContentScale.Fit, colorFilter = tint, modifier = m)
    }
    val markModifier = if (wide) Modifier.heightIn(max = 22.dp).widthIn(max = 180.dp) else Modifier.size(30.dp)
    if (plate != null) {
        Box(
            modifier.clip(RoundedCornerShape(8.dp)).background(plate)
                .padding(horizontal = if (wide) 8.dp else 4.dp, vertical = if (wide) 5.dp else 4.dp),
        ) { image(markModifier) }
    } else {
        image(modifier.then(markModifier))
    }
}
