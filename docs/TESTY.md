# Testy i wyniki

## Wynik weryfikacji

Weryfikacja z 20.11.2026: kompilacja debug zakończona powodzeniem, 14 testów jednostkowych oraz 11 testów na emulatorze zakończonych powodzeniem. Android Lint: 0 błędów i 15 ostrzeżeń o dostępności nowszych wersji przypiętych zależności. Nie pozostały ostrzeżenia dotyczące kodu interfejsu ani używania niedostępnych API.

| Grupa | Liczba | Wynik |
|---|---|---|
| Testy jednostkowe reguł i wyszukiwania | 8 | 8 zaliczonych |
| Testy jednostkowe szyfrowania | 6 | 6 zaliczonych |
| Testy integracyjne na Androidzie | 2 | 2 zaliczone |
| Testy interfejsu i nawigacji | 5 | 5 zaliczonych |
| Scenariusze zrzutów czterech widoków | 4 | 4 zaliczone |
| Łącznie | 25 | 25 zaliczonych |

Szczegółowy zapis nazw testów, czasu wykonania, ostrzeżeń Lint oraz sumy SHA-256 APK znajduje się w [results/verification.json](results/verification.json).

## Środowisko

- Windows, Android Studio z JDK 21.
- Kotlin 2.2.21, Gradle 8.13, AGP 8.13.2, compileSdk i targetSdk 36, minSdk 26.
- Osobny emulator x86_64 z systemem Android 16, API 36.1 i Usługami Google Play.
- Telefon Motorola razr 50 ultra z Androidem 17: aplikacja zainstalowana i uruchomiona przez ADB. Użytkownik potwierdził wykonanie zdjęcia, zapis skanu i pojawienie się rozpoznanego tekstu.

## Testy jednostkowe

`DocumentRulesTest` sprawdza pustą nazwę, granicę 100 znaków, przekroczenie limitu, białe znaki, dopuszczenie pustego tekstu OCR, limit długości tekstu, normalizację polskich znaków i bezpieczne traktowanie znaków `%`, `_` oraz `\` w wyszukiwaniu.

`ArchiveCipherTest` sprawdza odszyfrowanie polskiego tekstu, różne wyniki szyfrowania tego samego wejścia, wykrycie modyfikacji zaszyfrowanego pliku, odrzucenie innego klucza, zbyt krótkiego pliku i nieznanej wersji formatu.

## Testy integracyjne

`scanOcrEncryptedPersistenceSearchExportAndDelete` tworzy fikcyjny rachunek, importuje dwie strony, rozpoznaje słowo „internet”, edytuje nazwę na „Łączność”, wyszukuje bez polskich znaków, sprawdza zaszyfrowany plik i bazę oraz eksportuje poprawny dwustronicowy PDF. Następnie zamyka i ponownie otwiera bazę, odczytuje dane i obraz oraz usuwa dokument wraz z plikami. Sprawdza także odtworzenie losowego hasła bazy z pliku chronionego kluczem Android Keystore.

`sqlSearchTreatsPercentAndUnderscoreLiterally` sprawdza na rzeczywistej bazie SQLite, że znaki specjalne wpisane przez użytkownika nie stają się operatorami wzorca i nie zwracają niepasujących rekordów.

Testy integracyjne korzystają z osobnego katalogu testowego. Nie usuwają osobistego archiwum użytkownika.

## Testy interfejsu

- Puste archiwum pokazuje instrukcję, a przyciski skanowania i ustawień wywołują odpowiednie działania.
- Formularz nie zapisuje pustej nazwy i przyjmuje poprawioną nazwę.
- Niezapisana nazwa przetrwa odtworzenie stanu ekranu.
- Brak wyników wyszukiwania ma jednoznaczny komunikat.
- Zapytanie wyszukiwania przetrwa przejście do ustawień, powrót oraz odtworzenie Activity.

`ScreenshotTest` renderuje rzeczywiste ekrany Compose z fikcyjnym dokumentem: archiwum, szczegóły, edycję i ustawienia. Służy do powtarzalnego przygotowania materiałów do dokumentacji.

## Powtórzenie kontroli

Po skonfigurowaniu JDK i SDK:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

Przy uruchomionym emulatorze, aby nie uruchamiać testów na innych podłączonych urządzeniach:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.injected.device.serial=emulator-5554'
```

Przygotowanie i pobranie zrzutów po zbudowaniu pakietów testowych:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest
powershell -ExecutionPolicy Bypass -File tools/screenshots.ps1 -Serial emulator-5554
```

Raporty HTML są generowane w `app/build/reports`. Dane JSON w dokumentacji są zapisem zweryfikowanego wykonania; po zmianie kodu należy uruchomić kontrole ponownie i zaktualizować wyniki.

## Scenariusze ręczne na telefonie

| Scenariusz | Oczekiwany wynik | Stan |
|---|---|---|
| Pierwszy skan aparatem i OCR | Dokument zapisany, widoczny rozpoznany tekst | Potwierdzony przez użytkownika na Motoroli |
| Kilka stron i korekta narożników | Czytelne strony w prawidłowej kolejności | Do sprawdzenia na telefonie |
| Słabe światło i nieostry wydruk | Możliwość poprawy lub ponowienia skanu; brak utraty innych danych | Do sprawdzenia na telefonie |
| Pierwsze uruchomienie skanera bez internetu | Czytelny komunikat, możliwość ponowienia po połączeniu | Do sprawdzenia na telefonie |
| Skanowanie po pobraniu modułu w trybie offline | Lokalny zapis i OCR | Do sprawdzenia na telefonie |
| Eksport PDF do wybranego folderu | PDF otwiera się w zewnętrznej przeglądarce | Format i liczba stron potwierdzone automatycznie; wybór folderu do sprawdzenia ręcznie |
| Włączenie blokady i powrót do aplikacji | Wymagane uwierzytelnienie; anulowanie nie pokazuje archiwum | Do sprawdzenia na telefonie |
| Obrót lub złożenie telefonu podczas edycji | Zachowana nazwa i tekst, dostępne przewijanie | Odtworzenie stanu potwierdzone automatycznie; złożenie telefonu do sprawdzenia ręcznie |
| Duża czcionka i TalkBack | Dostępne przyciski, czytelne etykiety, możliwość przewijania | Do sprawdzenia na telefonie |

Do dalszych prób można użyć plików `docs/examples/rachunek-testowy.jpg` i `rachunek-testowy.pdf`. Testy automatyczne nie oceniają jakości wykrywania krawędzi na dowolnym zdjęciu ani procentowej skuteczności OCR.
