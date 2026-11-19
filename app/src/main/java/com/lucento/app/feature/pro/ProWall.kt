package com.lucento.app.feature.pro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.Composable
import com.lucento.app.core.designsystem.component.ConfirmDialog
import com.lucento.app.domain.entitlement.Access
import com.lucento.app.domain.entitlement.Limit
import com.lucento.app.domain.entitlement.ProFeature

/**
 * A Pro wall met at a tap: what isn't on the plan and, for a count, the numbers. Since the tester round of 2026-11-18
 * (owner: "anyone hitting a Pro wall should be prompted with a dialog to be directed to the Lucento Plan page") every
 * wall is one [ProWallDialog] at the tap that would cross it, never an inline note. [subject] names the thing when the
 * title needs it ("Grove", for a theme).
 */
data class ProWall(
    val feature: ProFeature,
    val used: Int? = null,
    val limit: Limit? = null,
    val subject: String? = null,
    /** Another count full at the same tap (Add wallet with money wallets and cards or loans both full): one dialog, both. */
    val alsoFull: ProWall? = null,
) {
    companion object {
        /**
         * The wall [access] means, or null when it allows, or when no plan would lift it (20 goals is the most on any plan:
         * that one keeps its plain note, since a dialog could only offer what doesn't help).
         */
        fun of(access: Access, subject: String? = null): ProWall? = when (access) {
            Access.Allowed -> null
            is Access.Locked -> ProWall(access.feature, subject = subject)
            is Access.LimitReached ->
                if (ProCopy.upgradeHelps(access.feature, access.limit)) ProWall(access.feature, access.used, access.limit, subject) else null
        }
    }
}

/**
 * The one Pro dialog: what the Free plan includes, what Pro adds, then **See Lucento Plan** (the plan page, naming the
 * feature) or **Not now**. Shown where the wall was met; [open] replaces the plan page's opening when a sheet must close
 * first. It replaced the inline lock notes (ProNote) everywhere.
 */
@Composable
fun ProWallDialog(wall: ProWall, onDismiss: () -> Unit, open: ((ProFeature?) -> Unit)? = null) {
    val openPro = LocalOpenPro.current
    // The words follow the plan held: Pro Lifetime without Lucento Cloud hears about Lucento Cloud.
    val plan = LocalEntitlements.current.displayPlan(System.currentTimeMillis())
    ConfirmDialog(
        title = ProCopy.wallTitle(wall, plan),
        text = ProCopy.wallBody(wall, plan),
        confirmLabel = ProCopy.WALL_SEE_PLAN,
        dismissLabel = ProCopy.WALL_NOT_NOW,
        icon = Icons.Outlined.WorkspacePremium,
        onConfirm = {
            onDismiss()
            (open ?: openPro)(wall.feature)
        },
        onDismiss = onDismiss,
    )
}
