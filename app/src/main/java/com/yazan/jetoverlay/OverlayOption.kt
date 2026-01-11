package com.yazan.jetoverlay

import androidx.compose.ui.graphics.Color

data class OverlayOption(val id: String, val name: String, val color: Color)

val options = listOf(
    OverlayOption("circle_red", "Red Circle", Color(0xFFFF5252)),
    OverlayOption("box_blue", "Blue Box", Color(0xFF448AFF)),
    OverlayOption("pill_green", "Green Pill", Color(0xFF69F0AE)),
    OverlayOption("card_purple", "Purple Card", Color(0xFFE040FB)),
    OverlayOption("badge_orange", "Orange Badge", Color(0xFFFFAB40)),
    OverlayOption("alert_teal", "Teal Alert", Color(0xFF18FFFF)),
)