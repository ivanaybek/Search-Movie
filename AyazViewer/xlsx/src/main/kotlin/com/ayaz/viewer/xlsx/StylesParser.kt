package com.ayaz.viewer.xlsx

import org.xmlpull.v1.XmlPullParser
import kotlin.math.max
import kotlin.math.min

/** Ham renk başvurusu; palet/tema sonradan çözülür. */
internal data class ColorRef(
    val rgb: Int? = null,
    val theme: Int? = null,
    val indexed: Int? = null,
    val tint: Double = 0.0,
    val auto: Boolean = false,
)

internal object Colors {

    /** Standart Excel (BIFF8) 64 renk paleti. */
    val DEFAULT_PALETTE: IntArray = intArrayOf(
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080,
        0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF, 0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF,
        0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF,
        0x00CCFF, 0xCCFFFF, 0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99,
        0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696,
        0x003366, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366, 0x333399, 0x333333,
    ).map { it or OPAQUE }.toIntArray()

    /** Office varsayılan teması, Excel tema indeks sırasıyla (lt1, dk1, lt2, dk2, accent1..6, hlink, folHlink). */
    val DEFAULT_THEME: IntArray = intArrayOf(
        0xFFFFFF, 0x000000, 0xE7E6E6, 0x44546A,
        0x4472C4, 0xED7D31, 0xA5A5A5, 0xFFC000, 0x5B9BD5, 0x70AD47,
        0x0563C1, 0x954F72,
    ).map { it or OPAQUE }.toIntArray()

    const val OPAQUE: Int = -0x1000000 // 0xFF000000

    fun parseHex(value: String?): Int? {
        if (value == null) return null
        val v = value.trim().removePrefix("#")
        val rgb = when (v.length) {
            8 -> v.substring(2).toLongOrNull(16)?.toInt()
            6 -> v.toLongOrNull(16)?.toInt()
            else -> null
        } ?: return null
        // Excel alfa kanalını yok sayar; birçok üretici "00" alfa yazar.
        return rgb or OPAQUE
    }

    fun read(p: XmlPullParser): ColorRef {
        val auto = Xml.boolAttr(p, "auto", false)
        val tint = Xml.attr(p, "tint")?.toDoubleOrNull() ?: 0.0
        return ColorRef(
            rgb = parseHex(Xml.attr(p, "rgb")),
            theme = Xml.attr(p, "theme")?.toIntOrNull(),
            indexed = Xml.attr(p, "indexed")?.toIntOrNull(),
            tint = tint,
            auto = auto,
        )
    }

    fun resolve(ref: ColorRef?, palette: IntArray, theme: IntArray): Int? {
        if (ref == null || ref.auto) return null
        val base = when {
            ref.rgb != null -> ref.rgb
            ref.theme != null -> theme.getOrNull(ref.theme)
            ref.indexed != null -> if (ref.indexed in palette.indices) palette[ref.indexed] else null
            else -> null
        } ?: return null
        return if (ref.tint != 0.0) applyTint(base, ref.tint) else base
    }

