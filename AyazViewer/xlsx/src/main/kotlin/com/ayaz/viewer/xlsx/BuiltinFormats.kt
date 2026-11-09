package com.ayaz.viewer.xlsx

/** Excel'in yerleşik sayı biçimleri (numFmtId 0-49). Tarih biçimleri Türkçe kullanıma uyarlanmıştır. */
object BuiltinFormats {

    fun code(id: Int): String? = when (id) {
        0 -> "General"
        1 -> "0"
        2 -> "0.00"
        3 -> "#,##0"
        4 -> "#,##0.00"
        5 -> "#,##0_);(#,##0)"
        6 -> "#,##0_);[Red](#,##0)"
        7 -> "#,##0.00_);(#,##0.00)"
        8 -> "#,##0.00_);[Red](#,##0.00)"
        9 -> "0%"
        10 -> "0.00%"
        11 -> "0.00E+00"
        12 -> "# ?/?"
        13 -> "# ??/??"
        14 -> SHORT_DATE
        15 -> "d-mmm-yy"
        16 -> "d-mmm"
        17 -> "mmm-yy"
        18 -> "h:mm AM/PM"
        19 -> "h:mm:ss AM/PM"
        20 -> "h:mm"
        21 -> "h:mm:ss"
        22 -> "$SHORT_DATE h:mm"
        in 27..36, in 50..58 -> SHORT_DATE
        37 -> "#,##0_);(#,##0)"
        38 -> "#,##0_);[Red](#,##0)"
        39 -> "#,##0.00_);(#,##0.00)"
        40 -> "#,##0.00_);[Red](#,##0.00)"
        41 -> "_(* #,##0_);_(* (#,##0);_(* \"-\"_);_(@_)"
        42 -> "_(* #,##0_);_(* (#,##0);_(* \"-\"_);_(@_)"
        43 -> "_(* #,##0.00_);_(* (#,##0.00);_(* \"-\"??_);_(@_)"
        44 -> "_(* #,##0.00_);_(* (#,##0.00);_(* \"-\"??_);_(@_)"
        45 -> "mm:ss"
        46 -> "[h]:mm:ss"
        47 -> "mm:ss.0"
        48 -> "##0.0E+0"
        49 -> "@"
        else -> null
    }

    /** numFmtId=14 yerel kısa tarih biçimidir; Türkiye için gg.aa.yyyy. */
    const val SHORT_DATE = "dd.mm.yyyy"
}
