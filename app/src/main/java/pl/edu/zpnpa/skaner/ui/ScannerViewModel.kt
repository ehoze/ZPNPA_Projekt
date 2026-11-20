package pl.edu.zpnpa.skaner.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.edu.zpnpa.skaner.AppContainer
import pl.edu.zpnpa.skaner.data.DocumentDetails
import pl.edu.zpnpa.skaner.data.DocumentEntity
import java.io.IOException
import java.security.GeneralSecurityException

sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Imported(val id: String) : UiEvent
    data object Saved : UiEvent
    data object Deleted : UiEvent
}

class ScannerViewModel(private val container: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    val repository = container.documents
    val query = saved.getStateFlow("query", "")
    private val _documents = MutableStateFlow<List<DocumentEntity>>(emptyList())
    val documents = _documents.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _archiveError = MutableStateFlow<String?>(null)
    val archiveError = _archiveError.asStateFlow()
    private val retry = MutableStateFlow(0)
    private val _detail = MutableStateFlow<DocumentDetails?>(null)
    val detail = _detail.asStateFlow()
    private val _detailError = MutableStateFlow<String?>(null)
    val detailError = _detailError.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()
    private val _lockEnabled = MutableStateFlow<Boolean?>(null)
    val lockEnabled = _lockEnabled.asStateFlow()
    private val _authenticated = MutableStateFlow(false)
    val authenticated = _authenticated.asStateFlow()
    private val channel = Channel<UiEvent>(Channel.BUFFERED)
    val events = channel.receiveAsFlow()

    init {
        viewModelScope.launch {
            container.settings.lockEnabled.collect { enabled ->
                _lockEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            combine(query, retry) { query, _ -> query }.collectLatest { query ->
                _loading.value = true
                _archiveError.value = null
                try {
                    repository.observe(query).collect { items ->
                        _documents.value = items
                        _loading.value = false
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    _loading.value = false
                    _archiveError.value = friendlyError(error)
                }
            }
        }
        viewModelScope.launch {
            try { repository.cleanOrphans() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The archive collector displays database errors with a retry action. */ }
        }
    }

    fun setQuery(value: String) { saved["query"] = value.take(200) }
    fun retryArchive() { retry.value++ }
    fun authenticate() { _authenticated.value = true }
    fun lock() { _authenticated.value = false }
    fun notify(message: String) { viewModelScope.launch { channel.send(UiEvent.Message(message)) } }

    fun setLockEnabled(enabled: Boolean) = runOperation("Zapisywanie ustawień…") {
        container.settings.setLockEnabled(enabled)
        _authenticated.value = true
    }

    fun prepareExport(id: String) { saved["exportId"] = id }
    fun finishExport(uri: Uri?) {
        val id = saved.get<String>("exportId")
        saved["exportId"] = null
        if (uri != null && id != null) export(id, uri)
    }

    fun open(id: String) {
        _detailError.value = null
        if (_detail.value?.document?.id != id) _detail.value = null
        viewModelScope.launch {
            try { _detail.value = repository.details(id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _detailError.value = friendlyError(error) }
        }
    }

    fun import(uris: List<Uri>) = runOperation("Przygotowywanie dokumentu…") {
        val result = repository.importScans(uris) { _busy.value = it }
        channel.send(UiEvent.Imported(result.id))
        if (result.ocrFailed) channel.send(UiEvent.Message("Zapisano skany, ale OCR części stron się nie powiódł. Możesz go ponowić w szczegółach."))
    }

    fun save(id: String, title: String, text: String) = runOperation("Zapisywanie zmian…") {
        repository.update(id, title, text)
        _detail.value = repository.details(id)
        channel.send(UiEvent.Saved)
    }

    fun delete(id: String) = runOperation("Usuwanie dokumentu…") {
        repository.delete(id)
        _detail.value = null
        channel.send(UiEvent.Deleted)
    }

    fun retryOcr(id: String) = runOperation("Rozpoznawanie tekstu…") {
        repository.retryOcr(id) { _busy.value = it }
        _detail.value = repository.details(id)
        channel.send(UiEvent.Message("Rozpoznawanie tekstu zakończone."))
    }

    fun export(id: String, target: Uri) = runOperation("Tworzenie PDF…") {
        repository.exportPdf(id, target) { _busy.value = it }
        channel.send(UiEvent.Message("Zapisano PDF w wybranym miejscu."))
    }

    private fun runOperation(label: String, work: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = label
        viewModelScope.launch {
            try { work() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { channel.send(UiEvent.Message(friendlyError(error))) }
            finally { _busy.value = null }
        }
    }

    private fun friendlyError(error: Exception): String = when (error) {
        is GeneralSecurityException -> "Nie można odszyfrować danych. Plik jest uszkodzony lub klucz jest niedostępny."
        is IOException -> "Nie udało się odczytać lub zapisać pliku. Sprawdź wolne miejsce i spróbuj ponownie."
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "Nieprawidłowe dane."
        else -> "Operacja nie powiodła się. Spróbuj ponownie. Jeśli problem powraca, uruchom aplikację ponownie."
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ScannerViewModel(container, extras.createSavedStateHandle()) as T
    }
}
