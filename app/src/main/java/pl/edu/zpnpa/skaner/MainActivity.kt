package pl.edu.zpnpa.skaner

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import pl.edu.zpnpa.skaner.ui.ScannerApp
import pl.edu.zpnpa.skaner.ui.ScannerTheme
import pl.edu.zpnpa.skaner.ui.ScannerViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : FragmentActivity() {
    private val model: ScannerViewModel by viewModels {
        ScannerViewModel.Factory((application as ScannerApplication).container)
    }
    private var preparingScanner by mutableStateOf(false)
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { model.lock() }
    }
    private val scannerLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages = GmsDocumentScanningResultParser.pages(result.data)
            if (pages.isEmpty()) model.notify("Skaner nie zwrócił żadnej strony. Spróbuj ponownie.")
            else model.import(pages)
        }
    }
    private val pdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        model.finishExport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        setContent {
            ScannerTheme {
                ScannerApp(model, preparingScanner,
                    onScan = ::scan,
                    onUnlock = { authenticate { model.authenticate() } },
                    onEnableLock = { authenticate { model.setLockEnabled(true) } },
                    onExport = { id, title ->
                        model.prepareExport(id)
                        pdfLauncher.launch(title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100) + ".pdf")
                    })
            }
        }
    }

    private fun scan() {
        if (preparingScanner || model.busy.value != null) return
        preparingScanner = true
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                preparingScanner = false
                try { scannerLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                catch (_: Exception) { model.notify("Nie można otworzyć skanera. Sprawdź Usługi Google Play.") }
            }
            .addOnFailureListener {
                preparingScanner = false
                model.notify("Nie udało się uruchomić skanera. Przy pierwszym użyciu połącz telefon z internetem i sprawdź aktualizacje Usług Google Play.")
            }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            model.notify("Najpierw ustaw blokadę ekranu telefonu: PIN, hasło lub biometrię.")
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    if (code != BiometricPrompt.ERROR_USER_CANCELED && code != BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                        model.notify("Nie udało się odblokować archiwum. Spróbuj ponownie.")
                }
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Odblokuj archiwum")
            .setSubtitle("Potwierdź tożsamość blokadą ekranu telefonu")
            .setAllowedAuthenticators(authenticators).build())
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }
}

private object GmsDocumentScanningResultParser {
    fun pages(intent: android.content.Intent?) =
        com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(intent)
            ?.pages.orEmpty().map { it.imageUri }
}
