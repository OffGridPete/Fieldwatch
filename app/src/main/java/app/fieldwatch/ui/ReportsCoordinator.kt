package app.fieldwatch.ui

import android.content.Intent
import app.fieldwatch.FieldwatchApp
import app.fieldwatch.domain.CandidateReport
import app.fieldwatch.domain.SignatureCandidates
import app.fieldwatch.domain.Sit
import app.fieldwatch.domain.SitDiff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CandidatesUi(
    val loading: Boolean = false,
    val report: CandidateReport? = null,
    val error: String? = null,
)

data class SitDiffUi(
    val loading: Boolean = false,
    val result: SitDiff.Result? = null,
    val error: String? = null,
)

/**
 * Reports-screen work: sit lifecycle, sit-vs-sit diff, and the signature
 * candidate sweep over the log. Notices and share intents go through
 * [exports] so progress UI stays on one flow.
 */
class ReportsCoordinator(
    private val app: FieldwatchApp,
    private val scope: CoroutineScope,
    private val exports: ExportCoordinator,
) {
    private val _candidates = MutableStateFlow(CandidatesUi())
    val candidates: StateFlow<CandidatesUi> = _candidates

    private val _sitDiff = MutableStateFlow(SitDiffUi())
    val sitDiff: StateFlow<SitDiffUi> = _sitDiff

    fun defaultSitName(): String = Sit.defaultName(System.currentTimeMillis())

    fun startSit(name: String) {
        scope.launch {
            val heard = app.devices.devices.value.filter { !it.gone }
            app.sits.start(name, heard, app.config.fleets, app.config.watchlist)
            publishSitNotice()
        }
    }

    fun endSit() {
        scope.launch {
            app.sits.end(app.config.fleets)
            publishSitNotice()
        }
    }

    fun renameSit(id: String, name: String) {
        scope.launch { app.sits.rename(id, name) }
    }

    fun deleteSit(id: String) {
        scope.launch { app.sits.delete(id) }
    }

    fun deleteAllSits() {
        scope.launch { app.sits.deleteAllClosed() }
    }

    fun selectSit(id: String?) {
        app.sits.select(id)
    }

    fun startSitDiff(aId: String, bId: String) {
        if (_sitDiff.value.loading) return
        scope.launch {
            _sitDiff.value = SitDiffUi(loading = true)
            runCatching {
                val a = app.sits.sitFile(aId) ?: error("First sit is gone")
                val b = app.sits.sitFile(bId) ?: error("Second sit is gone")
                withContext(Dispatchers.Default) { SitDiff.diff(a, b) }
            }.onSuccess { result ->
                _sitDiff.value = SitDiffUi(result = result)
            }.onFailure { err ->
                _sitDiff.value = SitDiffUi(error = err.message ?: "Could not compare sits")
            }
        }
    }

    fun closeSitDiff() {
        _sitDiff.value = SitDiffUi()
    }

    fun shareSitDiff() {
        val result = _sitDiff.value.result ?: return
        if (exports.active) return
        val text = SitDiff.toText(result, app.config.fleets)
        exports.share(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Fieldwatch sit diff — ${result.a.name} vs ${result.b.name}")
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Sit diff",
        )
    }

    private fun publishSitNotice() {
        val notice = app.sits.ui.value.notice ?: return
        exports.notice("Sits", notice)
        app.sits.consumeNotice()
    }

    fun startSignatureCandidates() {
        if (_candidates.value.loading) return
        scope.launch {
            _candidates.value = CandidatesUi(loading = true)
            runCatching {
                val radios = app.logs.readRadios()
                withContext(Dispatchers.Default) {
                    SignatureCandidates.analyze(radios, app.config.fleets)
                }
            }.onSuccess { report ->
                _candidates.value = CandidatesUi(report = report)
            }.onFailure { err ->
                _candidates.value = CandidatesUi(error = err.message ?: "Could not read the log")
            }
        }
    }
}
