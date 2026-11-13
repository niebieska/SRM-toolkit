# Powiadomienia e-mail dotyczące zgłoszeń

Dokument opisuje bieżące zachowanie powiadomień w aplikacji. Rozróżnia
potwierdzenie otrzymania zgłoszenia od późniejszej wiadomości o jego statusie.

## Potwierdzenie złożenia zgłoszenia

Wiadomość jest wysyłana po zapisaniu zgłoszenia ze statusem `NEW`. Zawiera kod
zgłoszenia i informacje o wybranym turnusie. Nie oznacza zaakceptowania zgłoszenia.

| Typ zgłoszenia | Odbiorcy | Zawartość |
|---|---|---|
| Uczestnik pełnoletni | Uczestnik | Potwierdzenie otrzymania zgłoszenia, kod zgłoszenia i dane turnusu. |
| Uczestnik niepełnoletni | Opiekun | Potwierdzenie zgłoszenia uczestnika, kod zgłoszenia i dane turnusu. Wiadomość nie jest kierowana do uczestnika. |
| Kadra pełnoletnia | Członek kadry | Potwierdzenie otrzymania zgłoszenia, kod zgłoszenia, dane turnusu oraz czytelna nazwa roli i podroli. |
| Kadra niepełnoletnia | Członek kadry i opiekun — osobna wiadomość do każdego adresu | To samo potwierdzenie, z nazwą roli i podroli. |

Wybór odbiorcy zależy od zapisanego oznaczenia pełnoletności zgłoszenia. W
publicznym formularzu jest ono obliczane w chwili składania zgłoszenia.

## Wiadomość po decyzji o statusie

Po ustawieniu statusu `ACCEPTED`, `WAITLIST` lub `REJECTED` Registration API
wysyła wiadomość z aktualnym statusem. Odbiorca jest wyznaczany tak samo dla
uczestnika i kadry:

| Zgłoszenie | Odbiorca wiadomości o statusie |
|---|---|
| Osoba pełnoletnia | Osoba wskazana w zgłoszeniu |
| Osoba niepełnoletnia | Opiekun wskazany w zgłoszeniu |

W przypadku niepełnoletniej kadry potwierdzenie złożenia zgłoszenia trafia do
członka kadry i opiekuna, natomiast wiadomość o późniejszej zmianie statusu
trafia do opiekuna.

### Zawartość zależna od statusu

| Status | Informacja w wiadomości |
|---|---|
| `NEW` — Nowe | Status początkowy. Przy zapisie wysyłane jest potwierdzenie złożenia zgłoszenia opisane wyżej; nie jest to wiadomość o decyzji. |
| `ACCEPTED` — Zaakceptowane | Informacja o akceptacji. Uczestnik otrzymuje instrukcje zaliczki, jeśli są skonfigurowane. Kadra otrzymuje je tylko wtedy, gdy zgłoszona rola lub podrola ma kod `kandydat_na_animatora`; pozostała kadra dostaje podziękowanie za gotowość do przyjazdu. W odpowiednich przypadkach wiadomość zawiera też link do karty uczestnictwa: dla niepełnoletnich oraz dla pełnoletnich uczestników, jeśli włączono kartę dla dorosłych. |
| `WAITLIST` — Lista rezerwowa | Informacja o wpisaniu na listę rezerwową i że organizator skontaktuje się, jeśli zwolni się miejsce. |
| `REJECTED` — Odrzucone | Informacja o odrzuceniu. Powód odrzucenia jest dołączany, jeśli został podany. |

`NEW` nie jest dozwolonym celem operacji zmiany statusu. Jest uwzględnione w
tabeli, ponieważ jest statusem początkowym zgłoszenia.

## Ważne ograniczenia

- Wysłanie odbywa się jako najlepsza próba po zapisaniu zgłoszenia lub statusu;
  błąd e-maila nie cofa zapisu.
- Backend próbuje wysłać powiadomienie po każdym poprawnym żądaniu aktualizacji
  statusu — również wtedy, gdy żądany status jest już bieżącym statusem. Historia
  statusów zapisuje tylko faktyczną zmianę. Powtórne żądanie może więc ponowić
  e-mail, ale nie jest to kontrolowana ani audytowana funkcja ponownej wysyłki.
- Przyjęcie wiadomości przez skonfigurowany serwer SMTP nie potwierdza jej
  dostarczenia do skrzynki odbiorcy. Osobny audyt wiadomości i ręczna ponowna
  wysyłka nie są częścią obecnego rozwiązania.

