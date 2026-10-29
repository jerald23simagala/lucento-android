package com.lucento.app.feature.wallets

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/** QA R6-10: at 2.0x on 360dp the preview card cut its text to "You…" and "Curren". */
class WalletPreviewLinesTest {
    @Test fun preview_text_wraps_instead_of_clipping() {
        assertTrue(WalletPreviewLines.NAME > 1)
        assertTrue(WalletPreviewLines.CURRENCY > 1)
        assertTrue(WalletPreviewLines.LABEL > 1)
    }

    @Test fun type_mark_leaves_most_of_a_360dp_card_to_the_text() {
        val cardContent = 360.dp - 20.dp * 2 - 24.dp * 2
        assertTrue(WalletPreviewLines.TYPE_MARK_MAX < cardContent / 3)
    }
}
