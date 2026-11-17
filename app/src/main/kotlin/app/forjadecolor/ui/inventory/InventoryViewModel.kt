package app.forjadecolor.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forjadecolor.AppContainer
import app.forjadecolor.domain.paints.Inventory
import app.forjadecolor.domain.paints.Brush
import app.forjadecolor.domain.paints.Paint
import app.forjadecolor.domain.paints.addBrush
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The owned paints and brushes, and saving a paint, for [InventoryScreen] and the colour wheel. */
class InventoryViewModel(private val container: AppContainer) : ViewModel() {
    private val _inventory = MutableStateFlow<Inventory?>(null)

    /** Null until loaded. */
    val inventory: StateFlow<Inventory?> = _inventory.asStateFlow()

    suspend fun load() {
        _inventory.value = container.repository.getInventory()
    }

    /** Adds or replaces the paint through [savePaint], then shows the inventory again. */
    fun save(paint: Paint) {
        viewModelScope.launch {
            savePaint(container.repository, paint)
            load()
        }
    }

    /** Adds the brush, or updates the one with the same code, then shows the inventory again. */
    fun saveBrush(brush: Brush) {
        viewModelScope.launch {
            val repo = container.repository
            repo.saveInventory(repo.getInventory().addBrush(brush))
            load()
        }
    }
}
