package app.fieldwatch.i18n

/** Stored/transport status remains canonical; display strings follow the current language. */
fun takDetail(value: String): String = when {
    value == "Off" -> localized("tak_off", "Off")
    value == "send failed" -> localized("tak_send_failed", "send failed")
    value == "Privacy mode — feed paused" -> localized("tak_privacy_paused", "Privacy mode — feed paused")
    value == "0 eligible radios (need Extra attention / payload latlon / GPS stamp)" ->
        localized("tak_no_eligible", "0 eligible radios (need Extra attention / payload latlon / GPS stamp)")
    value.startsWith("Host ") && value.endsWith(" did not resolve") ->
        localized("tak_host_unresolved", "Host %1\$s did not resolve", value.removePrefix("Host ").removeSuffix(" did not resolve"))
    else -> value
}
