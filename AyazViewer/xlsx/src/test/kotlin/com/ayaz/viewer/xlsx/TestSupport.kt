package com.ayaz.viewer.xlsx

import org.kxml2.io.KXmlParser
import java.io.File

object TestSupport {
    val parserFactory: () -> org.xmlpull.v1.XmlPullParser = { KXmlParser() }

    fun resource(name: String): File {
        val url = requireNotNull(TestSupport::class.java.classLoader.getResource(name)) { "missing resource $name" }
        return File(url.toURI())
    }

    fun open(name: String): XlsxReader = XlsxReader(resource(name), parserFactory)
}
