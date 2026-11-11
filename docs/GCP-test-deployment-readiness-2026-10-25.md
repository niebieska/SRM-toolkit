# Raport gotowości wdrożeniowej GCP TEST

Data audytu: 2026-10-25. Audyt dotyczył repozytorium i stanu infrastruktury opisanego przez operatora. Nie łączyłem się z GCP ani VM, nie budowałem obrazów, nie uruchamiałem kontenerów i nie zmieniałem konfiguracji wdrożeniowej.

To historyczny raport sprzed uruchomienia aplikacji na GCP TEST. Został
zastąpiony przez późniejszy stan zapisany w [WIP](WIP.md) i
[checkliście wdrożenia](deployment-checklist.md); poniższe sekcje opisują stan
z dnia audytu, a nie bieżącą gotowość.

## 1. Przegląd repozytorium

- Gałąź: `feature/test-deployment-packaging`.
- Drzewo robocze: jedyny widoczny nieśledzony plik to `sth.json`. Nie odczytywałem jego zawartości.
- Struktura obejmuje trzy API Spring Boot, dwa frontendy Vue, Compose, konfigurację Nginx i inicjalizację MariaDB. Nie ma konfiguracji TLS ani workflow CI w `.github`.
- Istotne pliki: [`docker-compose.yml`](../docker-compose.yml), [`.dockerignore`](../.dockerignore), [przykład konfiguracji środowiska](../.env.example), [checklista wdrożenia](deployment-checklist.md) i [lokalne uruchamianie w kontenerach](Local-container-testing.md).

## 2. Postęp infrastruktury

Stan GCP w tej sekcji traktuję jako **zgłoszony i wcześniej zweryfikowany przez operatora**, nie jako wynik mojego dostępu do projektu.

### Zakończone według przekazanych informacji

- VM Ubuntu 24.04, adres wewnętrzny i zarezerwowany adres zewnętrzny, VPC i podsieć.
- Administracja SSH przez IAP, OS Login, blokada kluczy projektowych; brak przypisanego konta usługi.
- Reguły aplikacyjne nie są otwarte z Internetu. Porty 80, 443, 8443, 3306 i MailHog pozostają zamknięte.
- Dysk danych jest zamontowany pod `/srv/srm-data`; automatyczne montowanie sprawdzono po restarcie VM.
- Docker, containerd, Git oraz Compose v5.6.1 są zainstalowane; Docker używa katalogu danych na dysku `/srv/srm-data`.
- Budżet GCP i progi alertów są skonfigurowane. Alerty budżetowe **nie są limitem wydatków**.

### Skonfigurowane, ale nadal do potwierdzenia

- Harmonogram snapshotów jest ustawiony, ale przy ostatniej kontroli nie było widocznego ukończonego snapshotu.
- Nie wykonano testu odtworzenia danych ze snapshotu.
- Szacowane 404 PLN miesięcznie to przybliżenie, nie gwarantowany koszt.

### Jeszcze nierozpoczęte

- Checkout aplikacji w `/opt/srm-toolkit` i prywatny plik konfiguracyjny w `/etc/srm-toolkit`.
- Uruchomienie usług, pierwsza inicjalizacja baz, migracje Flyway i testy aplikacji na VM.
- Backup spójny z MariaDB oraz próba odtworzenia.
- Konfiguracja publicznego HTTPS i otwieranie portów — **nie są częścią obecnego kroku**.

## 3. Audyt konfiguracji wdrożeniowej

