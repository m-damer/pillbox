package com.pillbox.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The line icons drawn in the design (24 x 24, 2 wide round stroke), as vectors. Use them wherever the app needs an icon,
 * so every screen shows the same set. Colour comes from the tint given to Icon. Back and the chevron flip in right-to-left.
 */
object DesignIcons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"
    private fun box(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}H${x + w - r}a$r,$r 0 0 1 $r,${r}V${y + h - r}a$r,$r 0 0 1 ${-r},${r}H${x + r}a$r,$r 0 0 1 ${-r},${-r}V${y + r}a$r,$r 0 0 1 $r,${-r}z"

    private fun icon(name: String, path: String, mirror: Boolean = false, width: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).addPath(
            pathData = addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ).build()

    val Back by lazy { icon("Back", "M19 12H5M11 6l-6 6 6 6", mirror = true) }
    val Chevron by lazy { icon("Chevron", "M9 5l7 7-7 7", mirror = true, width = 2.2f) }
    val ChevronDown by lazy { icon("ChevronDown", "M5 9l7 7 7-7", width = 2.2f) }
    val Check by lazy { icon("Check", "M5 12.5l4.5 4.5L19 7.5", width = 2.6f) }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6L6 18", width = 2.6f) }
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14", width = 2.4f) }
    val Trash by lazy { icon("Trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6") }
    val Warning by lazy { icon("Warning", "M12 3L2 20h20zM12 10v5M12 17.5h.01") }
    val Info by lazy { icon("Info", circle(12f, 12f, 9f) + "M12 11v6M12 7.5h.01") }
    val Lock by lazy { icon("Lock", box(5f, 11f, 14f, 10f, 2.5f) + "M8 11V8a4 4 0 0 1 8 0v3") }
    val Doc by lazy { icon("Doc", "M6 3h8l5 5v13H6zM14 3v5h5M9 13h6M9 17h6") }
    val Mail by lazy { icon("Mail", box(3f, 5f, 18f, 14f, 2.5f) + "M3 7l9 6 9-6") }
    val Star by lazy { icon("Star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z") }
    val Edit by lazy { icon("Edit", "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4") }
    val Bell by lazy { icon("Bell", "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0") }
    val Cloud by lazy { icon("Cloud", "M7 18a4.5 4.5 0 0 1-.5-9A6 6 0 0 1 18 9.5 4.3 4.3 0 0 1 17.5 18z") }
    val People by lazy { icon("People", circle(9f, 8f, 3.5f) + "M2.5 20c.5-3.5 3-5.5 6.5-5.5s6 2 6.5 5.5M16 4.7a3.5 3.5 0 0 1 0 6.6M18 14.8c2 .6 3.3 2.4 3.6 5.2") }
    val Person by lazy { icon("Person", circle(12f, 8f, 4f) + "M4.5 20c.5-3.8 3.4-6 7.5-6s7 2.2 7.5 6") }
    val Calendar by lazy { icon("Calendar", box(3.5f, 5f, 17f, 15.5f, 2.5f) + "M8 3v4M16 3v4M3.5 10h17") }
    val CalendarCheck by lazy { icon("CalendarCheck", box(3.5f, 5f, 17f, 15.5f, 2.5f) + "M8 3v4M16 3v4M3.5 10h17M9 15l2 2 4-4") }
    val Pill by lazy { icon("Pill", "M10.5 20.5l10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7zM8.5 8.5l7 7") }
    val Sliders by lazy { icon("Sliders", "M4 7h10M18 7h2M4 17h2M10 17h10" + circle(16f, 7f, 2.2f) + circle(8f, 17f, 2.2f)) }
    val Sound by lazy { icon("Sound", "M11 5L6 9H3v6h3l5 4zM15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13") }
    val ClockLate by lazy { icon("ClockLate", circle(12f, 13f, 8f) + "M12 9v4l2.5 2M9 2.5h6") }
    val Moon by lazy { icon("Moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val Utensils by lazy { icon("Utensils", "M7 3v8a3 3 0 0 0 3 3v7M7 3v5M10 3v5M17 3c-2 2-3 5-3 8h3v10") }
    val Accessibility by lazy { icon("Accessibility", circle(12f, 4.5f, 1.8f) + "M5 8.5l7 1.5 7-1.5M12 10v5M12 15l-3 5.5M12 15l3 5.5") }
    val Globe by lazy { icon("Globe", circle(12f, 12f, 9f) + "M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18") }
    val Alarm by lazy { icon("Alarm", circle(12f, 13f, 8f) + "M12 9v4l3 2M4 4L2 6M20 4l2 2") }
    val Refresh by lazy { icon("Refresh", "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7") }
    val Share by lazy { icon("Share", "M12 15V3M8 7l4-4 4 4M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-7") }
    val Phone by lazy { icon("Phone", "M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z") }
    val List by lazy { icon("List", "M8 6h13M8 12h13M8 18h13M3.5 6h.01M3.5 12h.01M3.5 18h.01") }
}
