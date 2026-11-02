package app.fieldwatch.domain

import java.util.Locale

/**
 * Country and operator for a stored network code. The code stays on screen.
 * A missing name leaves that line off. This is who the code is assigned to.
 */
object CellPlmn {
    fun country(plmn: String): String? {
        val iso = CellPlmnData.countryIso[mcc(plmn) ?: return null] ?: return null
        val name = Locale.Builder().setRegion(iso.uppercase(Locale.US)).build()
            .getDisplayCountry(LanguageText.dateLocale())
        if (name.isBlank() || name.equals(iso, ignoreCase = true)) return null
        return name
    }

    fun operator(plmn: String): String? {
        if (mcc(plmn) == null) return null
        return CellPlmnData.operator[plmn]
    }

    private fun mcc(plmn: String): Int? {
        val parts = plmn.split('-')
        if (parts.size != 2) return null
        val mcc = parts[0]
        val mnc = parts[1]
        if (mcc.length != 3 || mnc.length !in 2..3) return null
        if (!mcc.all { it.isDigit() } || !mnc.all { it.isDigit() }) return null
        return mcc.toInt()
    }
}
