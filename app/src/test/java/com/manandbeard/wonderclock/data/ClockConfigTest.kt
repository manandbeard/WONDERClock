package com.manandbeard.wonderclock.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Configs are persisted as JSON and can be copied between devices and between
 * app versions, so the format has to survive fields it has never seen.
 */
class ClockConfigTest {

    // Mirrors the format ConfigStore uses. ConfigStore itself needs a Context,
    // so the contract is pinned here instead.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun `every built-in preset survives a round trip`() {
        Presets.all.forEach { preset ->
            val decoded = json.decodeFromString<ClockConfig>(json.encodeToString(preset.config))
            assertEquals("preset ${preset.id} changed on round trip", preset.config, decoded)
        }
    }

    @Test
    fun `an empty object decodes to the defaults`() {
        assertEquals(ClockConfig(), json.decodeFromString<ClockConfig>("{}"))
    }

    @Test
    fun `unknown fields from a newer version are ignored`() {
        val fromTheFuture = """{"style":"ANALOG","somethingWeHaveNotInventedYet":42}"""
        val decoded = json.decodeFromString<ClockConfig>(fromTheFuture)
        assertEquals(ClockStyle.ANALOG, decoded.style)
    }

    @Test
    fun `a missing time zone means follow the device`() {
        assertNull(ClockConfig().timeZoneId)
        assertNull(json.decodeFromString<ClockConfig>("{}").timeZoneId)
    }

    @Test
    fun `preset ids are unique and resolvable`() {
        val ids = Presets.all.map { it.id }
        assertEquals("duplicate preset ids", ids.size, ids.toSet().size)
        ids.forEach { id -> assertEquals(id, Presets.byId(id)?.id) }
    }

    @Test
    fun `every preset tags itself with its own id`() {
        Presets.all.forEach { preset ->
            assertEquals(
                "preset ${preset.id} carries the wrong presetId",
                preset.id,
                preset.config.presetId,
            )
        }
    }

    @Test
    fun `every clock style has a starting preset`() {
        ClockStyle.entries.forEach { style ->
            val preset = Presets.forStyle(style)
            assertTrue("no preset for $style", preset.id.isNotBlank())
        }
    }

    @Test
    fun `a second tap zone needs a distinct action`() {
        assertTrue(!ClockConfig().hasSecondaryTapZone)
        val same = ClockConfig(tapAction = TapAction.CLOCK, tapActionSecondary = TapAction.CLOCK)
        assertTrue("identical actions should not split the widget", !same.hasSecondaryTapZone)
        val split = ClockConfig(
            tapAction = TapAction.CLOCK,
            tapActionSecondary = TapAction.CALENDAR,
        )
        assertTrue(split.hasSecondaryTapZone)
    }

    @Test
    fun `colours keep their alpha through a round trip`() {
        val translucent = ClockConfig(timeColor = 0x80FF0000.toInt())
        val decoded = json.decodeFromString<ClockConfig>(json.encodeToString(translucent))
        assertEquals(0x80FF0000.toInt(), decoded.timeColor)
        assertNotEquals(0xFFFF0000.toInt(), decoded.timeColor)
    }
}
