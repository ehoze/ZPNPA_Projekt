# Przygotowanie do prezentacji

## Pokaz działania w około pięć minut

1. Otwórz archiwum i pokaż przycisk skanowania.
2. Zeskanuj wydruk z kilkoma wyraźnymi wierszami. Pokaż dopasowanie krawędzi i perspektywy.
3. Nadaj nazwę, sprawdź OCR, popraw jedno słowo i zapisz.
4. Wróć do listy i znajdź dokument fragmentem jego treści. Możesz pokazać wyszukiwanie bez polskich znaków.
5. Otwórz dokument i zapisz PDF. Otwórz go w systemowej przeglądarce.
6. Zamknij i ponownie uruchom aplikację, aby pokazać trwałość danych.
7. Włącz blokadę w ustawieniach, wyjdź z aplikacji i pokaż odblokowanie.
8. Usuń dokument testowy i potwierdź zniknięcie z listy.

Przed pokazem uruchom skaner raz z internetem, aby moduł Google Play był już pobrany. Używaj fikcyjnego wydruku. Przygotuj drugi dokument na wypadek słabego oświetlenia.

## Jak czytać kod

- `MainActivity.kt`: uruchomienie skanera, wybór pliku PDF, uwierzytelnienie.
- `ui/ScannerApp.kt`: cztery ekrany, nawigacja i podstawowe formularze.
- `ui/ScannerViewModel.kt`: stan, zadania, obsługa błędów i zdarzenia.
- `data/DocumentRepository.kt`: import, OCR, pliki, edycja, usuwanie i eksport.
- `data/ArchiveDatabase.kt`: tabele i zapytania.
- `security/ArchiveKeys.kt`: klucz systemowy i hasło bazy.
- `security/ArchiveCipher.kt`: szyfrowanie pojedynczego pliku.
- `domain/DocumentRules.kt`: walidacja i normalizacja wyszukiwania.

## Pojęcia, które warto umieć wyjaśnić

**MVVM** dzieli aplikację na widok, stan i logikę współpracy z danymi. Compose wyświetla stan z ViewModel; repozytorium odpowiada za dane. Dzięki temu ekran nie zawiera zapytań SQL ani operacji szyfrowania.

**Coroutines** pozwalają wykonywać zadania asynchronicznie. Repozytorium przełącza operacje plikowe i bazę na `Dispatchers.IO`. `viewModelScope` przeżywa zmianę konfiguracji i kończy pracę po usunięciu ViewModel.

**Flow i StateFlow** przenoszą zmiany danych do interfejsu. Po dodaniu lub usunięciu rekordu Room emituje nową listę, a Compose aktualizuje widok.

**Room** to warstwa nad SQLite. Encje opisują tabele, DAO zapytania, a kompilator sprawdza SQL. **SQLCipher** szyfruje plik bazy.

**AES-GCM** szyfruje i uwierzytelnia plik. Użycie świeżego IV sprawia, że dwa zaszyfrowania tego samego obrazu mają różne wyniki. Zmiana zaszyfrowanych danych jest wykrywana przy odczycie.

**Android Keystore** chroni klucz przed prostym odczytaniem z plików aplikacji. Losowe hasło bazy jest zapisane jako zaszyfrowane dane, a nie stała w kodzie.

**OCR** rozpoznaje tekst z obrazu. Korekcja perspektywy poprawia geometrię zdjęcia, a OCR jest osobnym etapem. Jakość rozpoznania zależy od obrazu i nie jest gwarantowana dla każdego dokumentu.

**Transakcja** zapisuje rekord dokumentu i rekordy stron razem. Jeśli zapis nie może się zakończyć, nie pozostawia częściowo dodanego dokumentu w bazie.

## Dlaczego te wybory

Gotowy skaner ML Kit obsługuje aparat, krawędzie i perspektywę. Pozwala to zachować prosty kod i skupić się na wymaganiach aplikacji. Rozpoznawanie tekstu korzysta z modelu dołączonego do APK. Archiwum jest lokalne, więc własny serwer i Retrofit nie są potrzebne.

Cztery własne ekrany spełniają wymóg wieloekranowości niezależnie od dodatkowych ekranów SDK. Edycja tekstu OCR i nazwy realizuje aktualizację w CRUD. Wyszukiwanie używa znormalizowanego pola w szyfrowanej bazie, dlatego nie tworzy jawnego indeksu poza archiwum.

## Granice zabezpieczeń

Szyfrowanie dotyczy własnego archiwum aplikacji. Eksportowany PDF jest jawnym plikiem. Plikami tymczasowymi skanera zarządza SDK Google. Opcjonalna blokada ekranu jest dodatkową kontrolą dostępu, a klucz Keystore nie wymaga osobnego potwierdzenia biometrii przy każdym odszyfrowaniu. Usunięcie aplikacji powoduje utratę dostępu do lokalnego archiwum.
