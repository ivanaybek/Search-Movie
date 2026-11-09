package com.ayaz.viewer.xlsx

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** Sayı/tarih görüntüleme için yerel ayar sembolleri. */
class FormatLocale(
    val decimalSeparator: Char,
    val groupingSeparator: Char,
    val monthsShort: Array<String>,
    val monthsLong: Array<String>,
    /** Pazartesi'den başlayarak (ISO sırası). */
    val daysShort: Array<String>,
    val daysLong: Array<String>,
    val am: String,
    val pm: String,
    val trueText: String,
    val falseText: String,
) {
    companion object {
        val TURKISH = FormatLocale(
            decimalSeparator = ',',
            groupingSeparator = '.',
            monthsShort = arrayOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"),
            monthsLong = arrayOf(
                "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
            ),
            daysShort = arrayOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"),
            daysLong = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"),
            am = "ÖÖ",
            pm = "ÖS",
            trueText = "DOĞRU",
            falseText = "YANLIŞ",
        )
        val ENGLISH = FormatLocale(
            decimalSeparator = '.',
            groupingSeparator = ',',
            monthsShort = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
            monthsLong = arrayOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December",
            ),
            daysShort = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
            daysLong = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"),
            am = "AM",
            pm = "PM",
            trueText = "TRUE",
            falseText = "FALSE",
        )
    }
}

class FormattedValue(val text: String, val color: Int?)

/** Excel seri tarih dönüşümleri. */
object ExcelDate {
    private val EPOCH_1900 = LocalDate.of(1899, 12, 30)
    private val EPOCH_1900_EARLY = LocalDate.of(1899, 12, 31)
    private val EPOCH_1904 = LocalDate.of(1904, 1, 1)
    const val MAX_SERIAL = 2_958_465.999_999

    fun dayToDate(days: Long, date1904: Boolean): LocalDate = when {
        date1904 -> EPOCH_1904.plusDays(days)
        days < 61 -> EPOCH_1900_EARLY.plusDays(days) // 1900 artık yıl hatası öncesi
        else -> EPOCH_1900.plusDays(days)
    }

    fun toDateTime(serial: Double, date1904: Boolean): LocalDateTime? {
        if (serial.isNaN() || serial < 0 || serial > MAX_SERIAL) return null
        val totalMs = (serial * 86_400_000.0).roundToLong()
        val days = totalMs / 86_400_000L
        val ms = totalMs % 86_400_000L
        return dayToDate(days, date1904).atStartOfDay().plusNanos(ms * 1_000_000L)
    }

    fun fromDateTime(dt: LocalDateTime, date1904: Boolean): Double {
        val date = dt.toLocalDate()
        val days: Long = if (date1904) {
            ChronoUnit.DAYS.between(EPOCH_1904, date)
        } else {
            val d = ChronoUnit.DAYS.between(EPOCH_1900, date)
            if (d < 61) ChronoUnit.DAYS.between(EPOCH_1900_EARLY, date) else d
        }
        val secOfDay = dt.toLocalTime().toNanoOfDay() / 1e9
        return days + secOfDay / 86_400.0
    }

