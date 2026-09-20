# Sigurnosni mehanizmi

## Prijava i pristup

Prijava ide isključivo preko Keycloak-a (OAuth2/OIDC). Backend ne pamti sesije - svaki
zahtjev nosi JWT token u Authorization zaglavlju, i taj token se provjerava pri svakom
pozivu (potpis, važenje, i ko je korisnik).

Iz tokena se čita rola korisnika (ZAPOSLENI ili STUDENT) i na osnovu toga se dozvoljava
ili odbija pristup rutama. Dodatno, kad zaposleni pristupa nekom predmetu ili sesiji,
provjerava se da li je baš on taj predmet kreirao - drugi zaposleni ne može vidjeti niti
mijenjati tuđu evidenciju čak i ako pogodi ili sazna ID.

## QR kod

Svaki kod je zapravo slučajno generisan UUID sa HMAC potpisom preko tajnog ključa koji
postoji samo na serveru. Kod se čuva u Redisu sa rokom trajanja - kad taj rok prođe, kod
više ne postoji i ne može se iskoristiti. Kad se generiše novi kod, stari automatski
prestaje da važi, tako da u svakom trenutku postoji samo jedan ispravan kod po sesiji.

Provjera koda se radi isključivo na serveru - provjerava se da li postoji u Redisu, da li
mu se poklapa potpis, i da li pripada baš toj sesiji za koju se student prijavljuje.

## Sprječavanje duplih i lažnih prijava

Prije upisa se provjerava da li je student već prijavljen na toj sesiji. Uz to, u bazi
postoji jedinstveno ograničenje na kombinaciju sesija-student, tako da čak i ako dva
zahtjeva stignu u isto vrijeme, baza neće dozvoliti da se upiše dvaput.

Prijava je moguća samo dok je sesija aktivna - zatvorena sesija se ne može ponovo otvoriti,
a sesije se same zatvaraju kad prođe njihovo vrijeme.

Prije evidentiranja se provjerava i da li je student uopšte upisan na taj predmet.

## Ograničavanje broja pokušaja

Svaki korisnik ima ograničen broj pokušaja prijave u kratkom vremenskom periodu. Ako
pređe taj broj, dobija poruku da sačeka, bez obzira da li je token ispravan ili ne.

## Trag ko se prijavio i odakle

Uz svaki upis prisustva se bilježi IP adresa, uređaj/browser sa kog je prijava stigla,
tačno vrijeme i koji je token korišten. Zaposleni sve ovo vidi u live pregledu sesije i
može ukloniti unos ako mu djeluje sumnjivo.

## Šta se dešava kad nešto pukne

Ako Redis nije dostupan, sistem to prepozna i vrati jasnu poruku umjesto da se sruši.
Ako backend restartuje, aktivne sesije se učitaju nazad iz baze i QR kod nastavlja da se
mijenja kao da se ništa nije desilo. Ako korisniku padne internet dok skenira, aplikacija
to primijeti i kaže mu da prisustvo nije upisano, umjesto da ga ostavi u neizvjesnosti.

Sve greške na serveru (nepostojeći resurs, nedozvoljen pristup, loš zahtjev) vraćaju
odgovarajući HTTP status sa razumljivom porukom, ne generičku grešku sa stack traceom.

## Mrežna izloženost

Van računara na kojem radi Docker dostupna su samo dva porta: 3000 (aplikacija, kroz nginx)
i 8080 (Keycloak, potreban da se preglednik može prijaviti). Backend (8081), config-server
(8888), PostgreSQL (5432) i Redis (6379) vezani su samo na 127.0.0.1: mogu im pristupiti alati
i skripte sa istog računara, ali ne uređaji na lokalnoj mreži. Kontejneri međusobno
komuniciraju kroz internu Docker mrežu.

To je važno zbog Redisa (u njemu je trenutni QR kod) i config-servera (vraća konfiguraciju,
uključujući tajne vrijednosti). Da su dostupni na mreži, neko bi mogao pročitati QR kod bez
dolaska na nastavu.
