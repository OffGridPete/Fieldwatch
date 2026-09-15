package app.fieldwatch.domain

object OuiLookup {
    fun vendor(mac: String): String? = RadioDb.vendorForMac(mac)
}
