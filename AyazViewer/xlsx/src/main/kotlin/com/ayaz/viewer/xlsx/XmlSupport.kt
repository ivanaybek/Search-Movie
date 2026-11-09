package com.ayaz.viewer.xlsx

import org.xmlpull.v1.XmlPullParser

/** XmlPullParser için küçük yardımcılar. Ad alanı işleme açık varsayılır (yerel adlar). */
internal object Xml {

    fun attr(p: XmlPullParser, localName: String): String? {
        val n = p.attributeCount
        for (i in 0 until n) {
            if (localName(p.getAttributeName(i)) == localName) return p.getAttributeValue(i)
        }
        return null
    }

    /** İlişki kimliği (r:id) gibi ad alanlı öznitelikler için. */
    fun relAttr(p: XmlPullParser, localName: String): String? {
        val n = p.attributeCount
        var fallback: String? = null
        for (i in 0 until n) {
            if (localName(p.getAttributeName(i)) != localName) continue
            val ns = p.getAttributeNamespace(i) ?: ""
            val prefix = p.getAttributePrefix(i)
            if (ns.contains("relationships") || prefix == "r") return p.getAttributeValue(i)
            if (fallback == null) fallback = p.getAttributeValue(i)
        }
        return fallback
    }

    fun boolAttr(p: XmlPullParser, localName: String, default: Boolean): Boolean {
        val v = attr(p, localName) ?: return default
        return v == "1" || v.equals("true", ignoreCase = true) || v.equals("on", ignoreCase = true)
    }

    /** <b/>, <i val="0"/> gibi öğelerin değeri: val yoksa true. */
    fun flagElement(p: XmlPullParser): Boolean {
        val v = attr(p, "val") ?: return true
        return !(v == "0" || v.equals("false", ignoreCase = true) || v.equals("none", ignoreCase = true))
    }

    fun name(p: XmlPullParser): String = localName(p.name ?: "")

    private fun localName(raw: String): String {
        val idx = raw.indexOf(':')
        return if (idx >= 0) raw.substring(idx + 1) else raw
    }

    /** Geçerli START_TAG'ten eşleşen END_TAG'e kadar metni toplar (iç öğeler dahil). */
    fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF,
                XmlPullParser.IGNORABLE_WHITESPACE -> p.text?.let { sb.append(it) }
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    /**
     * Zengin metin (<si> / <is>) içindeki tüm <t> parçalarını birleştirir; fonetik
     * (<rPh>) bölümlerini atlar. Geçerli öğenin END_TAG'inde durur.
     */
    fun readRichText(p: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        var tDepth = 0
        var phoneticDepth = 0
        while (depth > 0) {
            when (p.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (name(p)) {
                        "rPh" -> phoneticDepth++
                        "t" -> if (phoneticDepth == 0) tDepth++
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth--
                    when (name(p)) {
                        "rPh" -> if (phoneticDepth > 0) phoneticDepth--
                        "t" -> if (tDepth > 0 && phoneticDepth == 0) tDepth--
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF,
                XmlPullParser.IGNORABLE_WHITESPACE -> if (tDepth > 0) p.text?.let { sb.append(it) }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return decodeEscapes(sb.toString())
    }

    /** Excel'in _x000D_ biçimindeki kaçış dizilerini çözer. */
    fun decodeEscapes(s: String): String {
        if (!s.contains("_x")) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '_' && i + 6 < s.length && s[i + 1] == 'x' &&
                s[i + 6] == '_' && isHex(s, i + 2, i + 6)
            ) {
                val code = s.substring(i + 2, i + 6).toInt(16)
                sb.append(code.toChar())
                i += 7
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun isHex(s: String, from: Int, to: Int): Boolean {
        for (k in from until to) {
            val c = s[k]
            if (!(c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F')) return false
        }
        return true
    }
}
