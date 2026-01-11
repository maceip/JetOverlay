package com.yazan.jetoverlay.api

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

data class OverlayConfig(
    val id: String,
    val type: String = id, // Defaults to id if not specified
    val initialX: Int = 0,
    val initialY: Int = 0,
    val width: Dp = Dp.Unspecified,
    val height: Dp = Dp.Unspecified
)

fun interface OverlayContentFactory {
    @Composable
    fun Content(
        modifier: Modifier,
        id: String,
        payload: Any?,
    )
}