| Obszar | Pliki / stan | Ryzyko i zalecane działanie |
|---|---|---|
| **Kontekst budowania obrazów** | [`.dockerignore`](../.dockerignore), [Dockerfile Biuro frontend](../frontend/biuro-frontend/Dockerfile), [Dockerfile Registration frontend](../frontend/registration-frontend/Dockerfile) | Aktualny `.dockerignore` nie wyklucza plików `deploy/nginx/*.conf`; wszystkie trzy wymagane pliki są w repozytorium i pasują do ścieżek `COPY`. Nie znaleziono więc wskazanej wcześniej blokady kontekstu. Buildów nie uruchomiono w tym audycie, więc ich wynik nadal wymaga potwierdzenia. |
| **Nieśledzony plik** | `sth.json` jest nieśledzony, a `.dockerignore` go nie wyklucza. Dockerfile kopiuje wybrane katalogi, więc plik nie powinien wejść do warstw obrazu; może jednak znaleźć się w kontekście budowania. | Przed buildem rozstrzygnąć, czy plik ma pozostać lokalny. Jeśli tak, wykluczyć go z kontekstu. Jego zawartości nie odczytywano. |
| **Compose i porty** | [`docker-compose.yml`](../docker-compose.yml) | To konfiguracja opisana jako lokalna. Frontendy są przypięte do `127.0.0.1:5173/5174`, MailHog UI do `127.0.0.1:8025`; API, SMTP i MariaDB nie mają opublikowanych portów. To dobry prywatny układ do dostępu przez tunel IAP, ale nie konfiguracja publicznego wejścia. |
| **Backendy i frontend** | Dockerfile używają Java 21, Maven 3.9 i Node 22; Nginx serwuje SPA i proxy’uje `/api`. | Java 21 jest zgodna z projektem. Frontendy budują się z `VITE_API_BASE_URL=/`, więc ruch przeglądarki idzie do proxy na tym samym originie. Backend CORS dopuszcza obecnie tylko lokalne originy; dla tego same-origin układu CORS nie powinien być potrzebny. |
| **Bazy i migracje** | [`docker-compose.yml`](../docker-compose.yml), [skrypt inicjalizacji Biuro](../deploy/mariadb/10-biuro-db.sh), konfiguracje Flyway w obu API. | MariaDB 11.4 inicjalizuje `registration_db`, a skrypt tworzy `biuro_db` i odrębnego użytkownika. Skrypt inicjalizacyjny wykona się tylko przy pierwszym utworzeniu pustego katalogu danych; zmiana env nie naprawi istniejącego wolumenu ani nie zmieni haseł. Flyway uruchamia migracje aplikacji. Testy MariaDB są opcjonalne i historycznie pomijane — trzeba uruchomić je przed akceptacją. |
| **Trwałość danych** | Compose używa wolumenu nazwanego `mariadb-data`; Docker RootDir na VM ma być na `/srv/srm-data/docker`. | Przy opisanym Docker RootDir dane wolumenu będą na dysku danych i przetrwają odtworzenie kontenera. Compose **nie używa bezpośrednio** `/srv/srm-data/mariadb`, tylko katalogu wolumenów pod Docker RootDir. Kod uploadów nie występuje; `/srv/srm-data/uploads` nie jest obecnie wykorzystywany. |
| **Gotowość usług** | Compose sprawdza MariaDB przez `healthcheck.sh`; API sprawdzają tylko otwarcie TCP. Email czeka na `mailhog` ze statusem `service_started`. | `depends_on` porządkuje start, lecz test TCP nie potwierdza gotowości HTTP, migracji, bazy ani katalogu Turnusów. Do akceptacji potrzebne są smoke testy na poziomie HTTP i przepływów. MailHog nie ma własnego healthchecka. |
| **Zasoby i logi** | Compose ustawia `restart: unless-stopped`, ale nie definiuje limitów CPU/pamięci, limitów JVM ani rotacji logów. | Na VM 8 GiB trzy JVM oraz MariaDB mogą konkurować o pamięć. Przed dłuższym testem warto określić limity JVM/Compose i rotację logów, żeby nie dopuścić do OOM ani zapełnienia dysku. |
| **TEST i poczta** | Compose wymusza `MAIL_HOST=mailhog`, `MAIL_ENABLED=true` i testowy nadawczy adres; SMTP nie jest publikowany. | Stos Compose nie powinien wysyłać prawdziwej poczty. Szablony zawierają jednak odnośniki i obraz logo z `rekolekcje.scj.pl`; podgląd wiadomości może prowadzić do zewnętrznej strony, a klient pocztowy może pobrać obraz z tego hosta. To nie jest połączenie SMTP aplikacji z produkcją, ale warto o tym pamiętać przy danych testowych. |
| **HTTPS i nagłówki proxy** | [Konfiguracja Nginx Biuro](../deploy/nginx/biuro-frontend.conf), analogiczna konfiguracja Registration. Obie nasłuchują HTTP na porcie 80. | Brak reverse proxy, konfiguracji TLS, HSTS ani ścieżki odnowienia certyfikatu. `X-Forwarded-Proto` jest ustawiane na `$scheme` wewnętrznego Nginx, czyli `http`; przy przyszłym terminowaniu TLS przed kontenerem trzeba poprawnie przekazać zaufany zewnętrzny schemat. |
| **Sekrety** | `.env` nie istnieje w bieżącym drzewie, jest ignorowany; historia Git zawiera wcześniejsze wersje. `.env.example` ma puste pola sekretów. | Wartości z historii należy traktować jako ujawnione i obrócić przed użyciem GCP. Compose przekazuje sekrety jako zmienne środowiskowe kontenerów; plik root-only z `/etc/srm-toolkit` ogranicza dostęp plikowy, ale administrator Dockera nadal może odczytać konfigurację kontenera. |
| **Skrypty i automatyzacja** | `scripts/run-local.sh` używa `gnome-terminal`; brak workflow CI. | Skrypt jest do pracy lokalnej, nie do uruchamiania na serwerze. Budowanie obrazów pomija testy Maven, więc testy należy uruchomić oddzielnie. `docker compose` nie jest dostępne lokalnie w tym środowisku; zainstalowane `docker-compose` to 1.25.0, którego dokumentacja projektu nie wspiera. Nie wykonano więc walidacji Compose. |

