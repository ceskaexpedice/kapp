# Import kurátorských výsledků z TSV

Modul `services:init` obsahuje importér TSV souborů. Slouží k dávkovému propsání kurátorských výsledků z tabulek, typicky exportovaných z Excelu.

## Co importér dělá

Importér:

1. Načte konfiguraci aplikace z `<user-home>/.kapp/app.conf`.
2. Načte mapování sloupců z `<user-home>/.kapp/tsv-mappings.json`.
3. Projde všechny `.tsv` soubory ve vstupním adresáři.
4. Pro každý řádek najde PID.
5. Podle sloupce se zveřejněním určí požadovaný kurátorský stav.
6. Doplní štítky a poznámky.
7. Najde existující dokument v Solru.
8. Pokud lze přechod provést, zapíše workflow změnu do Solru.
9. Na konci commitne kolekce `titles` a `authors`.

## Vstupní adresář

Výchozí vstupní adresář je:

```text
<user-home>/.kapp/Data
```

Při spuštění je možné předat jiný adresář jako první argument.

Orientační postup spuštění:

```powershell
.\gradlew :services:init:run --args="C:\cesta\k\adresari\s\tsv"
```

Pokud argument není předaný, použije se výchozí adresář.

## Mapování sloupců

Mapování se načítá ze souboru:

```text
<user-home>/.kapp/tsv-mappings.json
```

Mapování může být:

- přes přesný název souboru pomocí `match`,
- přes prefix názvu souboru pomocí `matchStartsWith`.

Příklad:

```json
{
  "mappings": [
    {
      "matchStartsWith": "kontrola_MONOGRAFIE_1900-1975.xlsx",
      "headers": {
        "Identifikátor UUID": "pid",
        "Zveřejnit": "public_string",
        "rok úmrtí posledního z autorů (RRRR)": "year_of_death_last_author",
        "Poznámky": "note",
        "Kontroloval (XX)": "checked_by"
      }
    }
  ]
}
```

## Podporované interní klíče

Podle aktuálního kódu importér aktivně zpracovává hlavně tyto interní klíče:

- `pid`
- `public_string`
- `note`
- `year_of_death_last_author`
- `date_issued_str`
- `checked_by`

Ostatní namapované sloupce se mohou v mapování objevit, ale aktuální importér je buď ignoruje, nebo je má zakomentované.

## PID

Sloupec namapovaný na `pid` může obsahovat:

- jedno UUID,
- UUID s prefixem `uuid:`,
- dvě UUID oddělená mezerou nebo znakem `|`.

Pokud prefix `uuid:` chybí, importér jej doplní.

Pokud jsou v hodnotě dvě UUID, první se bere jako root PID a druhé jako PID konkrétní jednotky.

Pokud jsou UUID více než dvě, importér vypíše upozornění a použije první dvě.

Řádky bez platného PID se přeskočí.

## Sloupec zveřejnění

Sloupec `public_string` se převádí takto:

- `ano`, `yes`, `true` -> `CONTROLLED_PUBLIC`
- `ne`, `no`, `false` -> `CONTROLLED_PRIVATE`
- `?` -> `CONTROLLED_UNKNOWN`
- prázdná hodnota -> `TO_CONTROL`
- jiná hodnota -> `CONTROLLED_UNKNOWN` a přidá se štítek `excel_incorrect_license`

Pozor: kurátorský workflow přechod v kódu dovoluje jen cílové stavy `CONTROLLED_*`. Prázdná hodnota tedy typicky nevynutí přechod do `TO_CONTROL`, protože tento cílový stav není mezi povolenými kurátorskými přechody.

## Rok úmrtí posledního autora

Sloupec `year_of_death_last_author` se uloží jen tehdy, když hodnota vypadá jako čtyřmístné číslo.

Hodnota se zapisuje do pole:

```text
last_year_death
```

Tato hodnota může ovlivnit automatické přechody workflow při dalším vyhodnocení.

## Datum vydání

Importér má zpracování pro interní klíč:

```text
date_issued_str
```

Při zpracování se snaží normalizovat výrazy typu:

- `asi 1900`
- `okolo r. 1900`
- `po roce 1900`
- `před r. 1900`
- `mezi r. 1900 a 1910`
- rozsahy s pomlčkou
- nejisté roky s `?`

Výsledkem jsou pole:

- `date_issued_str`
- `date_issued_year`
- `date_issued_start_year`
- `date_issued_end_year`

Poznámka k aktuálnímu stavu: mapování v repozitáři používá pro datum hodnotu `date.str`, zatímco kód očekává `date_issued_str`. Je potřeba sjednotit mapování nebo kód, jinak se datum z TSV nemusí zpracovat.

## Štítky a poznámky

Každý řádek dostane štítek:

```text
excel
```

Při problémech se přidávají další štítky:

- `excel_incorrect_license`
- `excel_failed_to_parse_date`
- `excel_suspicious_year_range`
- `excel_unexpected date format`

Poznámky ze sloupce `note` se přidávají do pole `note` pomocí atomické operace `add-distinct`.

## Uživatel změny

Pokud je vyplněn sloupec `checked_by`, použije se jako `state_user`.

Pokud není vyplněn, importér nastaví:

```text
Excel without curator
```

Workflow změna, pokud proběhne, používá uživatele:

```text
excelImporter
```

## Očekávaný výsledek

Po úspěšném importu mají odpovídající dokumenty v Solru:

- aktualizovaný `state`,
- aktualizovaný `state_user`,
- aktualizovaný `state_reason`,
- doplněnou položku ve `state_history`,
- aktualizované `assigned_licenses` podle cílového stavu,
- doplněné štítky a poznámky.

## Kdy řádek nezmění stav

Řádek nemusí změnit stav, pokud:

- nemá platný PID,
- dokument není v Solru,
- dokument nemá model,
- požadovaný stav není povolený kurátorským přechodem,
- při zpracování nastane chyba a importér ji pouze vypíše pro konkrétní soubor/řádek.
