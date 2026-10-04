package app.fieldwatch.domain

import app.fieldwatch.i18n.localized

/** Operator-facing disclaimer. First-run, Debrief, and AI Export share the same core. */
object FieldwatchDisclaimer {
    val HOBBY: String get() =
        localized("fieldwatch_disclaimer_this_is_a_hobby_project_provided_as", "This is a hobby project, provided as-is under the MIT License. Use at your own risk.")

    val HYPOTHESES: String get() =
        localized("fieldwatch_disclaimer_detections_pattern_matches_moving_with_you_possible", "Detections, pattern matches, “Moving with you” / “possible tail,” Debrief text, and AI Export are hypotheses — not identity, not a legal finding, and not a complete RF capture. Radios that are off, asleep, randomized, cellular-only, or hidden by the OS will not appear.")

    val LIABILITY: String get() =
        localized("fieldwatch_disclaimer_you_are_solely_responsible_for_how_you", "You are solely responsible for how you use this app and for following local law. To the maximum extent permitted by law, Off Grid Pete LLC is not liable for indirect, incidental, special, consequential, or punitive damages arising from its use.")

    val LOCATION: String get() =
        localized("fieldwatch_disclaimer_gps_stamps_are_this_phone_at_hear", "GPS stamps are this phone at hear-time, not the other radio, unless a decode map advertises its own latitude/longitude (Remote ID Location). Sharing a Debrief, sit compare, AI Export (sit or one radio), radio-detail Share as text, or log can take that path off the device. A TAK/CoT feed, when you turn it on, sends markers onto the LAN; that is on the operator.")

    val ACCEPT: String get() =
        localized("fieldwatch_disclaimer_by_checking_the_box_and_continuing_you", "By checking the box and continuing, you accept these terms and the MIT License.")

    const val LICENSE_TITLE = "MIT License"

    /** Body of LICENSE in the repository, without the title line. */
    const val LICENSE_BODY =
        "Copyright (c) 2026 Off Grid Pete LLC\n\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."

    val LICENSE_TEXT = "$LICENSE_TITLE\n\n$LICENSE_BODY"

    val firstRunDisclaimer: String get() = "$HOBBY\n\n$HYPOTHESES\n\n$LIABILITY"

    val firstRun: String get() =
        "$firstRunDisclaimer\n\n$LICENSE_TEXT\n\n$ACCEPT"

    /** Debrief text / PDF. Same core as first-run, without the click-through line. */
    fun report(window: DebriefWindow? = null): String {
        val source = if (window?.sitName != null) {
            localized("fieldwatch_disclaimer_named_sit_radios_heard_in_that_window", "named sit “%1\$s” (radios heard in that window; Live list cap still applied while watching). ", window.sitName)
        } else {
            localized("fieldwatch_disclaimer_the_in_memory_live_set_last_15", "the in-memory live set (last 15 minutes, cap about 400). ")
        }
        return localized("fieldwatch_disclaimer_n_n_n_n_n_n_n", "%1\$s\n\n%2\$s\n\n%3\$s\n\n%4\$s\n\nThis sit report is from %5\$sDo not use it in any situation where safety is in question.", HOBBY, HYPOTHESES, LIABILITY, LOCATION, source)
    }

    fun compare(): String =
        localized("fieldwatch_disclaimer_n_n_n_n_n_n_n_2", "%1\$s\n\n%2\$s\n\n%3\$s\n\n%4\$s\n\nThis compare is two windows of radios this phone heard (kind + MAC). BLE rotation is a new row. Do not use it in any situation where safety is in question.", HOBBY, HYPOTHESES, LIABILITY, LOCATION)

    fun experimentalMarkdown(): String = buildString {
        appendLine(localized("fieldwatch_disclaimer_disclaimer_repeat_this_in_your_answer", "## Disclaimer (repeat this in your answer)"))
        appendLine(HOBBY)
        appendLine(HYPOTHESES)
        appendLine(LIABILITY)
        appendLine(LOCATION)
        appendLine(localized("fieldwatch_disclaimer_do_not_use_fieldwatch_this_paste_or", "Do not use Fieldwatch, this paste, or your analysis in any situation where safety is in question."))
        appendLine(localized("fieldwatch_disclaimer_begin_your_reply_with_this_disclaimer_do", "Begin your reply with this disclaimer. Do not give safety advice."))
    }
}