## 4. Brakująca konfiguracja

Przed budowaniem lub wdrożeniem potrzebne są:

1. Nie potrzeba poprawki `.dockerignore` dla plików Nginx. Przed buildem ustalić, czy lokalny `sth.json` ma być wykluczony z kontekstu.
2. Prywatny plik Compose w `/etc/srm-toolkit`, z prawami tylko dla roota i bez wartości z historii Git. Wymagane hasła Compose obejmują hasło roota MariaDB, hasła obu użytkowników DB, `TURNUS_LOOKUP_PASSWORD`, `REGISTRATION_SERVICE_PASSWORD`, `EMAIL_SERVICE_PASSWORD` i `JWT_SECRET`. SMTP w tej konfiguracji ma pozostać skierowany do MailHog.
3. Decyzja, czy wolumen nazwany pod Docker RootDir jest docelową lokalizacją MariaDB, czy dane mają być montowane bezpośrednio z `/srv/srm-data/mariadb`.
4. Limity pamięci/JVM oraz rotacja logów, zanim stos będzie długo pracował na VM.
5. Weryfikacja migracji na pustych bazach i testy MariaDB-gated; osobna kopia bazy oraz udokumentowana próba restore.
6. Dla publicznego etapu: osobny projekt TLS/routingu, nagłówki proxy, rate limits i zabezpieczenia przeglądarki. Niczego z tego nie należy teraz aktywować.

## 5. Ocena bezpieczeństwa

**Weryfikacja builda:** nie znaleziono statycznej blokady w `.dockerignore`, ale obrazy nie były budowane podczas tego audytu. Gotowość builda pozostaje niepotwierdzona do czasu sprawdzenia na aktualnym HEAD.

**Przed jakimkolwiek publicznym dostępem:**

- Obrócić stare sekrety, które znalazły się w historii Git; usunięcie `.env` z bieżącego drzewa nie usuwa historii.
- Zaprojektować i przetestować TLS oraz automatyczne odnawianie.
- Dodać limity prób logowania i zgłoszeń. Obecny Nginx nie konfiguruje rate limiting.
- Ocenić token Biuro przechowywany w `localStorage`; XSS może umożliwić jego kradzież. Brak nagłówków CSP w obecnych konfiguracjach Nginx.
- Ustawić rotację logów oraz potwierdzić, że logi nie ujawniają sekretów ani zbędnych danych osobowych.

**Stan obecny:** brak publicznych reguł ingress jest poprawny i ogranicza ekspozycję. Nie należy teraz otwierać portów. Po uruchomieniu stosu backendy, DB i SMTP nie będą opublikowane na hoście, ale wszystkie kontenery należą do jednej sieci bridge.

### Certyfikat dla samego adresu IP

Wymóg certyfikatu dla `34.116.129.35` jest obecnie wykonalny: Let’s Encrypt udostępnia certyfikaty IP, ale są ważne około **160 godzin, czyli nieco ponad 6 dni**, i wymagają profilu `shortlived` oraz walidacji HTTP-01 lub TLS-ALPN-01. [Informacja Let’s Encrypt o ogólnej dostępności](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability)

