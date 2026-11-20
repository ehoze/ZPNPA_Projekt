package pl.edu.zpnpa.skaner.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CancellationException
import pl.edu.zpnpa.skaner.data.DocumentDetails
import pl.edu.zpnpa.skaner.data.DocumentEntity
import pl.edu.zpnpa.skaner.data.PageEntity
import pl.edu.zpnpa.skaner.domain.DocumentRules
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScannerApp(
    model: ScannerViewModel, preparingScanner: Boolean,
    onScan: () -> Unit, onUnlock: () -> Unit, onEnableLock: () -> Unit,
    onExport: (String, String) -> Unit,
) {
    val nav = rememberNavController()
    val documents by model.documents.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val loading by model.loading.collectAsStateWithLifecycle()
    val archiveError by model.archiveError.collectAsStateWithLifecycle()
    val detail by model.detail.collectAsStateWithLifecycle()
    val detailError by model.detailError.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val lock by model.lockEnabled.collectAsStateWithLifecycle()
    val authenticated by model.authenticated.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is UiEvent.Message -> snackbar.showSnackbar(event.text)
                is UiEvent.Imported -> {
                    nav.navigate("document/${event.id}")
                    nav.navigate("edit/${event.id}")
                }
                UiEvent.Saved -> nav.popBackStack()
                UiEvent.Deleted -> nav.popBackStack("archive", false)
            }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                lock == null -> LoadingView()
                lock == true && !authenticated -> LockScreen(onUnlock)
                else -> NavHost(navController = nav, startDestination = "archive") {
                    composable("archive") {
                        ArchiveScreen(documents, query, loading, archiveError,
                            model::setQuery, onScan, { nav.navigate("settings") },
                            { nav.navigate("document/$it") }, model::retryArchive)
                    }
                    composable("document/{id}") { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        LaunchedEffect(id) { model.open(id) }
                        DocumentRoute(detail, detailError, id, { nav.popBackStack() }, { model.open(id) }) { data ->
                            DetailScreen(data, { nav.popBackStack() }, { nav.navigate("edit/$id") },
                                { model.delete(id) }, { onExport(id, data.document.title) }, { model.retryOcr(id) },
                                loadBitmap = { model.repository.bitmap(it) })
                        }
                    }
                    composable("edit/{id}") { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        LaunchedEffect(id) { model.open(id) }
                        DocumentRoute(detail, detailError, id, { nav.popBackStack() }, { model.open(id) }) { data ->
                            EditScreen(data.document, { nav.popBackStack() }) { title, text -> model.save(id, title, text) }
                        }
                    }
                    composable("settings") {
                        SettingsScreen(lock == true, { nav.popBackStack() },
                            { enabled -> if (enabled) onEnableLock() else model.setLockEnabled(false) })
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
            val operation = busy ?: if (preparingScanner) "Otwieranie skanera…" else null
            if (operation != null && !(lock == true && !authenticated)) {
                BackHandler { }
                AlertDialog(onDismissRequest = {}, confirmButton = {},
                    title = { Text("Chwileczkę") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(operation)
                            if (preparingScanner) Text("Pierwsze uruchomienie może wymagać pobrania modułu skanera.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    documents: List<DocumentEntity>, query: String, loading: Boolean, error: String?,
    onQuery: (String) -> Unit, onScan: () -> Unit, onSettings: () -> Unit,
    onOpen: (String) -> Unit, onRetry: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Skaner", fontWeight = FontWeight.SemiBold) },
            actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Ustawienia") } })
    }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = onScan,
            icon = { Icon(Icons.Outlined.DocumentScanner, null) }, text = { Text("Skanuj dokument") })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max = 760.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text("Twoje dokumenty", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("Skany i rozpoznany tekst w jednym miejscu.", Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text("Szukaj w nazwie i tekście") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) ({
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, "Wyczyść wyszukiwanie") }
                    }) else null)
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Szyfrowane archiwum lokalne", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            when {
                loading -> item { LoadingView(Modifier.height(180.dp)) }
                error != null -> item { ErrorCard(error, onRetry) }
                documents.isEmpty() -> item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(28.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.Description, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(if (query.isBlank()) "Zacznij od pierwszego skanu" else "Brak wyników",
                                style = MaterialTheme.typography.titleMedium)
                            Text(if (query.isBlank()) "Zrób zdjęcie dokumentu lub wybierz je z galerii. Tekst rozpoznamy automatycznie."
                                else "Spróbuj innej nazwy lub fragmentu tekstu.", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                else -> {
                    item { Text("Dokumenty: ${documents.size}", style = MaterialTheme.typography.labelLarge) }
                    items(documents, key = { it.id }) { document ->
                        OutlinedCard(onClick = { onOpen(document.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(document.title, style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${dateLabel(document.createdAt)} · Strony: ${document.pageCount}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(document.text.ifBlank { "Brak rozpoznanego tekstu" }, Modifier.padding(top = 5.dp),
                                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentRoute(data: DocumentDetails?, error: String?, id: String, onBack: () -> Unit,
    onRetry: () -> Unit, content: @Composable (DocumentDetails) -> Unit) {
    when {
        error != null -> Column(Modifier.safeDrawingPadding().padding(20.dp)) {
            TextButton(onClick = onBack) { Text("Wróć do archiwum") }
            ErrorCard(error, onRetry)
        }
        data?.document?.id == id -> content(data)
        else -> LoadingView()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScreen(data: DocumentDetails, onBack: () -> Unit, onEdit: () -> Unit,
    onDelete: () -> Unit, onExport: () -> Unit, onOcr: () -> Unit,
    loadBitmap: suspend (PageEntity) -> Bitmap) {
    var pageIndex by rememberSaveable(data.document.id) { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Dokument") }, navigationIcon = { BackButton(onBack) },
            actions = { IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Outlined.Delete, "Usuń dokument") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max = 760.dp),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(data.document.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${dateLabel(data.document.createdAt)} · Strony: ${data.pages.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (data.pages.isNotEmpty()) item {
                val safeIndex = pageIndex.coerceIn(data.pages.indices)
                PagePreview(data.pages[safeIndex], loadBitmap)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { pageIndex-- }, enabled = safeIndex > 0) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Poprzednia strona")
                    }
                    Text("Strona ${safeIndex + 1} z ${data.pages.size}")
                    IconButton(onClick = { pageIndex++ }, enabled = safeIndex < data.pages.lastIndex) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Następna strona")
                    }
                }
            }
            item {
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Edytuj nazwę i tekst")
                }
                OutlinedButton(onClick = { dialog = "export" }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Eksportuj PDF")
                }
            }
            item {
                Text("Rozpoznany tekst", style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(data.document.text.ifBlank { "Nie znaleziono tekstu. Możesz wpisać go ręcznie lub ponowić OCR." },
                        Modifier.padding(top = 10.dp), maxLines = 12, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { dialog = "ocr" }) { Text("Rozpoznaj tekst ponownie") }
            }
        }
    }
    when (dialog) {
        "delete" -> ConfirmDialog("Usunąć dokument?", "Skany i tekst zostaną trwale usunięte z archiwum.",
            "Usuń", { dialog = null }, { dialog = null; onDelete() })
        "export" -> ConfirmDialog("Eksport do PDF", "PDF zapiszesz w wybranym miejscu. Wyeksportowany plik nie będzie objęty szyfrowaniem archiwum.",
            "Wybierz miejsce", { dialog = null }, { dialog = null; onExport() })
        "ocr" -> ConfirmDialog("Ponowić OCR?", "Nowy wynik zastąpi bieżący tekst, w tym ręczne poprawki.",
            "Rozpoznaj", { dialog = null }, { dialog = null; onOcr() })
    }
}

@Composable
private fun PagePreview(page: PageEntity, loadBitmap: suspend (PageEntity) -> Bitmap) {
    var image by remember(page) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(page) { mutableStateOf(false) }
    LaunchedEffect(page) {
        try { image = loadBitmap(page) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Box(Modifier.fillMaxWidth().height(330.dp).clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when {
            failed -> Text("Nie można odczytać skanu.", Modifier.padding(20.dp))
            image != null -> Image(image!!.asImageBitmap(), "Podgląd strony ${page.position + 1}",
                Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
            else -> CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(document: DocumentEntity, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var title by rememberSaveable(document.id) { mutableStateOf(document.title) }
    var text by rememberSaveable(document.id) { mutableStateOf(document.text) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val titleError = if (submitted) DocumentRules.titleError(title) else null
    val textError = if (submitted) DocumentRules.textError(text) else null
    val leave = { if (title != document.title || text != document.text) confirmDiscard = true else onBack() }
    BackHandler { leave() }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Edycja dokumentu") }, navigationIcon = { BackButton(leave) })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().widthIn(max = 760.dp),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text("Nadaj nazwę i popraw tekst", style = MaterialTheme.typography.titleLarge)
                Text("Zmiany zapiszą się w archiwum i będą dostępne w wyszukiwaniu.",
                    Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                OutlinedTextField(title, { title = it.take(DocumentRules.MAX_TITLE + 1) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Nazwa dokumentu") }, singleLine = true,
                    isError = titleError != null,
                    supportingText = { Text(titleError ?: "${title.trim().length}/${DocumentRules.MAX_TITLE}") })
            }
            item {
                OutlinedTextField(text, { text = it.take(DocumentRules.MAX_TEXT + 1) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Tekst dokumentu") },
                    minLines = 8, maxLines = 14, isError = textError != null,
                    supportingText = { Text(textError ?: "Wynik OCR możesz poprawić ręcznie.") })
            }
            item {
                Button(onClick = {
                    submitted = true
                    if (DocumentRules.titleError(title) == null && DocumentRules.textError(text) == null) onSave(title, text)
                }, modifier = Modifier.fillMaxWidth()) { Text("Zapisz zmiany") }
            }
        }
    }
    if (confirmDiscard) ConfirmDialog("Odrzucić zmiany?", "Niezapisane poprawki zostaną utracone.", "Odrzuć",
        { confirmDiscard = false }, { confirmDiscard = false; onBack() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(lockEnabled: Boolean, onBack: () -> Unit, onLockChanged: (Boolean) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Ustawienia") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).widthIn(max = 760.dp),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text("Prywatność", style = MaterialTheme.typography.titleLarge)
                OutlinedCard(Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Blokada archiwum", style = MaterialTheme.typography.titleMedium)
                            Text("Po opuszczeniu aplikacji wymagaj blokady ekranu telefonu.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = lockEnabled, onCheckedChange = onLockChanged,
                            modifier = Modifier.semantics { contentDescription = "Blokada archiwum" })
                    }
                }
            }
            item { InfoCard("Twoje dane", "Dokumenty i rozpoznany tekst są szyfrowane w pamięci aplikacji. Archiwum nie jest wysyłane do chmury. Usunięcie aplikacji usuwa również dostęp do archiwum.") }
            item { InfoCard("Skanowanie", "Możesz zeskanować do 10 stron naraz lub wybrać zdjęcia z galerii. Skaner automatycznie dopasowuje krawędzie i perspektywę. Przy pierwszym użyciu potrzebuje internetu do pobrania modułu Usług Google Play.") }
            item { InfoCard("Rozpoznawanie tekstu", "OCR działa na telefonie. Najlepsze wyniki uzyskasz przy dobrym oświetleniu i ostrym zdjęciu drukowanego tekstu. Wynik można poprawić w edycji dokumentu.") }
            item { InfoCard("Eksport PDF", "PDF zawiera obrazy stron. Tekst OCR jest dostępny i przeszukiwany w aplikacji. Plik zapisany poza archiwum nie jest szyfrowany przez aplikację.") }
            item { Text("Skaner · wersja 1.0\nProjekt 12 · Archiwum dokumentów z OCR", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Archiwum jest zablokowane", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.headlineSmall)
        Text("Odblokuj je, aby zobaczyć dokumenty.", Modifier.padding(vertical = 16.dp))
        Button(onClick = onUnlock) { Text("Odblokuj archiwum") }
    }
}

@Composable
private fun InfoCard(title: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Wstecz") }
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } })
}

@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(20.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

private fun dateLabel(time: Long) = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("pl")).format(Date(time))
