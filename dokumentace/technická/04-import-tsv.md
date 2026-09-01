# Tabulkové importy a poslední autor

TSV soubory jsou exporty ze starších Excelových tabulek, ve kterých uživatelé před vznikem současné webové aplikace evidovali stejný typ kurátorských údajů a prováděli obdobný rozhodovací workflow. Import proto slouží především jako převod historické práce z původního tabulkového postupu do datového modelu a workflow Kurátorské aplikace.

Nejde o nový paralelní způsob běžné kurátorské práce. Po převedení původních tabulek uživatelé pokračují ve stejném procesu prostřednictvím webové aplikace; TSV nástroje zachovávají a převádějí výsledky práce provedené před jejím nasazením.

Backend obsahuje dvě navazující cesty:

- `single-use-services:excel-importer` importuje kurátorské TSV tabulky do `titles`,
- `single-use-services:last-author-updater` následně páruje hodnoty posledních autorů s core `authors`.

Nejde o webový upload. Nástroje jsou určené pro řízené jednorázové backendové běhy.

## TSV importer

Hlavní třída:

```text
cz.inovatika.TsvToSolrIndexer
```

Výchozí vstupy:

```text
<user-home>/.kapp/Data
<user-home>/.kapp/tsv-mappings.json
<user-home>/.kapp/app.conf
```

Jiný adresář lze předat jako první argument:

```powershell
.\gradlew :single-use-services:excel-importer:run --args="C:\cesta\k\tsv"
```

## Mapování souborů a sloupců

`tsv-mappings.json` vybírá mapování přes přesný název (`match`) nebo prefix (`matchStartsWith`). Každá vstupní hlavička se převádí na interní klíč.

Používané klíče:

- `pid`,
- `public_string`,
- `note`,
- `year_of_death_last_author`,
- `public_since`,
- `date_issued_str`,
- `checked_by`,
- `checked_by_override`,
- `last_author`,
- `title`,
- `physical_locations`.

Neznámé namapované sloupce se přidají do poznámky. Nenamapované hlavičky se vypíší jednou do logu.

## PID a root PID

Hodnota PID může obsahovat UUID oddělená mezerou, `;` nebo `|`. Importer doplní prefix `uuid:`:

- jedno UUID se použije jako `pid` i `root_pid`,
- dvě UUID znamenají první `root_pid` a druhé `pid`,
- při více UUID se použijí první dvě a zapíše se upozornění.

Řádek bez platného PID se přeskočí.

## Kurátorská data

Importer:

- přidá štítek `excel`,
- zapisuje poznámky přes `add-distinct`,
- zapisuje uživatele z `checked_by`, případně z `checked_by_override`,
- normalizuje datum vydání do `date_issued_year_kapp`,
- zapisuje `last_year_death`,
- rozpoznává anonymní, kolektivní, úřední a pseudonymní díla,
- nerozpoznaného posledního autora parsuje do `last_author_excel`,
- mapuje siglu z tabulky do `archiver_location`,
- značí dokument jako uživatelsky změněný.

Kurátorsky opravený rok je záměrně v `date_issued_year_kapp`; sklizený `date_issued_year` zůstává zachovaný.

## Stavy a diagnostické štítky

`public_string` se převádí na povolený kurátorský cílový stav. Přechod se provede jen pokud existuje model a `Workflow.calculateCuratorTransition` jej dovolí.

Importer přidává diagnostické štítky například pro:

- neznámou hodnotu zveřejnění,
- neparsovatelné datum,
- podezřelý rozsah roku,
- nečekaný rok zveřejnění,
- problém s parsováním posledního autora,
- nejistou anonymitu/úřednost/kolektivnost.

Nový titul, který existuje jen v Excelu, začíná jako `IMPORTED`. U existujícího titulu se vychází z uloženého stavu.

## Zápis

Aktualizace jsou atomické. Na konci `finally` blok explicitně commituje `titles` a `authors`.

Solr, harvester, importer a updater se provozují v Docker prostředí připraveném repozitářem `nkp-deploy`. Compose publikuje Solr na portu `8983`, proto importer záměrně používá:

```text
http://localhost:8983/solr
```

Nejde o náhradní nebo dočasnou adresu, ale o očekávanou cestu k Solru v tomto způsobu nasazení.

## Parser posledního autora

`LastAuthorExcelParser` čistí role a interpunkci, rozděluje více jmen a rozpoznává roky narození/úmrtí. Výstup ukládá do `last_author_excel`. Speciální texty jako anonymní, kolektivní, pseudonymní nebo úřední dílo se místo jména převádějí na příznaky workflow.

## Last-author updater

Hlavní třída:

```text
cz.inovatika.impl.LastAuthorUpdaterMain
```

Načítá `.kapp/app.conf`, prochází tituly s údaji z Excelu, hledá kandidáty v `authors`, deduplikuje je a připravuje kurátorská autoritní data titulu.

Volby:

| Volba | Výchozí | Význam |
| --- | ---: | --- |
| `--rows=<n>` | `200` | Velikost vstupní stránky Solr. |
| `--batch-size=<n>` | `200` | Velikost zapisované dávky. |
| `--limit=<n>` | `0` | Maximum titulů; `0` znamená bez limitu. |
| `--dry-run` | vypnuto | Provede párování bez zápisu. |
| `--verbose` | vypnuto | Vypíše podrobnosti párování. |

Bezpečný první běh:

```powershell
.\gradlew :single-use-services:last-author-updater:run --args="--dry-run --verbose --limit=100"
```

Produkční běh:

```powershell
.\gradlew :single-use-services:last-author-updater:run --args="--rows=200 --batch-size=200"
```

Po zápisu spusťte updater workflow, aby se změna posledního autora promítla do stavu a `release_year`.

## Doporučený postup

1. Zálohujte `titles` a `authors`.
2. Ověřte mapování na malém TSV.
3. Zkontrolujte diagnostické štítky a několik změn stavu.
4. Spusťte last-author updater v `--dry-run`.
5. Zkontrolujte kandidáty a konflikty.
6. Spusťte zapisující běh s omezeným `--limit`.
7. Teprve potom zpracujte celý soubor a spusťte workflow updater.
