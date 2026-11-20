# Skaner dokumentów z OCR

Prosta aplikacja Android do skanowania dokumentów, rozpoznawania tekstu i przechowywania skanów w szyfrowanym archiwum. Projekt 12 z przedmiotu ZPNPA. Interfejs jest w języku polskim.

## Funkcje

- Zdjęcia dokumentów lub import zdjęć przez skaner ML Kit, do 10 stron na dokument.
- Automatyczne wykrywanie krawędzi, przycinanie i korekcja perspektywy; ręczne poprawki w interfejsie skanera.
- OCR drukowanego tekstu na urządzeniu, z modelem dołączonym do APK.
- Dodawanie, wyświetlanie, zmiana nazwy i tekstu oraz usuwanie dokumentów.
- Wyszukiwanie w nazwach i tekście OCR, bez rozróżniania wielkości liter i polskich znaków.
- Eksport wielostronicowego PDF z obrazami stron.
- Szyfrowana baza SQLCipher, skany szyfrowane AES-GCM i klucz chroniony Android Keystore.
- Opcjonalna blokada archiwum z wykorzystaniem blokady ekranu telefonu.

## Uruchomienie w Android Studio

1. Otwórz katalog projektu przez **File → Open**.
2. W SDK Manager zainstaluj **Android SDK Platform 36** oraz Build Tools proponowane przez Android Studio.
3. Jako Gradle JDK wybierz Javę dołączoną do Android Studio (`jbr`, JDK 21) lub JDK 17.
4. Poczekaj na Gradle Sync. Pierwsza synchronizacja wymaga internetu.
5. Podłącz telefon z debugowaniem USB lub uruchom emulator z obrazem **Google Play**. Wybierz moduł `app` i naciśnij **Run**.

Projekt używa AGP 8.13.2, Gradle 8.13 i Kotlin 2.2.21. Wymagany Android 8.0 lub nowszy (API 26). Na telefonie potrzebne są Usługi Google Play. Skaner pobiera swój moduł przy pierwszym użyciu; późniejsze skanowanie i OCR przetwarzają dokumenty lokalnie.

## Budowanie w Windows

Skrypt wykrywa JDK Android Studio i typowy katalog SDK:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
```

Wynik: `app/build/outputs/apk/debug/app-debug.apk`.

Testy na uruchomionym emulatorze lub podłączonym telefonie:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Jeśli `JAVA_HOME` nie jest ustawiony, można również uruchomić:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build.ps1 -Tasks connectedDebugAndroidTest
```

## Obsługa

1. W archiwum naciśnij **Skanuj dokument**. Zrób zdjęcie lub wybierz je z galerii.
2. Sprawdź krawędzie, dodaj ewentualne strony i zatwierdź skan.
3. Aplikacja zapisze zaszyfrowane skany i rozpozna tekst. Nadaj nazwę, popraw wynik i wybierz **Zapisz zmiany**.
4. W archiwum wyszukaj dokument po nazwie lub fragmencie treści. W szczegółach możesz przeglądać strony, edytować dane, ponowić OCR, eksportować PDF i usunąć dokument.
5. W ustawieniach możesz włączyć blokadę archiwum. Telefon musi mieć wcześniej skonfigurowany PIN, hasło lub biometrię.

PDF zapisany poza aplikacją nie jest szyfrowany przez archiwum. PDF zawiera obrazy, a wyszukiwanie w rozpoznanym tekście odbywa się w aplikacji. Usunięcie aplikacji usuwa dostęp do archiwum; kopie zapasowe danych aplikacji są wyłączone. Zrzuty ekranu są dozwolone w kompilacji debug do dokumentacji; kompilacja release używa `FLAG_SECURE`.

## Dokumentacja i materiały

- [Pełna dokumentacja PDF](docs/Skaner-dokumentacja.pdf)
- [Dokumentacja projektu i diagramy](docs/DOKUMENTACJA.md)
- [Testy i wyniki](docs/TESTY.md)
- [Przygotowanie do prezentacji](docs/OBRONA.md)
- [Zrzuty ekranów](docs/screenshots)
- [Fikcyjny rachunek do wydrukowania i zeskanowania](docs/examples/rachunek-testowy.pdf)
- Schemat Room: `app/schemas/pl.edu.zpnpa.skaner.data.ArchiveDatabase/1.json`.
- Raporty po uruchomieniu kontroli: `app/build/reports/tests/testDebugUnitTest`, `app/build/reports/androidTests/connected/debug`, `app/build/reports/lint-results-debug.html`.

Dokumentację PDF można odtworzyć poleceniem `python tools/make_report.py` po instalacji opcjonalnych zależności `pip install -r tools/requirements-docs.txt`. Te biblioteki służą wyłącznie do dokumentacji i nie są potrzebne do zbudowania aplikacji.

## Repozytorium Git

Repozytorium projektu: [ehoze/ZPNPA_Projekt](https://github.com/ehoze/ZPNPA_Projekt).

Pobranie kodu:

```powershell
git clone https://github.com/ehoze/ZPNPA_Projekt.git
cd ZPNPA_Projekt
```

Hasła, klucze i `local.properties` nie są wersjonowane. APK i PDF są generowanymi materiałami w katalogu `artifacts`.
