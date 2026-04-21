# Solr data a vyhledávání

Aplikace používá Solr jako hlavní datové úložiště. V dostupném kódu se pracuje se dvěma kolekcemi:

- `titles`
- `authors`

## Kolekce `titles`

Kolekce `titles` obsahuje záznamy dokumentů z Krameria a workflow informace.

### Identifikace dokumentu

- `pid` primární identifikátor dokumentu.
- `root_pid` identifikátor kořenového dokumentu.
- `model` typ dokumentu.
- `title` název dokumentu.
- `root_title` název kořenového dokumentu.
- `digital_library` zkratka zdrojové knihovny.

### MODS a bibliografická metadata

- `biblio_mods` plné MODS XML.
- `biblio_mods_md5` hash MODS XML.
- `date_issued_str` původní text data vydání.
- `date_issued_year` rozhodný rok vydání.
- `date_issued_start_year` začátek rozsahu vydání z TSV importu.
- `date_issued_end_year` konec rozsahu vydání z TSV importu.
- `physical_locations` fyzické lokace vytažené z MODS.

### Autoři

- `authors` čitelný seznam autorů.
- `authors_kramerius` JSON reprezentace autorů vytažená z MODS.
- `authors_ids` interní identifikátory autorů.
- `nameIdentifier` autoritní identifikátor, pokud je dostupný.
- `last_year_death` rok úmrtí posledního autora nebo jiný rozhodný rok.

### Licence

- `licenses` licence načtené ze zdrojového Krameria.
- `licenses_<acronym>` licence pro konkrétní digitální knihovnu, například `licenses_mzk`.
- `assigned_licenses` licence přiřazené workflow aplikace.

Workflow umí automaticky udržovat zejména hodnoty:

- `public`
- `onsite`

### Workflow pole

- `state` aktuální stav.
- `state_date` datum poslední změny stavu.
- `state_user` uživatel nebo systém, který stav změnil.
- `state_reason` důvod změny.
- `state_history` historie změn ve formě JSON položek.

### Pomocná pole

- `is_hierarchical` značí rodičovský/hierarchický dokument.
- `labels` štítky, často z TSV importu.
- `note` poznámky z TSV importu.

## Kolekce `authors`

Kolekce `authors` obsahuje autoritní dokumenty vytvořené z MODS.

Používaná pole podle sdíleného kódu zahrnují:

- `date`
- `year_death`
- `year_birth`
- `date_death`
- `date_birth`
- `confirmed_end_year`
- `confirmed_start_year`

Konkrétní strukturu dokumentu vytváří objekt autora při převodu na Solr dokument.

## Běžné operátorské dotazy

Dokumenty čekající na kontrolu:

```text
state:TO_CONTROL
```

Rodiče s otevřenými dětmi:

```text
state:PARENT_OPEN
```

Dokumenty importované z Excelu/TSV:

```text
labels:excel
```

Dokumenty s chybou v hodnotě zveřejnění v TSV:

```text
labels:excel_incorrect_license
```

Dokumenty s podezřelým rozsahem roku vydání (obvykle díla vydaná v budoucnosti apod.):

```text
labels:excel_suspicious_year_range
```

Dokumenty, které aplikace nastavila jako veřejné:

```text
assigned_licenses:public
```

Dokumenty, které aplikace nastavila jako on-site:

```text
assigned_licenses:onsite
```

Dokumenty ze zdroje MZK:

```text
digital_library:mzk
```

## Atomické aktualizace

Existující dokumenty se často aktualizují atomicky. Používají se operace:

- `set`
- `add`
- `add-distinct`
- `remove`

## Stavové hodnoty v indexu

Přestože enum má i textové hodnoty, do Solru se zapisují enum názvy. Operátorské dotazy proto používejte s hodnotami typu:

```text
NONCONTROLLED_PUBLIC
CONTROLLED_PRIVATE
TO_CONTROL
PARENT_OPEN
```

Ne s hodnotami:

```text
noncontrolled_public
controlled_private
to_control
parent_open
```
