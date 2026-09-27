package com.lifeline.app.emergency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SosCodecTest {

    // Synthetic open-ocean coordinates; never real device locations.
    private val syntheticLocation = SosLocation(latitude = 1.23456, longitude = 2.34567, accuracyMeters = 12f)

    private fun sos(
        description: String = "Person unconscious, breathing",
        location: SosLocation? = syntheticLocation,
        type: EmergencyType = EmergencyType.MEDICAL
    ) = SosPayload(
        id = "0123456789abcdef",
        type = type,
        description = description,
        timestampMs = 1_700_000_000_000L,
        location = location
    )

    @Test
    fun roundTripsWithLocation() {
        val original = sos()
        val decoded = SosCodec.decode(SosCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun roundTripsWithoutLocation() {
        val original = sos(location = null)
        assertEquals(original, SosCodec.decode(SosCodec.encode(original)))
    }

    @Test
    fun roundTripsEmptyDescription() {
        val original = sos(description = "")
        assertEquals(original, SosCodec.decode(SosCodec.encode(original)))
    }

    @Test
    fun firstLineIsHumanReadableForClientsWithoutSosSupport() {
        val firstLine = SosCodec.encode(sos()).lines().first()
        assertEquals("🆘 SOS [MEDICAL] Person unconscious, breathing", firstLine)
    }

    @Test
    fun encodeFlattensNewlinesAndTruncatesDescription() {
        val long = "line one\nline two\r\n" + "x".repeat(500)
        val decoded = SosCodec.decode(SosCodec.encode(sos(description = long)))
        assertNotNull(decoded)
        assertTrue(decoded!!.description.startsWith("line one line two x"))
        assertEquals(SosCodec.MAX_DESCRIPTION_CHARS, decoded.description.length)
    }

    @Test
    fun encodeUsesDotDecimalsRegardlessOfDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val encoded = SosCodec.encode(sos())
            assertTrue(encoded, encoded.contains("loc=1.23456,2.34567"))
            assertEquals(sos(), SosCodec.decode(encoded))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun ordinaryChatTextIsNotSos() {
        assertNull(SosCodec.decode("hello mesh"))
        assertNull(SosCodec.decode("🆘 SOS [FIRE] typed by hand, no metadata"))
        assertNull(SosCodec.decode(""))
    }

    @Test
    fun rejectsMalformedMetadata() {
        val good = SosCodec.encode(sos())
        assertNull(SosCodec.decode(good.replace("id=0123456789abcdef", "id=not-hex")))
        assertNull(SosCodec.decode(good.replace("id=0123456789abcdef", "id=abc")))
        assertNull(SosCodec.decode(good.replace(Regex("ts=\\d+"), "ts=soon")))
        assertNull(SosCodec.decode(good.replace(Regex(" ts=\\d+"), "")))
        assertNull(SosCodec.decode(good.replace("loc=1.23456,2.34567", "loc=91.0,2.0")))
        assertNull(SosCodec.decode(good.replace("loc=1.23456,2.34567", "loc=1.0,181.0")))
        assertNull(SosCodec.decode(good.replace("loc=1.23456,2.34567", "loc=NaN,2.0")))
    }

    @Test
    fun unknownTypeFromNewerClientDegradesToOther() {
        val encoded = SosCodec.encode(sos()).replace("t=MEDICAL", "t=AVALANCHE")
        assertEquals(EmergencyType.OTHER, SosCodec.decode(encoded)?.type)
    }

    @Test
    fun unknownMetadataKeysAreIgnored() {
        val encoded = SosCodec.encode(sos()) + " future=1"
        assertEquals(sos(), SosCodec.decode(encoded))
    }

    @Test
    fun newIdIsSixteenHexChars() {
        val id = SosCodec.newId()
        assertTrue(id, Regex("[0-9a-f]{16}").matches(id))
    }
}
