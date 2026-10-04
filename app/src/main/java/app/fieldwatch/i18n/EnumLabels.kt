package app.fieldwatch.i18n

import app.fieldwatch.domain.*

/** Display labels are separate from persisted enums and matching inputs. */

fun SignatureClass.displayLabel(): String = when (this) {
    SignatureClass.FINDER -> localized("label_signatureclass_finder", "Finder tags")
    SignatureClass.BEACON -> localized("label_signatureclass_beacon", "Retail beacons")
    SignatureClass.SIGNAGE -> localized("label_signatureclass_signage", "Signage")
    SignatureClass.WEARABLE -> localized("label_signatureclass_wearable", "Wearables")
    SignatureClass.SURVEILLANCE -> localized("label_signatureclass_surveillance", "Surveillance")
    SignatureClass.DRONE -> localized("label_signatureclass_drone", "Drones")
    SignatureClass.HACKING -> localized("label_signatureclass_hacking", "Pentest")
    SignatureClass.BODYWORN -> localized("label_signatureclass_bodyworn", "Body-worn")
    SignatureClass.LAW_ENFORCEMENT -> localized("label_signatureclass_law_enforcement", "Public safety")
    SignatureClass.VEHICLE -> localized("label_signatureclass_vehicle", "Vehicle")
    SignatureClass.GLASSES -> localized("label_signatureclass_glasses", "Glasses")
    SignatureClass.AUDIO -> localized("label_signatureclass_audio", "Audio")
    SignatureClass.CAMERA -> localized("label_signatureclass_camera", "Cameras")
    SignatureClass.THERMOSTAT -> localized("label_signatureclass_thermostat", "Thermostats")
    SignatureClass.LOCK -> localized("label_signatureclass_lock", "Access control")
    SignatureClass.HEALTH -> localized("label_signatureclass_health", "Health")
    SignatureClass.HOME -> localized("label_signatureclass_home", "Home IoT")
    SignatureClass.ISP -> localized("label_signatureclass_isp", "ISP / routers")
    SignatureClass.MESH -> localized("label_signatureclass_mesh", "Mesh")
    SignatureClass.PHONE -> localized("label_signatureclass_phone", "Phones / PCs")
    SignatureClass.OTHER -> localized("label_signatureclass_other", "Other")
}

fun ViewMode.displayLabel(): String = when (this) {
    ViewMode.RADAR -> localized("label_viewmode_radar", "Classic radar")
    ViewMode.LIST -> localized("label_viewmode_list", "Strength list")
    ViewMode.TIMELINE -> localized("label_viewmode_timeline", "Timeline")
    ViewMode.HYBRID -> localized("label_viewmode_hybrid", "Hybrid + sparklines")
    ViewMode.BY_CLASS -> localized("label_viewmode_by_class", "By class")
}

fun AlertVoiceWhat.displayLabel(): String = when (this) {
    AlertVoiceWhat.CLASS -> localized("label_alertvoicewhat_class", "Class")
    AlertVoiceWhat.SIGNATURE -> localized("label_alertvoicewhat_signature", "Signature")
    AlertVoiceWhat.BOTH -> localized("label_alertvoicewhat_both", "Class + signature")
}

fun SignatureListSort.displayLabel(): String = when (this) {
    SignatureListSort.NAME -> localized("label_signaturelistsort_name", "Name A–Z")
    SignatureListSort.CLASS -> localized("label_signaturelistsort_class", "Class A–Z")
}

fun RadioKind.displayLabel(): String = when (this) {
    RadioKind.WIFI -> localized("label_radiokind_wifi", "Wi-Fi")
    RadioKind.BLE -> localized("label_radiokind_ble", "BLE")
}

fun ScanIntensity.displayLabel(): String = when (this) {
    ScanIntensity.SAVER -> localized("label_scanintensity_saver", "Battery saver")
    ScanIntensity.BALANCED -> localized("label_scanintensity_balanced", "Balanced")
    ScanIntensity.PERFORMANCE -> localized("label_scanintensity_performance", "High performance")
}

fun ClassSlice.displayLabel(): String = kind?.displayLabel() ?: localized("label_unmatched", "Unmatched")
