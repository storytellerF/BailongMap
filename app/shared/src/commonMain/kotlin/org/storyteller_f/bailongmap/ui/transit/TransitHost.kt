package org.storyteller_f.bailongmap.ui.transit

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.storyteller_f.bailongmap.data.transit.*

sealed interface TransitImportState {
    data object Idle : TransitImportState
    data object Working : TransitImportState
    data class Review(val info: TransitPackageInfo) : TransitImportState
}
data class TransitUiState(val installed: TransitPackageInfo? = null, val importing: TransitImportState = TransitImportState.Working, val error: String? = null)

/** Owns package-management actions independently of Compose and confines UI state to coordination. */
class TransitHost(private val packages: TransitPackages, coordination: CoroutineDispatcher) {
    private val scope = CoroutineScope(SupervisorJob() + coordination)
    private val mutable = MutableStateFlow(TransitUiState())
    val state = mutable.asStateFlow()
    private val messages = Channel<String>(Channel.BUFFERED)
    val effects = messages.receiveAsFlow()

    init {
        scope.launch { packages.installed.collect { info -> mutable.value = mutable.value.copy(installed = info) } }
        scope.launch { perform { packages.load() } }
    }
    fun preview(document: String) = scope.launch {
        if (mutable.value.importing == TransitImportState.Working) return@launch
        perform { mutable.value = mutable.value.copy(importing = TransitImportState.Review(packages.preview(document))) }
    }
    fun install() = scope.launch {
        if (mutable.value.importing !is TransitImportState.Review) return@launch
        perform { packages.install() }
    }
    fun discard() = scope.launch {
        if (mutable.value.importing != TransitImportState.Working) perform { packages.discard() }
    }
    fun remove() = scope.launch {
        if (mutable.value.importing == TransitImportState.Idle) perform { packages.remove() }
    }
    private suspend fun perform(action: suspend () -> Unit) {
        mutable.value = mutable.value.copy(importing = TransitImportState.Working, error = null)
        try {
            action()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            val message = "公共交通数据操作失败：请检查文件格式、有效期和存储空间"
            mutable.value = mutable.value.copy(error = message)
            messages.send(message)
        }
        finally {
            if (mutable.value.importing == TransitImportState.Working) mutable.value = mutable.value.copy(importing = TransitImportState.Idle)
        }
    }
    fun close() { scope.cancel() }
}
