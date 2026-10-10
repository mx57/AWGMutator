package com.example

import android.content.Context
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataExtractionRulesTest {

    @Test
    fun testDataExtractionRulesXmlParsing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser: XmlResourceParser = context.resources.getXml(R.xml.data_extraction_rules)
        assertNotNull("XmlResourceParser should not be null for data_extraction_rules", parser)

        val tagNames = mutableListOf<String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                tagNames.add(parser.name)
            }
            eventType = parser.next()
        }

        assertTrue("Root tag should be data-extraction-rules", tagNames.contains("data-extraction-rules"))
        assertTrue("Tag list should contain cloud-backup", tagNames.contains("cloud-backup"))
        assertTrue("Tag list should contain device-transfer", tagNames.contains("device-transfer"))
        assertTrue("Tag list should contain include", tagNames.contains("include"))
        assertTrue("Tag list should contain exclude", tagNames.contains("exclude"))
    }

    @Test
    fun testBackupRulesXmlParsing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser: XmlResourceParser = context.resources.getXml(R.xml.backup_rules)
        assertNotNull("XmlResourceParser should not be null for backup_rules", parser)

        val tagNames = mutableListOf<String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                tagNames.add(parser.name)
            }
            eventType = parser.next()
        }

        assertTrue("Root tag should be full-backup-content", tagNames.contains("full-backup-content"))
        assertTrue("Tag list should contain include", tagNames.contains("include"))
        assertTrue("Tag list should contain exclude", tagNames.contains("exclude"))
    }
}
