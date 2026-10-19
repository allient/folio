package com.allient.folio

import androidx.compose.foundation.Image
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import knotes.shared.generated.resources.Res
import knotes.shared.generated.resources.folio_icon_primary
import knotes.shared.generated.resources.folio_symbol
import knotes.shared.generated.resources.geist_bold
import knotes.shared.generated.resources.geist_medium
import knotes.shared.generated.resources.geist_regular
import knotes.shared.generated.resources.geist_semibold
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

/** Folio brand kit v1.0 (packages/brand): palette, type and marks. */
object FolioBrand {
    val Sage = Color(0xFF2F7A68)
    val Ink = Color(0xFF1E2B28)
    val Cream = Color(0xFFF6F1E6)
    val SageSoft = Color(0xFFE3EFEA)
    val FoldMint = Color(0xFFC7E3D8)
    /** Lighter cream for large app backgrounds, so white paper and cards still stand out. */
    val Paper = Color(0xFFFBF8F2)
    val Muted = Color(0xFF66716D)
    val Hairline = Color(0xFFE6E1D6)
}

/** Geist (SIL Open Font License, composeResources/files/geist-OFL.txt). */
@Composable
fun folioFontFamily(): FontFamily = FontFamily(
    Font(Res.font.geist_regular, FontWeight.Normal),
    Font(Res.font.geist_medium, FontWeight.Medium),
    Font(Res.font.geist_semibold, FontWeight.SemiBold),
    Font(Res.font.geist_bold, FontWeight.Bold),
)

@Composable
fun folioTypography(): Typography {
    val family = folioFontFamily()
    return remember(family) {
        val base = Typography()
        fun TextStyle.geist() = copy(fontFamily = family)
        Typography(
            displayLarge = base.displayLarge.geist(), displayMedium = base.displayMedium.geist(), displaySmall = base.displaySmall.geist(),
            headlineLarge = base.headlineLarge.geist(), headlineMedium = base.headlineMedium.geist(), headlineSmall = base.headlineSmall.geist(),
            titleLarge = base.titleLarge.geist(), titleMedium = base.titleMedium.geist(), titleSmall = base.titleSmall.geist(),
            bodyLarge = base.bodyLarge.geist(), bodyMedium = base.bodyMedium.geist(), bodySmall = base.bodySmall.geist(),
            labelLarge = base.labelLarge.geist(), labelMedium = base.labelMedium.geist(), labelSmall = base.labelSmall.geist(),
        )
    }
}

/** The «F.» symbol alone. Its box is 325:364. */
@Composable
fun FolioSymbol(modifier: Modifier = Modifier, color: Color = FolioBrand.Sage) =
    Image(painterResource(Res.drawable.folio_symbol), "Folio", modifier, colorFilter = ColorFilter.tint(color))

/** Primary app icon: sage F on cream paper with the folded corner. Use 64 dp or larger so the fold reads. */
@Composable
fun FolioIcon(modifier: Modifier = Modifier) = Image(painterResource(Res.drawable.folio_icon_primary), "Folio", modifier)
