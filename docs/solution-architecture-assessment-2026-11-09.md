# SRM Toolkit — architektura istniejąca

Data przeglądu: 2026-11-09
Zakres: implementacja widoczna w repozytorium oraz zgłoszony stan środowiska
TEST. Dokument opisuje stan obecny; nie zawiera planu docelowego ani listy
proponowanych zmian.
Poprzednia ocena z 2026-10-06 pozostaje zachowana w
[`solution-architecture-assessment.md`](solution-architecture-assessment-old.md).

## Podstawa przeglądu

- Lokalny checkout: `feature/participant-minor-email-optional`, commit `1130ff4`.
  Referencja `origin/development` wskazuje `7e3d8ed` (PR #56), zawierający tę
  poprawkę.
- Stan GCP pochodzi z relacji operatora; nie wykonywano połączenia z GCP ani
  inspekcji działających usług.
- Nie uruchamiano testów ani aplikacji na potrzeby tego przeglądu.

## Komponenty i komunikacja

```text
Przeglądarka
  ├── Registration frontend (Vue 3 / Vite)
  │      └── publiczne endpointy Registration API
  └── Biuro frontend (Vue 3 / Vite / Pinia)
         └── Biuro API ── uwierzytelnione żądania ── Registration API

Biuro API ── biuro_db
Registration API ── registration_db
Registration API ── Email service ── SMTP
```

| Komponent | Obecna odpowiedzialność |
|---|---|
| `registration-frontend` | Wieloetapowe formularze uczestnika i kadry, walidacja klienta, prezentacja wyniku wysłania zgłoszenia. |
| `registration-api` | Walidacja serwerowa i zapis zgłoszeń, statusy, historia statusów i edycji, endpointy list/statystyk oraz integracja katalogu turnusów i powiadomień. |
| `biuro-frontend` | Obsługa zgłoszeń i ich szczegółów, definicje turnusów, statystyki, listy i eksporty. |
| `biuro-api` | Konta i uprawnienia Biura, katalog turnusów, integracja z Registration API oraz udostępnienie danych frontendowi. |
| `email-service` | Renderowanie szablonów i synchroniczna wysyłka wiadomości przez SMTP. |
| MariaDB | Współdzielony serwer bazy z oddzielnymi schematami `registration_db` i `biuro_db`. |

Biuro API pośredniczy między panelem Biura a zarządzającymi endpointami Registration
API. Publiczny formularz wysyła zgłoszenie bezpośrednio do publicznych endpointów
Registration API. Registration API pobiera katalog turnusów przez uwierzytelniony
endpoint Biuro API i korzysta z lokalnej pamięci podręcznej.

## Przechowywanie danych

- `registration_db` przechowuje zgłoszenia. Dane formularza, w tym dane osobowe,
  kontaktowe i odpowiedzi zdrowotne, są przechowywane w strukturze JSON zgłoszenia.
- W tym samym schemacie przechowywana jest historia zmian statusu oraz audyt edycji
  zgłoszenia. Historia statusów zawiera poprzedni i nowy status, czas serwera,
  konto operatora i — gdy dotyczy — powód odrzucenia.
- `biuro_db` przechowuje definicje turnusów, wymagania kadrowe oraz konta, role,
  przypisania Viewerów i audyt operacji na kontach.
- Schematy są aktualizowane przez osobne migracje Flyway w Registration API i
  Biuro API.
- Role i certyfikaty sezonowe są częściowo zdefiniowane w plikach konfiguracyjnych;
  wymagane liczby ról są zapisane w definicji danego turnusu.
- W analizowanym checkoutcie nie ma funkcji płatności dostępnej w głównym przepływie
  aplikacji.

## Logowanie i uprawnienia

- Formularze publiczne umożliwiają odczyt opcji turnusu i wysłanie zgłoszenia.
- Zarządzające endpointy Registration API wymagają uwierzytelnienia serwisowego;
  sekretów usługowych nie umieszcza się w frontendzie.
- Panel Biura loguje użytkowników przez Biuro API. Konta mają role `SYS_ADMIN`,
  `OPERATOR` lub `VIEWER`; Viewer otrzymuje dostęp do przypisanych turnusów i ich
  list.
- Sesje Biura są oparte na JWT i mogą być unieważniane przez serwer.
- W kodzie nie ma uwierzytelniania MFA.

## Zgłoszenia, pojemność i odświeżanie widoków

- Zgłoszenia przechodzą przez istniejący przepływ statusów. Zmiana statusu i zapis
  jej historii są wykonywane transakcyjnie; powiadomienie jest wysyłane po zapisie.
- Akceptacja nie jest obecnie blokowana po osiągnięciu pojemności uczestników lub
  kadry. Pojemności są rozdzielne; zmniejszenie limitu poniżej zaakceptowanej liczby
  danego typu jest blokowane.
- Zgłoszenia mogą być pobierane, gdy właściwe zapisy są otwarte; formularz zapisuje
  poprawne nowe zgłoszenie ze statusem `NEW`.
- Wymagania kadrowe są konfigurowane per turnus. Panel pokazuje statystyki
  zaakceptowane/wymagane dla skonfigurowanych ról. Suma wymagań i liczba miejsc dla
  kadry pozostają niezależne; edytor obecnie nie pokazuje ostrzeżenia o przekroczeniu.
- Tabela zgłoszeń pobiera dane przy otwarciu, zmianie filtrów oraz po zmianie
  statusu. Nie wykonuje okresowego automatycznego odświeżania po nadejściu nowego
  zgłoszenia.
- Registration API odświeża pamięć podręczną katalogu turnusów w interwale
  konfiguracyjnym, domyślnie co godzinę, zachowując ostatni poprawny wynik podczas
  awarii źródła.

## Wiadomości e-mail

Registration API wywołuje email-service synchronicznie po zapisaniu zgłoszenia lub
zmiany statusu. Błąd wysyłki nie cofa już zapisanego zgłoszenia ani statusu.

Registration API loguje kod zgłoszenia, szablon i wynik `SMTP_ACCEPTED`, `NOT_SENT`
lub `UNCONFIRMED`. Email-service loguje zaakceptowane wysyłki z odbiorcą, kodem
zgłoszenia i `Message-ID`; w przypadku błędu zapisuje odbiorcę, szablon i szczegóły
diagnostyczne. Dostęp do tych logów jest ograniczony. Wynik `SMTP_ACCEPTED` oznacza
przyjęcie wiadomości przez serwer SMTP, nie potwierdzenie dostarczenia do skrzynki.

W bazie nie ma trwałego dziennika wysyłek powiązanego ze zgłoszeniem. Panel Biura
nie pokazuje historii wysyłek ani nie udostępnia ręcznego ponowienia wiadomości.
Historia statusów zgłoszenia nie jest historią e-maili.

## Uruchamianie i środowiska

- Główny `docker-compose.yml` buduje trzy backendy i dwa frontendy, uruchamia
  MariaDB oraz MailHog. Baza ma nazwany wolumen; frontendy i panel MailHog są
  domyślnie opublikowane wyłącznie na loopback hosta, porty backendów i bazy nie
  są publikowane.
- `docker-compose.external-smtp.yml` zastępuje konfigurację MailHog ustawieniami
  jawnie podanego serwera SMTP.
- `docker-compose.gcp-test-http.yml` dodaje Caddy jako reverse proxy HTTP na porcie
  80 i kieruje ruch do frontendu formularza albo Biura według hosta.
- Operator zgłosił, że GCP TEST jest dostępny z Internetu po HTTP i używa SMTP.
  Jest to stan TEST oparty na danych syntetycznych; nie potwierdza konfiguracji
  aktualnie działającej maszyny.
- Konfiguracja Compose przechowuje dane MariaDB na wolumenie, a konfiguracja GCP
  zakłada zewnętrzny plik środowiskowy. Snapshot dysku jest skonfigurowany według
  relacji operatora; pierwszy zakończony snapshot i odtworzenie nie zostały
  potwierdzone. Backup i test restore MariaDB nie zostały potwierdzone.

## Ograniczenia tej oceny

Opis przedstawia stan kodu i znany stan środowiska na dzień przeglądu. Nie jest
auditem bezpieczeństwa, testem obciążeniowym ani potwierdzeniem działających reguł
firewall, sekretów, certyfikatów, backupów lub bieżących kontenerów GCP. Poprzednia,
historyczna ocena pozostaje dostępna w
[`solution-architecture-assessment.md`](solution-architecture-assessment-old.md).
