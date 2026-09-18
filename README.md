# Sistem za evidenciju prisustva studenata putem QR koda

Aplikacija za digitalnu evidenciju prisustva na nastavi, rađena za predmet PISIO
na Elektrotehničkom fakultetu u Banjoj Luci.

## Šta treba imati instalirano

- Docker Desktop (ili Docker Engine + Compose v2 na Linuxu)
- Slobodni portovi 3000, 5432, 6379, 8080, 8081, 8888

## Pokretanje

```bash
git clone <repo>
cd qr-attendance
docker compose up --build
```

Aplikacija se otvara na http://localhost:3000

Prvo pokretanje traje 2-4 minuta jer se grade slike i podiže Keycloak sa realmom.

### Testiranje sa telefona

Pošto se prisustvo evidentira skeniranjem koda telefonom, za rad preko lokalne mreže treba
u `.env` upisati IP adresu računara:

```dotenv
HOST_IP=192.168.1.X
```

pa ponovo pokrenuti:

```bash
docker compose up -d --force-recreate
```

Telefon i računar moraju biti na istoj WiFi mreži. Na telefonu se onda otvara
`http://192.168.1.X:3000`.

## Test nalozi

| Uloga | Korisničko ime | Lozinka |
|---|---|---|
| Zaposleni | profesor@etf.unibl.org | prof123 |
| Student | sinisa.sarkanovic@student.etf.unibl.org | student123 |

Ovi nalozi se sami naprave pri prvom pokretanju, iz `keycloak/realm-export.json`.

Keycloak admin konzola je na http://localhost:8080 (admin / admin123).

## Od čega se sistem sastoji

| Servis | Port | Šta radi |
|---|---|---|
| frontend | 3000 | React aplikacija, servirana kroz nginx koji prosljeđuje pozive na backend |
| backend | 8081 | Spring Boot API + WebSocket za live praćenje |
| config-server | 8888 | Drži konfiguraciju backenda, plus service discovery |
| keycloak | 8080 | Prijava i autentifikacija korisnika |
| postgres | 5432 | Baza za aplikaciju i za Keycloak |
| redis | 6379 | QR tokeni (sa isteka) i ograničavanje broja pokušaja |

## Kako se sistem koristi

1. Zaposleni se prijavi i uploaduje spisak studenata (xlsx ili csv).
2. Sistem pročita predmet, godinu, tip nastave i grupu iz fajla. Za prvu godinu se vrši
   automatska podjela, studenti se dodatno raspoređuju u grupe (Г1-Г4, Л1-Л11) prema indeksu.
3. Zaposleni napravi sesiju (tip, datum, termin, grupa) i aktivira je.
4. Pojavljuje se QR kod koji se povremeno automatski mijenja.
5. Student skenira kod telefonom, sistem provjeri token i upiše prisustvo.
6. Zaposleni u realnom vremenu vidi ko se prijavio i može obrisati pogrešan unos.
7. Kad prođe vrijeme sesije, ona se sama zatvori. Poslije toga se može izvesti evidencija
   u XLSX ili PDF, i pogledati analitika prisustva.

## Podešavanja

Sve ide preko `.env` fajla u root folderu:

| Varijabla | Šta znači |
|---|---|
| `HOST_IP` | adresa preko koje se pristupa aplikaciji |
| `STUDENT_EMAIL_DOMAIN` | domen za studentske mejlove |
| `EMPLOYEE_EMAIL_DOMAIN` | domen za mejlove zaposlenih |
| `QR_REFRESH_INTERVAL` | koliko često se mijenja QR kod (u milisekundama) |
| `QR_SECRET` | ključ kojim se potpisuju QR tokeni |
| `ATTENDANCE_THRESHOLD` | prag prisustva za analitiku, u procentima |

Pravila po kojima se studenti prve godine dijele u grupe nalaze se u
`config-server/src/main/resources/config/backend-docker.yml`, sekcija `app.group-rules`.
Mogu se mijenjati bez diranja Java koda - samo treba restartovati config-server i backend.

## Testiranje graničnih slučajeva

U folderu `test-scripts/` su PowerShell skripte kojima se provjeravaju stvari kao što su
istek QR koda, više prijava istog studenta, gomila istovremenih prijava i šta se dešava
kad Redis padne.

## Gašenje

```bash
docker compose down        # ugasi, podaci ostaju
docker compose down -v     # ugasi i obriši sve podatke
```

## Sigurnost

Detaljan opis je u [SECURITY.md](SECURITY.md).