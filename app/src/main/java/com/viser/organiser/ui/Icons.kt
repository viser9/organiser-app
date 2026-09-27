package com.viser.organiser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Stroke icons drawn from the designs' inline SVG paths (24×24 viewBox). */
object Ic {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},$y h${w - 2 * r} a$r,$r 0 0 1 $r,$r v${h - 2 * r} a$r,$r 0 0 1 -$r,$r h${-(w - 2 * r)} a$r,$r 0 0 1 -$r,-$r v${-(h - 2 * r)} a$r,$r 0 0 1 $r,-$r z"

    private fun icon(name: String, width: Float, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                pathData = addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Search = icon("search", 1.7f, circle(11f, 11f, 6f), "m20 20-4.5-4.5")
    val Sliders = icon("sliders", 1.7f, "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    val Home = icon("home", 1.6f, "M4 10.5 12 4l8 6.5V20h-5.5v-5h-5v5H4z")
    val Bookmark = icon("bookmark", 1.6f, "M7 4h10v16l-5-3.5L7 20z")
    val Plus = icon("plus", 1.8f, "M12 5v14M5 12h14")
    val CheckCircle = icon("todo", 1.6f, circle(12f, 12f, 8f), "m8.5 12 2.5 2.5 4.5-5")
    val Wallet = icon("wallet", 1.6f, rect(3.5f, 6f, 17f, 13f, 2f), "M3.5 10h17", circle(16f, 14.5f, 1f))
    val Card = icon("card", 1.8f, rect(3.5f, 6f, 17f, 13f, 2f), "M3.5 10h17")
    val Inbox = icon("inbox", 1.8f, "M4 13 6.5 5h11L20 13v6H4z", "M4 13h4.5l1 2h5l1-2H20")
    val ChevronDown = icon("down", 2f, "m7 10 5 5 5-5")
    val ChevronRight = icon("right", 2f, "m9 6 6 6-6 6")
    val ChevronLeft = icon("left", 2f, "m15 6-6 6 6 6")
    val Close = icon("close", 1.8f, "M6 6l12 12M18 6 6 18")
    val Link = icon("link", 1.7f, "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1")
    val Note = icon("note", 1.7f, "M6 4h9l3 3v13H6z", "M9 10h6M9 14h6")
    val Place = icon("place", 1.7f, "M12 21s-6-5.5-6-10.5a6 6 0 0 1 12 0C18 15.5 12 21 12 21z", circle(12f, 10.5f, 2f))
    val Map = icon("map", 1.6f, "M4 6l5-2 6 2 5-2v14l-5 2-6-2-5 2z", "M9 4v14M15 6v14")
    val Grid = icon("grid", 1.6f, rect(4f, 4f, 7f, 7f, 1.5f), rect(13f, 4f, 7f, 7f, 1.5f), rect(4f, 13f, 7f, 7f, 1.5f), rect(13f, 13f, 7f, 7f, 1.5f))
    val ListView = icon("list", 1.7f, "M8 7h12M8 12h12M8 17h12", "M4 7h.01M4 12h.01M4 17h.01")
    val Bell = icon("bell", 1.8f, "M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20h4")
    val Repeat = icon("repeat", 1.8f, "M4 12a7 7 0 0 1 12-5l2 2M20 12a7 7 0 0 1-12 5l-2-2", "M18 4v5h-5M6 20v-5h5")
    val Check = icon("check", 3f, "m6 12 4 4 8-8")
    val Tag = icon("tag", 1.8f, "M4 4h8l8 8-8 8-8-8z", circle(9f, 9f, 1.3f))
    val Pin = icon("pin", 1.8f, "M9 4h6l-1 6 4 3v2H6v-2l4-3z", "M12 15v5")
    val Checklist = icon("checklist", 1.8f, rect(4f, 4f, 16f, 16f, 3f), "m8 12 3 3 5-6")
    val Trash = icon("trash", 1.7f, "M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13")
    val Open = icon("open", 1.7f, "M14 5h5v5M19 5l-8 8", "M17 14v5H5V7h5")
    val Navigate = icon("navigate", 1.7f, "M4 11 20 4l-7 16-2-7z")
    val Calendar = icon("calendar", 1.7f, rect(4f, 5f, 16f, 15f, 2f), "M4 10h16M9 3v4M15 3v4")
    val Download = icon("download", 1.7f, "M12 4v11M7 10l5 5 5-5M5 20h14")
    val Upload = icon("upload", 1.7f, "M12 20V9M7 14l5-5 5 5M5 4h14")
    val Sms = icon("sms", 1.7f, "M4 5h16v11H9l-5 4z")
    val Shield = icon("shield", 1.7f, "M12 3 5 6v6c0 4 3 7 7 9 4-2 7-5 7-9V6z")
    val Undo = icon("undo", 1.8f, "M9 14 4 9l5-5", "M4 9h10a6 6 0 0 1 0 12h-3")
}