    /** Excel tint algoritması (HLS parlaklık ayarı). */
    fun applyTint(argb: Int, tint: Double): Int {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        var l = (mx + mn) / 2.0
        var h = 0.0
        var s = 0.0
        if (mx != mn) {
            val d = mx - mn
            s = if (l > 0.5) d / (2.0 - mx - mn) else d / (mx + mn)
            h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6.0 else 0.0)
                g -> (b - r) / d + 2.0
                else -> (r - g) / d + 4.0
            } / 6.0
        }
        l = if (tint < 0) l * (1.0 + tint) else l * (1.0 - tint) + tint
        l = l.coerceIn(0.0, 1.0)
        val (nr, ng, nb) = hslToRgb(h, s, l)
        return OPAQUE or (nr shl 16) or (ng shl 8) or nb
    }

    private fun hslToRgb(h: Double, s: Double, l: Double): Triple<Int, Int, Int> {
        if (s == 0.0) {
            val v = (l * 255.0 + 0.5).toInt().coerceIn(0, 255)
            return Triple(v, v, v)
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun hue(tIn: Double): Double {
            var t = tIn
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        fun to255(x: Double) = (x * 255.0 + 0.5).toInt().coerceIn(0, 255)
        return Triple(to255(hue(h + 1.0 / 3)), to255(hue(h)), to255(hue(h - 1.0 / 3)))
    }
}

/** theme1.xml içinden renk şemasını okur (Excel tema indeks sırasıyla döner). */
internal object ThemeParser {
    private val ORDER = mapOf(
        "lt1" to 0, "dk1" to 1, "lt2" to 2, "dk2" to 3,
        "accent1" to 4, "accent2" to 5, "accent3" to 6, "accent4" to 7, "accent5" to 8, "accent6" to 9,
        "hlink" to 10, "folHlink" to 11,
    )

    fun parse(p: XmlPullParser): IntArray {
        val result = Colors.DEFAULT_THEME.copyOf()
        var inScheme = false
        var current = -1
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val n = Xml.name(p)
                when {
                    n == "clrScheme" -> inScheme = true
                    inScheme && ORDER.containsKey(n) -> current = ORDER.getValue(n)
                    inScheme && current >= 0 && n == "srgbClr" ->
                        Colors.parseHex(Xml.attr(p, "val"))?.let { result[current] = it }
                    inScheme && current >= 0 && n == "sysClr" -> {
                        val c = Colors.parseHex(Xml.attr(p, "lastClr"))
                            ?: when (Xml.attr(p, "val")) {
                                "window" -> Colors.OPAQUE or 0xFFFFFF
                                else -> Colors.OPAQUE
                            }
                        result[current] = c
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                val n = Xml.name(p)
                if (n == "clrScheme") return result
                if (ORDER.containsKey(n)) current = -1
            }
            event = p.next()
        }
        return result
    }
}

/** styles.xml ayrıştırıcısı. */
internal object StylesParser {

    private class FontSpec(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var strike: Boolean = false,
        var size: Float = 11f,
        var name: String? = null,
        var color: ColorRef? = null,
    )

    private class SideSpec(val style: String, var color: ColorRef? = null)

    private class BorderSpec(
        var left: SideSpec? = null,
        var right: SideSpec? = null,
        var top: SideSpec? = null,
        var bottom: SideSpec? = null,
    )

    private class FillSpec(
        var pattern: String? = null,
        var fg: ColorRef? = null,
        var bg: ColorRef? = null,
        var gradient: ColorRef? = null,
        var isGradient: Boolean = false,
    )

    private class XfSpec(
        val numFmtId: Int,
        val fontId: Int,
        val fillId: Int,
        val borderId: Int,
        var hAlign: HAlign = HAlign.GENERAL,
        var vAlign: VAlign = VAlign.BOTTOM,
        var wrap: Boolean = false,
        var indent: Int = 0,
    )

    fun parse(p: XmlPullParser, theme: IntArray): List<CellStyle> {
        val numFmts = HashMap<Int, String>()
        val fonts = ArrayList<FontSpec>()
        val fills = ArrayList<FillSpec>()
        val borders = ArrayList<BorderSpec>()
        val xfs = ArrayList<XfSpec>()
        val customPalette = ArrayList<Int>()

        var section = ""
        var font: FontSpec? = null
        var fill: FillSpec? = null
        var border: BorderSpec? = null
        var side: SideSpec? = null
        var xf: XfSpec? = null
        var gradientStop = false

        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                val n = Xml.name(p)
                when (n) {
                    "numFmts", "fonts", "fills", "borders", "cellXfs", "cellStyleXfs", "dxfs",
                    "indexedColors", "colors", "tableStyles", "cellStyles", "extLst" ->
                        if (section.isEmpty() || n == "indexedColors") section = n
                }
                when (section) {
                    "numFmts" -> if (n == "numFmt") {
                        val id = Xml.attr(p, "numFmtId")?.toIntOrNull()
                        val code = Xml.attr(p, "formatCode")
                        if (id != null && code != null) numFmts[id] = code
                    }
                    "fonts" -> when (n) {
                        "font" -> font = FontSpec()
                        "b" -> font?.bold = Xml.flagElement(p)
                        "i" -> font?.italic = Xml.flagElement(p)
                        "u" -> font?.underline = Xml.flagElement(p)
                        "strike" -> font?.strike = Xml.flagElement(p)
                        "sz" -> font?.let { f ->
                            Xml.attr(p, "val")?.toFloatOrNull()?.let { if (it > 0f && it < 410f) f.size = it }
                        }
                        "name" -> font?.name = Xml.attr(p, "val")
                        "color" -> font?.color = Colors.read(p)
                    }
                    "fills" -> when (n) {
                        "fill" -> fill = FillSpec()
                        "patternFill" -> fill?.pattern = Xml.attr(p, "patternType") ?: "none"
                        "fgColor" -> fill?.fg = Colors.read(p)
                        "bgColor" -> fill?.bg = Colors.read(p)
                        "gradientFill" -> fill?.isGradient = true
                        "stop" -> gradientStop = true
                        "color" -> if (gradientStop && fill?.gradient == null) fill?.gradient = Colors.read(p)
                    }
                    "borders" -> when (n) {
                        "border" -> border = BorderSpec()
                        "left", "start", "right", "end", "top", "bottom" -> {
                            val style = Xml.attr(p, "style")
                            side = if (style != null && style != "none") SideSpec(style) else null
                            border?.let { b ->
                                when (n) {
                                    "left", "start" -> b.left = side
                                    "right", "end" -> b.right = side
                                    "top" -> b.top = side
                                    "bottom" -> b.bottom = side
                                }
                            }
                        }
                        "diagonal", "vertical", "horizontal" -> side = null
                        "color" -> side?.color = Colors.read(p)
                    }
                    "cellXfs" -> when (n) {
                        "xf" -> xf = XfSpec(
                            numFmtId = Xml.attr(p, "numFmtId")?.toIntOrNull() ?: 0,
                            fontId = Xml.attr(p, "fontId")?.toIntOrNull() ?: 0,
                            fillId = Xml.attr(p, "fillId")?.toIntOrNull() ?: 0,
                            borderId = Xml.attr(p, "borderId")?.toIntOrNull() ?: 0,
                        )
                        "alignment" -> xf?.let { x ->
                            x.hAlign = when (Xml.attr(p, "horizontal")) {
                                "left" -> HAlign.LEFT
                                "center" -> HAlign.CENTER
                                "right" -> HAlign.RIGHT
                                "fill" -> HAlign.FILL
                                "justify" -> HAlign.JUSTIFY
                                "centerContinuous" -> HAlign.CENTER_CONTINUOUS
                                "distributed" -> HAlign.DISTRIBUTED
                                else -> HAlign.GENERAL
                            }
                            x.vAlign = when (Xml.attr(p, "vertical")) {
                                "top" -> VAlign.TOP
                                "center" -> VAlign.CENTER
                                "justify" -> VAlign.JUSTIFY
                                "distributed" -> VAlign.DISTRIBUTED
                                else -> VAlign.BOTTOM
                            }
                            x.wrap = Xml.boolAttr(p, "wrapText", false)
                            x.indent = Xml.attr(p, "indent")?.toIntOrNull()?.coerceIn(0, 50) ?: 0
                        }
                    }
                    "indexedColors" -> if (n == "rgbColor") {
                        customPalette.add(Colors.parseHex(Xml.attr(p, "rgb")) ?: Colors.OPAQUE)
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                val n = Xml.name(p)
                if (n == section) {
                    section = if (n == "indexedColors") "colors" else ""
                    if (n == "colors") section = ""
                } else {
                    when (section) {
                        "fonts" -> if (n == "font") { font?.let { fonts.add(it) }; font = null }
                        "fills" -> when (n) {
                            "fill" -> { fill?.let { fills.add(it) }; fill = null }
                            "stop" -> gradientStop = false
                        }
                        "borders" -> when (n) {
                            "border" -> { border?.let { borders.add(it) }; border = null }
                            "left", "start", "right", "end", "top", "bottom", "diagonal" -> side = null
                        }
                        "cellXfs" -> if (n == "xf") { xf?.let { xfs.add(it) }; xf = null }
                    }
                }
            }
            event = p.next()
        }

        val palette = if (customPalette.isNotEmpty()) {
            val pal = Colors.DEFAULT_PALETTE.copyOf()
            for (i in customPalette.indices) if (i < pal.size) pal[i] = customPalette[i]
            pal
        } else Colors.DEFAULT_PALETTE

        fun resolveSide(s: SideSpec?): BorderSide? {
            if (s == null) return null
            val weight = when (s.style) {
                "medium", "mediumDashed", "mediumDashDot", "mediumDashDotDot", "slantDashDot" -> 2
                "thick" -> 3
                "double" -> 2
                "hair" -> 1
                else -> 1
            }
            val dashed = s.style.contains("ash", ignoreCase = true) || s.style == "dotted" || s.style == "hair"
            return BorderSide(weight, Colors.resolve(s.color, palette, theme), dashed, s.style == "double")
        }

        val defaultFont = fonts.firstOrNull() ?: FontSpec()
        val styles = ArrayList<CellStyle>(max(1, xfs.size))
        for (x in xfs) {
            val f = fonts.getOrNull(x.fontId) ?: defaultFont
            val fl = fills.getOrNull(x.fillId)
            val fillColor = fl?.let { spec ->
                if (spec.isGradient) {
                    Colors.resolve(spec.gradient, palette, theme)
                } else when (spec.pattern) {
                    null, "none", "gray125", "gray0625" -> null
                    else -> {
                        val fg = spec.fg?.takeUnless { it.indexed == 64 }
                        Colors.resolve(fg, palette, theme)
                            ?: Colors.resolve(spec.bg?.takeUnless { it.indexed == 64 || it.indexed == 65 }, palette, theme)
                    }
                }
            }
            val b = borders.getOrNull(x.borderId)
            val fontColorRef = f.color?.takeUnless { it.indexed == 64 }
            styles.add(
                CellStyle(
                    numFmtId = x.numFmtId,
                    formatCode = numFmts[x.numFmtId] ?: BuiltinFormats.code(x.numFmtId),
                    bold = f.bold,
                    italic = f.italic,
                    underline = f.underline,
                    strike = f.strike,
                    fontColor = Colors.resolve(fontColorRef, palette, theme),
                    fontSizePt = f.size,
                    fontName = f.name,
                    fillColor = fillColor,
                    hAlign = x.hAlign,
                    vAlign = x.vAlign,
                    wrapText = x.wrap,
                    indent = x.indent,
                    borderLeft = resolveSide(b?.left),
                    borderRight = resolveSide(b?.right),
                    borderTop = resolveSide(b?.top),
                    borderBottom = resolveSide(b?.bottom),
                ),
            )
        }
        if (styles.isEmpty()) {
            styles.add(CellStyle(fontSizePt = defaultFont.size, fontName = defaultFont.name, formatCode = "General"))
        }
        return styles
    }
}
