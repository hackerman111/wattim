package io.ronesec.android.ui.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class WattimDimensions(
    val space4: Dp = 4.dp,
    val space8: Dp = 8.dp,
    val space12: Dp = 12.dp,
    val space16: Dp = 16.dp,
    val space24: Dp = 24.dp,
    val space32: Dp = 32.dp,

    val cardRadius: Dp = 4.dp,
    val buttonRadius: Dp = 4.dp,
    val inputRadius: Dp = 4.dp,
    val badgeRadius: Dp = 3.dp,

    val borderWidth: Dp = 1.dp,

    val cardPadding: Dp = 16.dp,
    val buttonPaddingHorizontal: Dp = 16.dp,
    val buttonPaddingVertical: Dp = 10.dp,
    val badgePaddingHorizontal: Dp = 6.dp,
    val badgePaddingVertical: Dp = 2.dp,

    val minTouchTarget: Dp = 48.dp
)
