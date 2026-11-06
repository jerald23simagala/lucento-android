package com.lucento.app.feature.sharedspace

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lucento.app.core.designsystem.component.EmptyState
import com.lucento.app.core.designsystem.component.InfoNote
import com.lucento.app.core.designsystem.component.LucentoTopBar
import com.lucento.app.core.designsystem.component.MascotMood
import com.lucento.app.core.designsystem.component.MascotScene
import com.lucento.app.core.designsystem.theme.Spacing
import com.lucento.app.domain.PeopleDirectory
import com.lucento.app.domain.SharedAccess

/**
 * Lucento Shared: what this phone may change, provided once at the root (LucentoRoot) so any screen can hide the
 * controls a Partner can't use. Solo outside a Shared Space. The database enforces the same rule on every request.
 */
val LocalSharedAccess = staticCompositionLocalOf { SharedAccess.Solo }

/** Lucento Shared: names for "Added by …" on transactions. Empty outside a Shared Space. */
val LocalPeople = staticCompositionLocalOf { PeopleDirectory() }

/** "Wallets are managed by Alex" — the one sentence that explains a control that isn't there for a Partner. */
fun ownerManages(access: SharedAccess, what: String): String =
    "${what.replaceFirstChar { it.uppercase() }} in this Shared Space are managed by ${access.ownerDisplay}. You can see them all, and add, edit or delete transactions."

/** A quiet note in place of the controls a Partner doesn't have. Nothing is shown outside a Shared Space. */
@Composable
fun OwnerManagesNote(what: String, modifier: Modifier = Modifier) {
    val access = LocalSharedAccess.current
    if (access.canManageStructure) return
    InfoNote(text = ownerManages(access, what), modifier = modifier, icon = Icons.Outlined.Group)
}

/**
 * A screen that only changes the structure (a schedule editor, category setup): a Partner sees why it isn't available
 * instead of a form whose saves would be refused.
 */
@Composable
fun OwnerOnly(title: String, what: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val access = LocalSharedAccess.current
    if (access.canManageStructure) {
        content()
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter),
    ) {
        LucentoTopBar(title = title, onBack = onBack)
        EmptyState(
            title = "${access.ownerDisplay} manages this",
            body = ownerManages(access, what),
            illustration = { MascotScene(mood = MascotMood.Curious, mascotSize = 96.dp) },
        )
    }
}
