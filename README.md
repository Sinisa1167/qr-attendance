# Sistem za evidenciju prisustva studenata putem QR koda

Aplikacija za digitalnu evidenciju prisustva na nastavi, rađena za predmet PISIO
na Elektrotehničkom fakultetu u Banjoj Luci.

## Šta treba imati instalirano

- Docker Desktop (ili Docker Engine + Compose v2 na Linuxu)
- Slobodni portovi 3000, 5432, 6379, 8080, 8081, 8888

Java, Maven i Node.js **nisu potrebni**: backend i frontend se grade unutar Dockera.

## Pokretanje

```bash
git clone <repo>
cd qr-attendance
docker compose up --build
```

Aplikacija se otvara na http://localhost:3000

`.env` fajl nije obavezan: sve varijable imaju demo vrijednosti u `docker-compose.yml`. Ako nešto
treba promijeniti (npr. `HOST_IP`), kopirati `.env.example` u `.env` i izmijeniti samo to.

Prvo pokretanje traje nekoliko minuta (5-10) jer se unutar Dockera kompajlira backend, gradi
frontend i podiže Keycloak sa realmom. Sljedeća pokretanja su brza.

### Testiranje sa telefona

Pošto se prisustvo evidentira skeniranjem koda telefonom, za rad preko lokalne mreže treba
u `.env` upisati IP adresu računara:

```dotenv
HOST_IP=192.168.1.X
```

pa primijeniti promjenu:

```bash
docker compose up -d
```

Telefon i računar moraju biti na istoj WiFi mreži, a Windows firewall mora dozvoliti dolazne
veze na portove 3000 i 8080. Na telefonu se onda otvara `http://192.168.1.X:3000`.

**Kamera i sigurni kontekst.** Preglednici dozvoljavaju pristup kameri samo na HTTPS adresama
(ili na `localhost`). Pošto se aplikacija lokalno služi preko HTTP-a, na telefonu treba jednom
podesiti izuzetak (Android, Chrome):

1. otvoriti `chrome://flags/#unsafely-treat-insecure-origin-as-secure`
2. u polje upisati `http://192.168.1.X:3000` (tačno onako kako se otvara aplikacija)
3. postaviti na **Enabled** i restartovati Chrome

Ako se IP adresa računara promijeni, izuzetak treba ponovo upisati. Na iPhone-u (Safari) ova
opcija ne postoji; za taj slučaj je potreban HTTPS sa pouzdanim sertifikatom.

## Test nalozi

| Uloga | Korisničko ime | Lozinka |
|---|---|---|
| Zaposleni | profesor@etf.unibl.org | prof123 |
| Student | sinisa.sarkanovic@student.etf.unibl.org | student123 |

Ovi nalozi se sami naprave pri prvom pokretanju, iz `keycloak/realm-export.json`.

Keycloak admin konzola je na http://localhost:8080 (admin / admin123). Ovo su demo vrijednosti:
prije upotrebe na mreži kojoj ne vjerujete (npr. učionica) promijeniti `KEYCLOAK_ADMIN_PASSWORD`
u `.env`, jer je konzola dostupna svima na istoj mreži.

## Od čega se sistem sastoji

| Servis | Port | Šta radi |
|---|---|---|
| frontend | 3000 | React aplikacija, servirana kroz nginx koji prosljeđuje pozive na backend |
| backend | 8081 | Spring Boot API + WebSocket za live praćenje |
| config-server | 8888 | Drži konfiguraciju backenda, plus service discovery |
| keycloak | 8080 | Prijava i autentifikacija korisnika |
| postgres | 5432 | Baza za aplikaciju i za Keycloak |
| redis | 6379 | QR tokeni (sa isteka) i ograničavanje broja pokušaja |

Portovi 3000 i 8080 dostupni su na lokalnoj mreži. Portovi 8081, 8888, 5432 i 6379 dostupni su
**samo sa računara na kojem radi Docker** (za razvoj, alate i test-skripte); drugi uređaji na
mreži do njih ne mogu.

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

Sve ide preko `.env` fajla u root folderu (nije obavezan, vidi gore; šablon je `.env.example`):

| Varijabla | Šta znači |
|---|---|
| `HOST_IP` | adresa preko koje se pristupa aplikaciji |
| `STUDENT_EMAIL_DOMAIN` | domen za studentske mejlove |
| `EMPLOYEE_EMAIL_DOMAIN` | domen za mejlove zaposlenih |
| `QR_REFRESH_INTERVAL` | koliko često se mijenja QR kod (u milisekundama) |
| `QR_SECRET` | ključ kojim se potpisuju QR tokeni |
| `ATTENDANCE_THRESHOLD` | prag prisustva za analitiku, u procentima |

Vrijednosti u repozitoriju su samo demo; za stvarnu upotrebu promijeniti lozinke i `QR_SECRET`.

Pravila po kojima se studenti prve godine dijele u grupe nalaze se u
`config-server/src/main/resources/config/backend-docker.yml`, sekcija `app.group-rules`.
Mogu se mijenjati bez diranja Java koda, ali je konfiguracija pakovana u config-server, pa
nakon izmjene treba ponovo izgraditi i pokrenuti:

```bash
docker compose up -d --build config-server backend
```

Napomena: granice grupa su namjerno malo šire od onih u tekstu specifikacije (npr. Г3 do 11124,
Г4 do 1259, Л6 do 11124, Л9 do 1259), jer testna lista studenata sadrži indekse
(1258/25, 1259/25, 11124/25) koje bi inače ostale izvan svih grupa.

## Razvoj: ponovni build

| Situacija | Komanda |
|---|---|
| Prvi put, ili nakon `git pull` | `docker compose up -d --build` |
| Promijenjen samo backend | `docker compose up -d --build backend` |
| Promijenjen samo frontend | `docker compose up -d --build frontend` |
| Ništa nije mijenjano u kodu | `docker compose up -d` |
| Promijenjen `.env` | `docker compose up -d` |
| Logovi | `docker compose logs -f backend` |

Bez `--build` se pokreće ranije izgrađena slika, pa se izmjene koda neće vidjeti.

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
