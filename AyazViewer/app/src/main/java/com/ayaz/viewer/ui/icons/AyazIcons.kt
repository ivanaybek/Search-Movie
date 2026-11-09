package com.ayaz.viewer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Material Icons çekirdek paketinde olmayan simgeler (Material Design ikon yolları, Apache 2.0).
 * Büyük "material-icons-extended" kitaplığı yerine yalnızca gerekenler eklenmiştir.
 */
object AyazIcons {

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    val History by lazy {
        icon(
            "History",
            "M13,3c-4.97,0 -9,4.03 -9,9L1,12l3.89,3.89 0.07,0.14L9,12L6,12c0,-3.87 3.13,-7 7,-7s7,3.13 7,7 -3.13,7 -7,7c-1.93,0 -3.68,-0.79 -4.94,-2.06l-1.42,1.42C8.27,19.99 10.51,21 13,21c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9zM12,8v5l4.28,2.54 0.72,-1.21 -3.5,-2.08L13.5,8L12,8z",
        )
    }
    val Folder by lazy {
        icon("Folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }
    val FolderOpen by lazy {
        icon(
            "FolderOpen",
            "M20,6h-8l-2,-2L4,4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2L22,8c0,-1.1 -0.9,-2 -2,-2zM20,18L4,18L4,8h16v10z",
        )
    }
    val CreateNewFolder by lazy {
        icon(
            "CreateNewFolder",
            "M20,6h-8l-2,-2L4,4c-1.11,0 -1.99,0.89 -1.99,2L2,18c0,1.11 0.89,2 2,2h16c1.11,0 2,-0.89 2,-2L22,8c0,-1.11 -0.89,-2 -2,-2zM19,14h-3v3h-2v-3h-3v-2h3L14,9h2v3h3v2z",
        )
    }
    val ContentCopy by lazy {
        icon(
            "ContentCopy",
            "M16,1L4,1c-1.1,0 -2,0.9 -2,2v14h2L4,3h12L16,1zM19,5L8,5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h11c1.1,0 2,-0.9 2,-2L21,7c0,-1.1 -0.9,-2 -2,-2zM19,21L8,21L8,7h11v14z",
        )
    }
    val Download by lazy { icon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z") }
    val NoteAdd by lazy {
        icon(
            "NoteAdd",
            "M14,2L6,2c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2L18,22c1.1,0 2,-0.9 2,-2L20,8l-6,-6zM16,16h-3v3h-2v-3L8,16v-2h3v-3h2v3h3v2zM13,9L13,3.5L18.5,9L13,9z",
        )
    }
    val Description by lazy {
        icon(
            "Description",
            "M14,2H6c-1.1,0 -1.99,0.9 -1.99,2L4,20c0,1.1 0.89,2 1.99,2H18c1.1,0 2,-0.9 2,-2V8l-6,-6zM16,18H8v-2h8v2zM16,14H8v-2h8v2zM13,9V3.5L18.5,9H13z",
        )
    }
    val Chat by lazy {
        icon(
            "Chat",
            "M20,2L4,2c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2zM6,9h12v2L6,11L6,9zM14,14L6,14v-2h8v2zM18,8L6,8L6,6h12v2z",
        )
    }
    val Shield by lazy { icon("Shield", "M12,1L3,5v6c0,5.55 3.84,10.74 9,12 5.16,-1.26 9,-6.45 9,-12L21,5l-9,-4z") }
    val DarkMode by lazy {
        icon(
            "DarkMode",
            "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9 9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36 -0.98,1.37 -2.58,2.26 -4.4,2.26 -2.98,0 -5.4,-2.42 -5.4,-5.4 0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z",
        )
    }
    val Storage by lazy {
        icon(
            "Storage",
            "M2,20h20v-4L2,16v4zM4,17h2v2L4,19v-2zM2,4v4h20L22,4L2,4zM6,7L4,7L4,5h2v2zM2,14h20v-4L2,10v4zM4,11h2v2L4,13v-2z",
        )
    }
    val Numbers by lazy {
        icon(
            "Numbers",
            "M20.5,10L21,8h-4l1,-4h-2l-1,4h-4l1,-4h-2L9,8H5l-0.5,2h4l-1,4h-4L3,16h4l-1,4h2l1,-4h4l-1,4h2l1,-4h4l0.5,-2h-4l1,-4H20.5zM13.5,14h-4l1,-4h4L13.5,14z",
        )
    }
    val GridOn by lazy {
        icon(
            "GridOn",
            "M20,2H4c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM8,20H4v-4h4v4zM8,14H4v-4h4v4zM8,8H4V4h4v4zM14,20h-4v-4h4v4zM14,14h-4v-4h4v4zM14,8h-4V4h4v4zM20,20h-4v-4h4v4zM20,14h-4v-4h4v4zM20,8h-4V4h4v4z",
        )
    }
    val Pages by lazy {
        icon(
            "Pages",
            "M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zM19,19H5V5h14v14zM7,7h10v2H7zM7,11h10v2H7zM7,15h7v2H7z",
        )
    }

    val Undo by lazy {
        icon("Undo", "M12.5,8c-2.65,0 -5.05,0.99 -6.9,2.6L2,7v9h9l-3.62,-3.62c1.39,-1.16 3.16,-1.88 5.12,-1.88 3.54,0 6.55,2.31 7.6,5.5l2.37,-0.78C21.08,11.03 17.15,8 12.5,8z")
    }
    val Redo by lazy {
        icon("Redo", "M18.4,10.6C16.55,8.99 14.15,8 11.5,8c-4.65,0 -8.58,3.03 -9.96,7.22L3.9,16c1.05,-3.19 4.05,-5.5 7.6,-5.5 1.95,0 3.73,0.72 5.12,1.88L13,16h9V7l-3.6,3.6z")
    }
    val Save by lazy {
        icon("Save", "M17,3L5,3c-1.11,0 -2,0.9 -2,2v14c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2,-2L21,7l-4,-4zM12,19c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM15,9L5,9L5,5h10v4z")
    }
    val Highlighter by lazy {
        icon("Highlighter", "M22,24H2v-4h20V24zM13.06,5.19l3.75,3.75L7.75,18H4v-3.75L13.06,5.19zM17.88,7.87l-3.75,-3.75l1.83,-1.83c0.39,-0.39 1.02,-0.39 1.41,0l2.34,2.34c0.39,0.39 0.39,1.02 0,1.41L17.88,7.87z")
    }
    val TextFields by lazy {
        icon("TextFields", "M2.5,4v3h5v12h3V7h5V4H2.5zM21.5,9h-9v3h3v7h3v-7h3V9z")
    }
    val Eraser by lazy {
        icon("Eraser", "M16.24,3.56l4.95,4.94c0.78,0.79 0.78,2.05 0,2.84L12,20.53a4.008,4.008 0,0 1,-5.66 0L2.81,17c-0.78,-0.79 -0.78,-2.05 0,-2.84l10.6,-10.6c0.79,-0.78 2.05,-0.78 2.83,0zM4.22,15.58l3.54,3.53c0.78,0.79 2.04,0.79 2.83,0l3.53,-3.53l-4.95,-4.95l-4.95,4.95z")
    }
    val RotateRight by lazy {
        icon("RotateRight", "M15.55,5.55L11,1v3.07C7.06,4.56 4,7.92 4,12s3.05,7.44 7,7.93v-2.02c-2.84,-0.48 -5,-2.94 -5,-5.91s2.16,-5.43 5,-5.91L11,10l4.55,-4.45zM19.93,11c-0.17,-1.39 -0.72,-2.73 -1.62,-3.89l-1.42,1.42c0.54,0.75 0.88,1.6 1.02,2.47h2.02zM13,17.9v2.02c1.39,-0.17 2.74,-0.71 3.9,-1.61l-1.44,-1.44c-0.75,0.54 -1.59,0.89 -2.46,1.03zM16.89,15.48l1.42,1.41c0.9,-1.16 1.45,-2.5 1.62,-3.89h-2.02c-0.14,0.87 -0.48,1.72 -1.02,2.48z")
    }
    val TableRows by lazy {
        icon("TableRows", "M21,8H3V4h18V8zM21,10H3v4h18V10zM21,16H3v4h18V16z")
    }
}