Certbot 5.4+ potrafi wystawić taki certyfikat, ale według dokumentacji Let’s Encrypt obsługa IP przez instalatory Nginx/Apache nie jest gotowa: trzeba samodzielnie podpiąć certyfikat oraz skonfigurować niezawodny deploy/reload hook i odnowienie. [Instrukcja obsługi IP w Certbot](https://letsencrypt.org/2026/03/11/shorter-certs-certbot) Odnowienie wymaga publicznej osiągalności challenge na porcie 80 (HTTP-01) albo 443 (TLS-ALPN-01). To przyszły warunek, nie rekomendacja otwierania ich teraz. Ten sam certyfikat IP może być użyty na portach 443 i 8443.

Google-managed certyfikaty w dokumentacji dotyczą walidacji domen i obsługiwanych load balancerów; nie są gotowym mechanizmem TLS dla obecnej VM z bezpośrednim IP. [Dokumentacja Google-managed certyfikatów](https://docs.cloud.google.com/load-balancing/docs/ssl-certificates/google-managed-certs?hl=en)

## 6. Gotowość do etapów wdrożenia

| Etap | Ocena | Uzasadnienie |
|---|---|---|
| Budowanie obrazów | **Niezweryfikowane** | `.dockerignore` nie wyklucza wymaganych plików Nginx, ale build nie został uruchomiony w tym audycie. |
| Walidacja Compose | **Niezweryfikowana** | Lokalnie dostępny jest tylko Compose 1.25.0, a nie wspierany przez projekt plugin v2; na VM polecenia jeszcze nie uruchomiono. |
| Wdrożenie wewnętrzne | **Jeszcze nie** | Nie ma jeszcze checkoutu i pliku środowiskowego na VM; build ma znaną blokadę. Po poprawce konfiguracja portów loopback pozwala na prywatną weryfikację przez IAP bez otwierania ingress. |
| Publiczny HTTPS | **Nie** | Brak TLS/proxy, odnowienia certyfikatu, rate limits i przeglądu bezpieczeństwa; reguły ingress są słusznie zamknięte. |

Dokumentacja zapisuje wcześniejsze lokalne buildy i uruchomienie stosu. Nie uruchomiono builda ani testów podczas tego audytu, więc historyczny wynik nie stanowi weryfikacji obecnego HEAD.

## 7. Następny bezpieczny krok

Nie uruchamiać jeszcze builda. Po zatwierdzeniu poprawki `.dockerignore` i umieszczeniu dokładnie tej gałęzi na VM, pierwszym niezmieniającym stanu testem będzie:

```bash
cd /opt/srm-toolkit
docker compose --env-file /etc/srm-toolkit/compose.env -p srm-test config --quiet
```

Oczekiwany wynik: brak tekstu i kod wyjścia `0`. Nie wypisuje rozwiązanej konfiguracji ani sekretów i nie uruchamia usług; rollback nie jest potrzebny. Jeśli env file jeszcze nie istnieje, ten krok należy odłożyć — nie kopiować `.env.example` jako gotowej konfiguracji bez uzupełnienia wymaganych wartości.

## 8. Lista postępu

- [x] Gałąź repozytorium to `feature/test-deployment-packaging`.
- [x] Infrastruktura VM, dysku, Docker i IAP — według przekazanej, wcześniej zweryfikowanej informacji operatora.
- [x] Brak publicznego ingress i brak wdrożonych kontenerów — według przekazanych informacji.
- [!] Pierwszy snapshot i test odtworzenia — niepotwierdzone.
- [!] Obrazy — build nie został ponownie zweryfikowany na obecnym HEAD; nie znaleziono blokady w `.dockerignore`.
- [!] Compose — walidacja nie została wykonana; lokalny plugin Compose v2 jest niedostępny.
- [ ] Prywatna konfiguracja TEST i checkout na VM.
- [ ] Build, uruchomienie wewnętrzne i smoke testy — po poprawce i osobnej zgodzie na uruchomienie.
- [ ] Backup MariaDB i test restore.
- [ ] Projekt IP-TLS i odnowienia — przed osobną decyzją o publicznym dostępie.
- [ ] Security review, rate limiting i akceptacja operatora przed jakąkolwiek zmianą firewall.

Nie zmieniono konfiguracji wdrożeniowej. `.dockerignore` nie wymaga poprawki dla plików Nginx. Nieśledzony `sth.json` pozostał nietknięty.
