package app.forjadecolor.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.advice.AdvicePackage
import app.forjadecolor.domain.advice.SendResult
import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.PaintRef
import app.forjadecolor.domain.paints.findBrush
import app.forjadecolor.domain.paints.findPaint
import app.forjadecolor.domain.paints.findSet
import app.forjadecolor.domain.paints.setAsScan
import app.forjadecolor.domain.scan.ScanResult
import app.forjadecolor.domain.scan.applyScan
import app.forjadecolor.domain.scan.buildScanPrompt
import app.forjadecolor.domain.scan.checkInCatalogue
import app.forjadecolor.domain.scan.isComplete
import app.forjadecolor.domain.scan.parseScanAnswer
import app.forjadecolor.domain.storage.StoredBlob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanSendStatus { NONE, PHOTO_MISSING, FALLBACK }

/** A paint read from the photo, waiting for the user: [existing] is the owned paint it would update. */
data class PaintItem(val paint: Paint, val existing: Paint?, val included: Boolean, val fromCatalogue: Boolean = false) {
    val complete: Boolean get() = isComplete(paint)
    val updates: Boolean get() = existing != null
}

/** A brush read from the photo, waiting for the user: [updates] when it would replace an owned brush. */
data class BrushItem(val brush: Brush, val updates: Boolean, val included: Boolean) {
    val complete: Boolean get() = brush.code.isNotBlank() && brush.name.isNotBlank()
}

/** The list the user confirms; null until an answer was read. */
data class ScanList(val paints: List<PaintItem>, val brushes: List<BrushItem>) {
    val toSave: Int get() = paints.count { it.included && it.complete } + brushes.count { it.included && it.complete }
}

data class ScanState(
    val sendStatus: ScanSendStatus = ScanSendStatus.NONE,
    /** The instructions on screen when sharing was not possible, to copy by hand. */
    val prompt: String? = null,
    val errors: List<String> = emptyList(),
    val list: ScanList? = null,
)

/**
 * Adding paints and brushes from a photo: sends it with the scan instructions, reads the AI's list and
 * keeps it until the user confirms. Nothing reaches the inventory before [save].
 */
class ScanViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    /**
     * Shares the photo and the instructions. The photo is stored only for the bridge to read it and is
     * deleted right after: it belongs to no project.
     */
    fun send(photo: ByteArray?) {
        if (photo == null) {
            _state.update { it.copy(sendStatus = ScanSendStatus.PHOTO_MISSING) }
            return
        }
        viewModelScope.launch {
            val repo = container.repository
            val id = repo.putBlob(StoredBlob("image/jpeg", photo))
            val result = try {
                container.advisorBridge.send(AdvicePackage(buildScanPrompt(), id))
            } finally {
                repo.deleteBlob(id)
            }
            _state.update {
                if (result == SendResult.FALLBACK) it.copy(sendStatus = ScanSendStatus.FALLBACK, prompt = buildScanPrompt())
                else it.copy(sendStatus = ScanSendStatus.NONE, prompt = null)
            }
        }
    }

    fun readAnswer(): String? = container.advisorBridge.readAnswer()

    private var setLoaded = false

    /** Opens a paint set as the list to confirm, once: a recreated screen keeps the user's ticks and edits. */
    fun loadSet(code: String) {
        if (setLoaded) return
        setLoaded = true
        val set = findSet(code) ?: return
        viewModelScope.launch {
            val list = setAsScan(set, container.repository.getInventory())
            _state.update {
                it.copy(list = ScanList(list.paints.map { s -> PaintItem(s.paint, s.existing, isComplete(s.paint) && s.existing == null, s.fromCatalogue) }, emptyList()))
            }
        }
    }

    /** Reads the answer into the list, every complete item ticked; an unusable answer leaves only errors. */
    fun read(raw: String) {
        viewModelScope.launch {
            val inv = container.repository.getInventory()
            // The catalogue is checked first; the AI's data is kept only for paints it does not list.
            when (val result = parseScanAnswer(raw, inv).let { if (it is ScanResult.Success) checkInCatalogue(it, inv) else it }) {
                is ScanResult.Failure -> _state.update { it.copy(errors = result.errors) }
                is ScanResult.Success -> _state.update {
                    it.copy(
                        errors = emptyList(),
                        list = ScanList(
                            // A paint the user already owns comes unticked: ticking it updates the owned one.
                            result.paints.map { s -> PaintItem(s.paint, s.existing, isComplete(s.paint) && s.existing == null, s.fromCatalogue) },
                            result.brushes.map { s -> BrushItem(s.brush, s.existing != null, s.brush.code.isNotBlank() && s.brush.name.isNotBlank()) },
                        ),
                    )
                }
            }
        }
    }

    fun clearErrors() = _state.update { it.copy(errors = emptyList()) }

    fun discard() = _state.update { it.copy(list = null) }

    fun togglePaint(index: Int) = editList { l ->
        l.copy(paints = l.paints.mapIndexed { i, p -> if (i == index && p.complete) p.copy(included = !p.included) else p })
    }

    fun toggleBrush(index: Int) = editList { l ->
        l.copy(brushes = l.brushes.mapIndexed { i, b -> if (i == index && b.complete) b.copy(included = !b.included) else b })
    }

    /** The user corrected a paint: it is ticked again, and checked again against the inventory. */
    fun correctPaint(index: Int, paint: Paint) {
        viewModelScope.launch {
            val inv = container.repository.getInventory()
            editList { l ->
                l.copy(paints = l.paints.mapIndexed { i, p -> if (i == index) PaintItem(paint, owned(inv, paint), isComplete(paint)) else p })
            }
        }
    }

    fun correctBrush(index: Int, brush: Brush) {
        viewModelScope.launch {
            val inv = container.repository.getInventory()
            editList { l ->
                l.copy(brushes = l.brushes.mapIndexed { i, b -> if (i == index) BrushItem(brush, inv.findBrush(brush.code) != null, true) else b })
            }
        }
    }

    /** Saves the ticked, complete items through [applyScan], then [onSaved]. */
    fun save(onSaved: () -> Unit) {
        val list = _state.value.list ?: return
        viewModelScope.launch {
            val repo = container.repository
            val paints = list.paints.filter { it.included && it.complete }.map { it.paint }
            val brushes = list.brushes.filter { it.included && it.complete }.map { it.brush }
            repo.saveInventory(repo.getInventory().applyScan(paints, brushes))
            _state.update { it.copy(list = null) }
            onSaved()
        }
    }

    private fun owned(inv: Inventory, p: Paint) = if (p.code.isBlank()) null else inv.findPaint(PaintRef(p.brand, p.code))

    private fun editList(change: (ScanList) -> ScanList) = _state.update { s -> s.copy(list = s.list?.let(change)) }
}