    /** "2024-03-05", "2024-03-05T10:20:30Z", "2024-03-05T10:20:30.123" */
    fun parseIso(text: String): LocalDateTime? {
        val t = text.trim().removeSuffix("Z")
        return try {
            when {
                t.length == 10 -> LocalDate.parse(t).atStartOfDay()
                t.contains('T') -> {
                    val noZone = t.substringBefore('+').let { s ->
                        // "-hh:mm" saat dilimini at (tarih kısmındaki tireleri koru)
                        val idx = s.lastIndexOf('-')
                        if (idx > 10) s.substring(0, idx) else s
                    }
                    LocalDateTime.parse(noZone)
                }
                t.length in 5..12 && t.contains(':') -> LocalDate.of(1899, 12, 31)
                    .atTime(java.time.LocalTime.parse(t))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Excel sayı biçim kodu (ör. `#,##0.00;[Red]-#,##0.00`, `dd.mm.yyyy hh:mm`) yorumlayıcısı.
 * Desteklenenler: bölümler, koşullar, renkler, tırnaklı metinler, binlik ayırıcı, ölçekleme,
 * yüzde, bilimsel gösterim, kesirler, tarih/saat ve geçen süre ([h]:mm).
 */
class NumberFormat private constructor(private val sections: List<Section>) {

    private val textSection: Section? = sections.firstOrNull { it.hasAt } ?: sections.getOrNull(3)
    private val numberSections: List<Section> = sections.filter { it !== textSection }.take(3)
    private val hasConditions = numberSections.any { it.condOp != null }

    /** Biçim, tarih/saat biçimi mi (ilk sayı bölümüne göre). */
    val isDateFormat: Boolean = numberSections.firstOrNull()?.isDate == true

    fun format(value: Double, date1904: Boolean, locale: FormatLocale): FormattedValue {
        if (value.isNaN()) return FormattedValue("#SAYI!", null)
        if (value.isInfinite()) return FormattedValue("#SAYI!", null)
        if (numberSections.isEmpty()) return FormattedValue(formatGeneral(value, locale), null)

        var autoMinus: Boolean
        val section: Section
        if (!hasConditions) {
            when (numberSections.size) {
                1 -> { section = numberSections[0]; autoMinus = value < 0 }
                2 -> if (value >= 0) { section = numberSections[0]; autoMinus = false }
                    else { section = numberSections[1]; autoMinus = false }
                else -> when {
                    value > 0 -> { section = numberSections[0]; autoMinus = false }
                    value < 0 -> { section = numberSections[1]; autoMinus = false }
                    else -> { section = numberSections[2]; autoMinus = false }
                }
            }
        } else {
            val matched = numberSections.firstOrNull { it.condOp != null && it.matches(value) }
                ?: numberSections.firstOrNull { it.condOp == null }
                ?: return FormattedValue("#".repeat(6), null)
            section = matched
            autoMinus = value < 0 && !(matched.condOp?.startsWith("<") == true && matched.condValue <= 0)
        }

        val text = when {
            section.isDate -> formatDate(section, value, date1904, locale)
            section.isGeneralOnly -> {
                val num = if (value < 0 && !autoMinus) abs(value) else value
                val sb = StringBuilder()
                for (t in section.tokens) {
                    when (t) {
                        is Tok.Lit -> sb.append(t.text)
                        is Tok.General -> sb.append(formatGeneral(num, locale))
                        is Tok.Percent -> sb.append('%')
                        else -> {}
                    }
                }
                sb.toString()
            }
            else -> formatNumber(section, value, autoMinus, locale)
        }
        return FormattedValue(text, section.color)
    }

    fun formatText(text: String): FormattedValue {
        val sec = textSection ?: return FormattedValue(text, null)
        val sb = StringBuilder()
        for (t in sec.tokens) {
            when (t) {
                is Tok.At -> sb.append(text)
                is Tok.Lit -> sb.append(t.text)
                else -> {}
            }
        }
        return FormattedValue(sb.toString(), sec.color)
    }

    // ---------------------------------------------------------------- sayılar

    private fun formatNumber(sec: Section, raw: Double, autoMinus: Boolean, loc: FormatLocale): String {
        var v = abs(raw)
        repeat(sec.percentCount) { v *= 100.0 }
        repeat(sec.scaleCommas) { v /= 1000.0 }
        val body = when {
            sec.expIndex >= 0 -> formatScientific(sec, v, loc)
            sec.slashIndex >= 0 -> formatFraction(sec, v)
            else -> formatFixed(sec, v, loc)
        }
        val isZero = body.none { it in '1'..'9' }
        return if (raw < 0 && autoMinus && !isZero) "-$body" else body
    }

    private fun formatFixed(sec: Section, v: Double, loc: FormatLocale): String {
        val decimals = sec.fracDigitCount
        val plain = roundHalfUp(v, decimals).toPlainString()
        var intStr = plain.substringBefore('.')
        val fracStr = plain.substringAfter('.', "").padEnd(decimals, '0')
        if (intStr == "0") intStr = ""
        val sb = StringBuilder()
        renderInteger(sec.intTokens, intStr, sec.grouping, loc, sb, showBareDigits = sec.dotIndex >= 0)
        if (sec.dotIndex >= 0) {
            sb.append(loc.decimalSeparator)
            renderFraction(sec.fracTokens, fracStr, sb)
        }
        return sb.toString()
    }

    private fun renderInteger(
        tokens: List<Tok>,
        digits: String,
        grouping: Boolean,
        loc: FormatLocale,
        sb: StringBuilder,
        showBareDigits: Boolean = false,
    ) {
        val digitTokens = tokens.count { it is Tok.Digit }
        if (digitTokens == 0) {
            // Yer tutucu yoksa yalnızca metinler gösterilir; ".00" gibi biçimlerde tam kısım yine yazılır.
            for (t in tokens) appendLiteral(t, sb)
            if (showBareDigits && digits.isNotEmpty()) sb.append(digits)
            return
        }
        if (grouping) {
            val firstDigit = tokens.indexOfFirst { it is Tok.Digit }
            val lastDigit = tokens.indexOfLast { it is Tok.Digit }
            for (i in 0 until firstDigit) appendLiteral(tokens[i], sb)
            // En az '0' yer tutucu sayısı kadar basamak göster.
            val zeros = tokens.count { it is Tok.Digit && it.ch == '0' }
            val padded = if (digits.length < zeros) digits.padStart(zeros, '0') else digits
            val grouped = StringBuilder()
            for ((idx, ch) in padded.withIndex()) {
                val fromRight = padded.length - idx
                grouped.append(ch)
                if (fromRight > 1 && (fromRight - 1) % 3 == 0) grouped.append(loc.groupingSeparator)
            }
            if (padded.isEmpty()) {
                // '?' yer tutucuları için hizalama boşluğu
                val q = tokens.count { it is Tok.Digit && it.ch == '?' }
                repeat(q) { grouped.append(' ') }
            }
            sb.append(grouped)
            for (i in lastDigit + 1 until tokens.size) appendLiteral(tokens[i], sb)
            return
        }
        var k = 0
        val overflow = digits.length - digitTokens
        for (t in tokens) {
            if (t is Tok.Digit) {
                if (k == 0 && overflow > 0) sb.append(digits, 0, overflow)
                val pos = overflow + k
                if (pos >= 0) {
                    sb.append(digits[pos])
                } else {
                    when (t.ch) {
                        '0' -> sb.append('0')
                        '?' -> sb.append(' ')
                        else -> {}
                    }
                }
                k++
            } else {
                appendLiteral(t, sb)
            }
        }
    }

    private fun renderFraction(tokens: List<Tok>, digits: String, sb: StringBuilder) {
        var lastNonZero = -1
        for (i in digits.indices) if (digits[i] != '0') lastNonZero = i
        var j = 0
        for (t in tokens) {
            if (t is Tok.Digit) {
                val ch = if (j < digits.length) digits[j] else '0'
                if (j > lastNonZero) {
                    when (t.ch) {
                        '0' -> sb.append('0')
                        '?' -> sb.append(' ')
                        else -> {}
                    }
                } else {
                    sb.append(ch)
                }
                j++
            } else {
                appendLiteral(t, sb)
            }
        }
    }

    private fun appendLiteral(t: Tok, sb: StringBuilder) {
        when (t) {
            is Tok.Lit -> sb.append(t.text)
            is Tok.Percent -> sb.append('%')
            else -> {}
        }
    }

    private fun formatScientific(sec: Section, v: Double, loc: FormatLocale): String {
        val intTokens = sec.tokens.subList(0, if (sec.dotIndex in 0 until sec.expIndex) sec.dotIndex else sec.expIndex)
        val fracTokens = if (sec.dotIndex in 0 until sec.expIndex) sec.tokens.subList(sec.dotIndex + 1, sec.expIndex) else emptyList()
        val expTok = sec.tokens[sec.expIndex] as Tok.Exp
        val expTokens = sec.tokens.subList(sec.expIndex + 1, sec.tokens.size)
        val intDigits = intTokens.count { it is Tok.Digit }.coerceAtLeast(1)
        val decimals = fracTokens.count { it is Tok.Digit }
        val engineering = intDigits > 1 && intTokens.any { it is Tok.Digit && it.ch == '#' }
        var exponent = 0
        var mantissa = 0.0
        if (v != 0.0) {
            var e = floor(log10(v)).toInt()
            e = if (engineering) Math.floorDiv(e, intDigits) * intDigits else e - (intDigits - 1)
            mantissa = v / 10.0.pow(e)
            val limit = 10.0.pow(intDigits)
            var rounded = roundHalfUp(mantissa, decimals).toDouble()
            if (rounded >= limit) {
                val step = if (engineering) intDigits else 1
                e += step
                mantissa = v / 10.0.pow(e)
                rounded = roundHalfUp(mantissa, decimals).toDouble()
            }
            mantissa = rounded
            exponent = e
        }
        val plain = roundHalfUp(mantissa, decimals).toPlainString()
        var intStr = plain.substringBefore('.')
        val fracStr = plain.substringAfter('.', "").padEnd(decimals, '0')
        if (intStr == "0") intStr = ""
        val sb = StringBuilder()
        renderInteger(intTokens, intStr, false, loc, sb)
        if (sec.dotIndex in 0 until sec.expIndex) {
            sb.append(loc.decimalSeparator)
            renderFraction(fracTokens, fracStr, sb)
        }
        sb.append('E')
        if (exponent < 0) sb.append('-') else if (expTok.plus) sb.append('+')
        val expDigits = expTokens.count { it is Tok.Digit }.coerceAtLeast(1)
        sb.append(abs(exponent).toString().padStart(expDigits, '0'))
        val lastDigit = expTokens.indexOfLast { it is Tok.Digit }
        for (i in lastDigit + 1 until expTokens.size) appendLiteral(expTokens[i], sb)
        return sb.toString()
    }

    private fun formatFraction(sec: Section, v: Double): String {
        val tokens = sec.tokens
        val slash = sec.slashIndex
        var numStart = slash - 1
        while (numStart >= 0 && tokens[numStart] is Tok.Digit) numStart--
        val before = tokens.subList(0, numStart + 1)
        val hasWhole = before.any { it is Tok.Digit }
        var denDigits = 0
        var fixedDen = 0
        var afterEnd = slash + 1
        while (afterEnd < tokens.size) {
            val t = tokens[afterEnd]
            if (t is Tok.Digit) {
                denDigits++
            } else if (t is Tok.Lit && t.text.all { it.isDigit() } && t.text.isNotEmpty() && denDigits == 0) {
                fixedDen = t.text.toIntOrNull() ?: 0
            } else break
            afterEnd++
        }
        val maxDen = if (fixedDen > 0) fixedDen else (10.0.pow(denDigits.coerceIn(1, 4)).toInt() - 1)
        var whole = if (hasWhole) floor(v).toLong() else 0L
        val frac = v - whole
        var bestNum = 0L
        var bestDen = 1L
        if (fixedDen > 0) {
            bestDen = fixedDen.toLong()
            bestNum = (frac * fixedDen).roundToLong()
        } else {
            var bestErr = Double.MAX_VALUE
            for (d in 1..maxDen) {
                val n = (frac * d).roundToLong()
                val err = abs(frac - n.toDouble() / d)
                if (err < bestErr - 1e-12) {
                    bestErr = err
                    bestNum = n
                    bestDen = d.toLong()
                    if (err == 0.0) break
                }
            }
        }
        if (hasWhole && bestNum == bestDen && bestNum != 0L) {
            whole++
            bestNum = 0
        }
        val sb = StringBuilder()
        val firstDigit = tokens.indexOfFirst { it is Tok.Digit }
        for (i in 0 until firstDigit.coerceAtLeast(0)) appendLiteral(tokens[i], sb)
        val parts = ArrayList<String>(2)
        if (hasWhole && (whole != 0L || bestNum == 0L)) parts.add(whole.toString())
        if (bestNum != 0L) parts.add("$bestNum/$bestDen")
        if (parts.isEmpty()) parts.add("0")
        sb.append(parts.joinToString(" "))
        for (i in afterEnd until tokens.size) appendLiteral(tokens[i], sb)
        return sb.toString()
    }

    // ---------------------------------------------------------------- tarih

    private fun formatDate(sec: Section, value: Double, date1904: Boolean, loc: FormatLocale): String {
        if (value < 0 || value > ExcelDate.MAX_SERIAL + 1) return "#".repeat(8)
        val subDigits = sec.subSecondDigits
        val unitMs = when (subDigits) {
            0 -> 1000L
            1 -> 100L
            2 -> 10L
            else -> 1L
        }
        var totalMs = (value * 86_400_000.0).roundToLong()
        totalMs = (totalMs.toDouble() / unitMs).roundToLong() * unitMs
        val days = totalMs / 86_400_000L
        val msOfDay = totalMs % 86_400_000L
        val date = ExcelDate.dayToDate(days, date1904)
        val hour = (msOfDay / 3_600_000L).toInt()
        val minute = ((msOfDay / 60_000L) % 60).toInt()
        val second = ((msOfDay / 1000L) % 60).toInt()
        val millis = (msOfDay % 1000L).toInt()
        val sb = StringBuilder()
        for (t in sec.tokens) {
            when (t) {
                is Tok.Lit -> sb.append(t.text)
                is Tok.DateTok -> when (t.kind) {
                    DateKind.YEAR -> if (t.count <= 2) sb.append(pad2(date.year % 100)) else sb.append(date.year)
                    DateKind.MONTH, DateKind.MONTH_OR_MINUTE -> when (t.count) {
                        1 -> sb.append(date.monthValue)
                        2 -> sb.append(pad2(date.monthValue))
                        3 -> sb.append(loc.monthsShort[date.monthValue - 1])
                        4 -> sb.append(loc.monthsLong[date.monthValue - 1])
                        else -> sb.append(loc.monthsLong[date.monthValue - 1].first())
                    }
                    DateKind.MINUTE -> if (t.count == 1) sb.append(minute) else sb.append(pad2(minute))
                    DateKind.DAY -> when (t.count) {
                        1 -> sb.append(date.dayOfMonth)
                        2 -> sb.append(pad2(date.dayOfMonth))
                        3 -> sb.append(loc.daysShort[date.dayOfWeek.value - 1])
                        else -> sb.append(loc.daysLong[date.dayOfWeek.value - 1])
                    }
                    DateKind.HOUR -> {
                        val h = if (sec.hasAmPm) (if (hour % 12 == 0) 12 else hour % 12) else hour
                        if (t.count == 1) sb.append(h) else sb.append(pad2(h))
                    }
                    DateKind.SECOND -> if (t.count == 1) sb.append(second) else sb.append(pad2(second))
                }
                is Tok.SubSecond -> {
                    sb.append(loc.decimalSeparator)
                    sb.append(millis.toString().padStart(3, '0').substring(0, t.digits.coerceIn(1, 3)))
                }
                is Tok.AmPm -> {
                    val txt = if (hour < 12) loc.am else loc.pm
                    if (t.short) sb.append(if (t.lower) txt.first().lowercaseChar() else txt.first()) else sb.append(txt)
                }
                is Tok.Elapsed -> {
                    val amount = when (t.unit) {
                        'h' -> totalMs / 3_600_000L
                        'm' -> totalMs / 60_000L
                        else -> totalMs / 1000L
                    }
                    sb.append(amount.toString().padStart(t.count, '0'))
                }
                is Tok.Digit -> sb.append(t.ch)
                is Tok.Dot -> sb.append('.')
                is Tok.Comma -> sb.append(',')
                is Tok.Percent -> sb.append('%')
                is Tok.Slash -> sb.append('/')
                else -> {}
            }
        }
        return sb.toString()
    }

    private fun pad2(v: Int): String = if (v < 10) "0$v" else v.toString()

    // ---------------------------------------------------------------- ayrıştırma

    internal enum class DateKind { YEAR, MONTH, MINUTE, MONTH_OR_MINUTE, DAY, HOUR, SECOND }

    internal sealed class Tok {
        class Lit(val text: String) : Tok()
        class Digit(val ch: Char) : Tok()
        object Dot : Tok()
        object Comma : Tok()
        object Percent : Tok()
        class Exp(val plus: Boolean) : Tok()
        object Slash : Tok()
        object At : Tok()
        object General : Tok()
        class DateTok(var kind: DateKind, val count: Int) : Tok()
        class AmPm(val short: Boolean, val lower: Boolean) : Tok()
        class Elapsed(val unit: Char, val count: Int) : Tok()
        class SubSecond(val digits: Int) : Tok()
    }

    internal class Section(rawTokens: List<Tok>, val color: Int?, val condOp: String?, val condValue: Double) {
        val tokens: List<Tok>
        val isDate: Boolean
        val hasAt: Boolean
        val hasAmPm: Boolean
        val isGeneralOnly: Boolean
        val subSecondDigits: Int
        val dotIndex: Int
        val expIndex: Int
        val slashIndex: Int
        val percentCount: Int
        var scaleCommas: Int = 0
            private set
        var grouping: Boolean = false
            private set
        val intTokens: List<Tok>
        val fracTokens: List<Tok>
        val fracDigitCount: Int

        init {
            isDate = rawTokens.any { it is Tok.DateTok || it is Tok.AmPm || it is Tok.Elapsed }
            hasAt = rawTokens.any { it is Tok.At }
            hasAmPm = rawTokens.any { it is Tok.AmPm }
            if (isDate) resolveMinutes(rawTokens)
            tokens = if (isDate) rawTokens else analyseCommas(rawTokens)
            isGeneralOnly = tokens.any { it is Tok.General } && tokens.none { it is Tok.Digit }
            subSecondDigits = tokens.filterIsInstance<Tok.SubSecond>().maxOfOrNull { it.digits } ?: 0
            expIndex = if (isDate) -1 else tokens.indexOfFirst { it is Tok.Exp }
            val dot = if (isDate) -1 else tokens.indexOfFirst { it is Tok.Dot }
            dotIndex = if (expIndex >= 0 && dot > expIndex) -1 else dot
            slashIndex = if (isDate || expIndex >= 0) -1 else {
                val s = tokens.indexOfFirst { it is Tok.Slash }
                if (s > 0 && tokens.subList(0, s).any { it is Tok.Digit }) s else -1
            }
            percentCount = tokens.count { it is Tok.Percent }
            val intEnd = when {
                dotIndex >= 0 -> dotIndex
                expIndex >= 0 -> expIndex
                else -> tokens.size
            }
            intTokens = tokens.subList(0, intEnd)
            fracTokens = if (dotIndex >= 0) tokens.subList(dotIndex + 1, if (expIndex > dotIndex) expIndex else tokens.size) else emptyList()
            fracDigitCount = fracTokens.count { it is Tok.Digit }
        }

        fun matches(v: Double): Boolean = when (condOp) {
            "<" -> v < condValue
            "<=" -> v <= condValue
            ">" -> v > condValue
            ">=" -> v >= condValue
            "=" -> v == condValue
            "<>" -> v != condValue
            else -> true
        }

        /** Binlik ayırıcı ve ölçekleme virgüllerini çözümler; virgül belirteçlerini kaldırır. */
        private fun analyseCommas(src: List<Tok>): List<Tok> {
            val lastDigit = src.indexOfLast { it is Tok.Digit }
            val firstDigit = src.indexOfFirst { it is Tok.Digit }
            val dot = src.indexOfFirst { it is Tok.Dot }
            val out = ArrayList<Tok>(src.size)
            for ((i, t) in src.withIndex()) {
                if (t !is Tok.Comma) { out.add(t); continue }
                when {
                    lastDigit in 0 until i -> {
                        // son basamaktan sonra gelen virgüller: her biri 1000'e böler
                        val onlyCommasOrLitBetween = src.subList(lastDigit + 1, i).all { it is Tok.Comma }
                        if (onlyCommasOrLitBetween) scaleCommas++ else out.add(Tok.Lit(","))
                    }
                    firstDigit in 0 until i && (dot < 0 || i < dot) -> grouping = true
                    else -> out.add(Tok.Lit(","))
                }
            }
            return out
        }

        private fun resolveMinutes(list: List<Tok>) {
            val dateIdx = list.indices.filter { list[it] is Tok.DateTok || list[it] is Tok.Elapsed }
            for ((pos, i) in dateIdx.withIndex()) {
                val t = list[i] as? Tok.DateTok ?: continue
                if (t.kind != DateKind.MONTH_OR_MINUTE) continue
                if (t.count >= 3) { t.kind = DateKind.MONTH; continue }
                val prev = dateIdx.getOrNull(pos - 1)?.let { list[it] }
                val next = dateIdx.getOrNull(pos + 1)?.let { list[it] }
                val prevIsHour = (prev is Tok.DateTok && prev.kind == DateKind.HOUR) ||
                    (prev is Tok.Elapsed && prev.unit == 'h')
                val nextIsSecond = (next is Tok.DateTok && next.kind == DateKind.SECOND) ||
                    (next is Tok.Elapsed && next.unit == 's')
                t.kind = if (prevIsHour || nextIsSecond) DateKind.MINUTE else DateKind.MONTH
            }
        }
    }

    companion object {
        private val NAMED_COLORS = mapOf(
            "black" to 0xFF000000.toInt(), "blue" to 0xFF0000FF.toInt(), "cyan" to 0xFF00FFFF.toInt(),
            "green" to 0xFF00FF00.toInt(), "magenta" to 0xFFFF00FF.toInt(), "red" to 0xFFFF0000.toInt(),
            "white" to 0xFFFFFFFF.toInt(), "yellow" to 0xFFFFFF00.toInt(),
        )

        val GENERAL: NumberFormat = parse("General")

        fun parse(code: String?): NumberFormat {
            if (code.isNullOrBlank()) return NumberFormat(emptyList())
            val sections = splitSections(code).map { parseSection(it) }
            val effective = if (sections.size == 1 && sections[0].tokens.size == 1 && sections[0].tokens[0] is Tok.General) {
                emptyList()
            } else sections
            return NumberFormat(effective)
        }

        private fun splitSections(code: String): List<String> {
            val result = ArrayList<String>()
            val sb = StringBuilder()
            var i = 0
            var inQuote = false
            var inBracket = false
            while (i < code.length) {
                val c = code[i]
                when {
                    inQuote -> { sb.append(c); if (c == '"') inQuote = false }
                    c == '"' -> { sb.append(c); inQuote = true }
                    c == '\\' && i + 1 < code.length -> { sb.append(c).append(code[i + 1]); i++ }
                    c == '[' -> { inBracket = true; sb.append(c) }
                    c == ']' -> { inBracket = false; sb.append(c) }
                    c == ';' && !inBracket -> { result.add(sb.toString()); sb.setLength(0) }
                    else -> sb.append(c)
                }
                i++
            }
            result.add(sb.toString())
            return result.take(4)
        }

        private fun parseSection(s: String): Section {
            val tokens = ArrayList<Tok>()
            var color: Int? = null
            var condOp: String? = null
            var condValue = 0.0
            val lit = StringBuilder()
            fun flush() {
                if (lit.isNotEmpty()) { tokens.add(Tok.Lit(lit.toString())); lit.setLength(0) }
            }
            fun add(t: Tok) { flush(); tokens.add(t) }
            fun countRun(start: Int, ch: Char): Int {
                var j = start
                while (j < s.length && s[j].equals(ch, ignoreCase = true)) j++
                return j - start
            }
            var i = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '"' -> {
                        val end = s.indexOf('"', i + 1).let { if (it < 0) s.length else it }
                        lit.append(s, i + 1, end)
                        i = end + 1
                        continue
                    }
                    c == '\\' -> { if (i + 1 < s.length) lit.append(s[i + 1]); i += 2; continue }
                    c == '_' -> { lit.append(' '); i += 2; continue }
                    c == '*' -> { i += 2; continue }
                    c == '[' -> {
                        val end = s.indexOf(']', i + 1).let { if (it < 0) s.length else it }
                        val content = s.substring(i + 1, end)
                        val lower = content.lowercase()
                        when {
                            NAMED_COLORS.containsKey(lower) -> color = NAMED_COLORS[lower]
                            lower.startsWith("color") -> {
                                val n = lower.removePrefix("color").toIntOrNull()
                                if (n != null && n in 1..56) color = Colors.DEFAULT_PALETTE[7 + n]
                            }
                            content.isNotEmpty() && content[0] in "<>=" -> {
                                val op = content.takeWhile { it in "<>=" }
                                val num = content.substring(op.length).trim().replace(',', '.').toDoubleOrNull()
                                if (num != null) { condOp = op; condValue = num }
                            }
                            content.startsWith("$") -> {
                                val sym = content.substring(1).substringBefore('-')
                                if (sym.isNotEmpty()) lit.append(sym)
                            }
                            lower.isNotEmpty() && lower.all { it == 'h' } -> add(Tok.Elapsed('h', lower.length))
                            lower.isNotEmpty() && lower.all { it == 'm' } -> add(Tok.Elapsed('m', lower.length))
                            lower.isNotEmpty() && lower.all { it == 's' } -> add(Tok.Elapsed('s', lower.length))
                            else -> {}
                        }
                        i = end + 1
                        continue
                    }
                    s.regionMatches(i, "General", 0, 7, ignoreCase = true) -> { add(Tok.General); i += 7; continue }
                    s.regionMatches(i, "AM/PM", 0, 5, ignoreCase = true) -> {
                        add(Tok.AmPm(short = false, lower = false)); i += 5; continue
                    }
                    s.regionMatches(i, "A/P", 0, 3, ignoreCase = true) -> {
                        add(Tok.AmPm(short = true, lower = c == 'a')); i += 3; continue
                    }
                    c == '0' || c == '#' || c == '?' -> add(Tok.Digit(c))
                    c == '.' -> {
                        val prev = tokens.lastOrNull()
                        if (lit.isEmpty() && prev is Tok.DateTok && prev.kind == DateKind.SECOND && i + 1 < s.length && s[i + 1] == '0') {
                            val n = countRun(i + 1, '0')
                            add(Tok.SubSecond(n))
                            i += 1 + n
                            continue
                        }
                        add(Tok.Dot)
                    }
                    c == ',' -> add(Tok.Comma)
                    c == '%' -> add(Tok.Percent)
                    (c == 'E' || c == 'e') && i + 1 < s.length && (s[i + 1] == '+' || s[i + 1] == '-') -> {
                        add(Tok.Exp(plus = s[i + 1] == '+')); i += 2; continue
                    }
                    c == '/' -> add(Tok.Slash)
                    c == '@' -> add(Tok.At)
                    c == 'y' || c == 'Y' -> {
                        val n = countRun(i, 'y')
                        add(Tok.DateTok(DateKind.YEAR, n)); i += n; continue
                    }
                    c == 'm' || c == 'M' -> {
                        val n = countRun(i, 'm'); add(Tok.DateTok(DateKind.MONTH_OR_MINUTE, n)); i += n; continue
                    }
                    c == 'd' || c == 'D' -> {
                        val n = countRun(i, 'd'); add(Tok.DateTok(DateKind.DAY, n)); i += n; continue
                    }
                    c == 'h' || c == 'H' -> {
                        val n = countRun(i, 'h'); add(Tok.DateTok(DateKind.HOUR, n)); i += n; continue
                    }
                    c == 's' || c == 'S' -> {
                        val n = countRun(i, 's'); add(Tok.DateTok(DateKind.SECOND, n)); i += n; continue
                    }
                    else -> lit.append(c)
                }
                i++
            }
            flush()
            val isDate = tokens.any { it is Tok.DateTok || it is Tok.AmPm || it is Tok.Elapsed }
            // Tarih biçimlerinde '/' bir ayraçtır.
            val finalTokens = if (isDate) tokens.map { if (it is Tok.Slash) Tok.Lit("/") else it } else tokens
            return Section(finalTokens, color, condOp, condValue)
        }

        internal fun roundHalfUp(v: Double, decimals: Int): BigDecimal =
            BigDecimal(v.toString()).round(MathContext(15, RoundingMode.HALF_UP)).setScale(decimals, RoundingMode.HALF_UP)

        /** Excel "Genel" biçimi. */
        fun formatGeneral(v: Double, loc: FormatLocale): String {
            if (v.isNaN() || v.isInfinite()) return "#SAYI!"
            if (v == 0.0) return "0"
            val a = abs(v)
            val text = when {
                a >= 1e15 || a < 1e-9 -> {
                    val bd = BigDecimal(v).round(MathContext(6, RoundingMode.HALF_UP))
                    val exp = bd.precision() - bd.scale() - 1
                    val mant = bd.movePointLeft(exp).stripTrailingZeros().toPlainString()
                    mant + "E" + (if (exp < 0) "-" else "+") + abs(exp).toString().padStart(2, '0')
                }
                v == floor(v) -> v.toLong().toString()
                else -> BigDecimal(v.toString()).round(MathContext(10, RoundingMode.HALF_UP))
                    .stripTrailingZeros().toPlainString()
            }
            return if (loc.decimalSeparator != '.') text.replace('.', loc.decimalSeparator) else text
        }
    }
}
