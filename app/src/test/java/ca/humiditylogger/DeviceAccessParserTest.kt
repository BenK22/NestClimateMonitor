package ca.humiditylogger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAccessParserTest {
    @Test
    fun mapsThermostatTraitsIntoReading() {
        val readings = DeviceAccessParser.thermostats(RESPONSE, timestampMs = 1234L)

        assertEquals(1, readings.size)
        val reading = readings.single()
        assertEquals(1234L, reading.timestampMs)
        assertEquals("Dining Room", reading.source)
        assertEquals("enterprises/project/devices/thermostat", reading.deviceId)
        assertEquals(21.5, reading.temperatureC!!, 0.001)
        assertEquals(48.0, reading.humidityPercent!!, 0.001)
        assertEquals(20.0, reading.heatingSetpointC!!, 0.001)
        assertEquals(24.0, reading.coolingSetpointC!!, 0.001)
        assertEquals("HEATCOOL", reading.systemMode)
        assertEquals("HEATING", reading.runningState)
        assertEquals("OFF", reading.ecoState)
    }

    @Test
    fun ignoresNonThermostatsAndAllowsMissingTraits() {
        val readings = DeviceAccessParser.thermostats(
            """{"devices":[{"name":"light","type":"sdm.devices.types.CAMERA"},
                {"name":"thermostat","type":"sdm.devices.types.THERMOSTAT","traits":{}}]}"""
        )

        assertEquals(1, readings.size)
        assertEquals("Nest Thermostat", readings.single().source)
        assertNull(readings.single().temperatureC)
        assertNull(readings.single().humidityPercent)
    }

    @Test
    fun extractsCodeFromCodeOrRedirectUrl() {
        assertEquals("plain-code", DeviceAccessClient.extractAuthorizationCode("plain-code"))
        assertEquals(
            "code/with+characters",
            DeviceAccessClient.extractAuthorizationCode(
                "https://www.google.com/?code=code%2Fwith%2Bcharacters&scope=sdm"
            ),
        )
    }

    @Test
    fun automaticClipboardImportRequiresTheConfiguredRedirectOrigin() {
        assertTrue(DeviceAccessClient.isExpectedRedirectUrl("https://www.google.com/?code=good"))
        assertFalse(DeviceAccessClient.isExpectedRedirectUrl("https://evil.example/?code=stolen"))
        assertFalse(DeviceAccessClient.isExpectedRedirectUrl("not a url with code=anything"))
        assertFalse(DeviceAccessClient.isExpectedRedirectUrl("http://www.google.com/?code=insecure"))
    }

    @Test
    fun surfacesOAuthAndSdmErrorMessages() {
        assertEquals(
            "Google request failed (400): The OAuth client was deleted.",
            DeviceAccessClient.apiError(400, """{"error":"invalid_client","error_description":"The OAuth client was deleted."}"""),
        )
        assertEquals(
            "Google request failed (403): Permission denied",
            DeviceAccessClient.apiError(403, """{"error":{"message":"Permission denied"}}"""),
        )
    }

    private companion object {
        val RESPONSE = """
            {
              "devices": [{
                "name": "enterprises/project/devices/thermostat",
                "type": "sdm.devices.types.THERMOSTAT",
                "traits": {
                  "sdm.devices.traits.Info": {"customName": "Dining Room"},
                  "sdm.devices.traits.Temperature": {"ambientTemperatureCelsius": 21.5},
                  "sdm.devices.traits.Humidity": {"ambientHumidityPercent": 48},
                  "sdm.devices.traits.ThermostatTemperatureSetpoint": {
                    "heatCelsius": 20, "coolCelsius": 24
                  },
                  "sdm.devices.traits.ThermostatMode": {"mode": "HEATCOOL"},
                  "sdm.devices.traits.ThermostatHvac": {"status": "HEATING"},
                  "sdm.devices.traits.ThermostatEco": {"mode": "OFF"}
                }
              }]
            }
        """.trimIndent()
    }
}
