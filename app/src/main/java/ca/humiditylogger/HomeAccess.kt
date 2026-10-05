package ca.humiditylogger

/** App-level permission state; UNAVAILABLE identifies an explicitly SDK-free verification build. */
enum class HomePermissionState { GRANTED, NOT_GRANTED, UNAVAILABLE }

/** Consent result without proprietary SDK types, keeping shared UI and worker code testable. */
data class HomePermissionResult(val granted: Boolean, val status: String, val errorMessage: String?)

/** Unselected snapshots and local diagnostics; diagnostics may contain private home/device names. */
data class SampleResult(val readings: List<Reading>, val diagnostics: List<String>)
