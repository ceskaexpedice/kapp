# Solr data a vyhledávání

Solr je společné úložiště backendu i webu. Provozní definice cores a schémat jsou v `nkp-deploy/solr`.

## Cores

- `titles` - bibliografická, workflow a kurátorská data titulů,
- `authors` - autoři vytěžení z titulů a upravení v KAPP,
- `aut` - prohledávaný autoritní zdroj,
- `contracts` - smlouvy a jejich metadata,
- `configuration` - dynamická konfigurace UI a oprávnění.

## `titles`

### Identifikace a zdroj

- `pid`, `root_pid`, `model`,
- `title`, `root_title`,
- `digital_library`,
- `physical_locations`,
- `biblio_mods`, `biblio_mods_md5`.

Jeden centrální core obsahuje data z více Krameriů. `digital_library` a `licenses_<acronym>` zachovávají původ.

### Roky a autorství

- `date_issued_str` - původní text data vydání,
- `date_issued_year` - rok odvozený ze sklizených dat,
- `date_issued_year_kapp` - kurátorsky opravený rok s vyšší prioritou,
- `last_year_death` - rozhodný rok úmrtí posledního autora,
- `last_author_excel` - hodnota posledního autora z tabulky,
- `release_year` - vypočtený rok uvolnění,
- `is_pseudonymous`, `is_anonymous`, `is_collective`, `is_official`.

### Autoři

- `authors_kramerius` - autoři ze zdrojových MODS,
- `authors_kapp` - kurátorsky upravení autoři,
- `authors` a `authors_ids` - vyhledávací/odvozené hodnoty,
- `author_modified` a `modified_by_user` - příznaky uživatelských úprav.

### Licence a smlouvy

- `licenses` - zdrojové licence,
- `licenses_<acronym>` - zdrojové licence konkrétní knihovny,
- `assigned_licenses` - výsledné licence KAPP,
- `contracts` nebo odpovídající vazební pole webu - smlouvy přiřazené k titulu.

Updater může `assigned_licenses` měnit workflow přechodem nebo přidáním licence ze smlouvy.

### Workflow

- `state`,
- `state_date`,
- `state_user`,
- `state_reason`,
- `state_history`,
- `last_modified_date`.

Enum názvy se do indexu ukládají velkými písmeny, například `TO_CONTROL`, `CONTROLLED_PRIVATE` a `PARENT_OPEN`.

### Pomocná data

- `is_hierarchical`,
- `labels`,
- `error_labels`,
- `note`,
- `batch`.

## `authors`

Core obsahuje mimo jiné:

- `id`, `old_id`, `nameIdentifier`,
- `full`, `given`, `family`,
- `date`, `date_birth`, `date_death`,
- `year_birth`, `year_death`,
- `confirmed_start_year`, `confirmed_end_year`,
- `labels`, `note`,
- `modified_by_user`, `last_modified_date`.

## `contracts`

Důležitá pole:

- `filename`, `name`, `cislo_smlouvy`, `title`,
- `datum_uzavreni`, `datum_platnosti`,
- `nazev_poskytovatele`, `ico`, `kontaktni_osoby`,
- `digitalni_knihovny`, `license`,
- `hlavni`, `hlavni_smlouva`, `dodatky`,
- `ccnb`, `issn`,
- `souhrn`, `full_text`.

Updater používá především `filename`, `ccnb`, `issn` a `license`.

## `configuration`

Core je vlastněný webovou vrstvou a obsahuje dynamické seznamy a mapování. Jeho kontrakt je popsán v
[technické dokumentaci webové a API vrstvy](../architecture/web-a-api.md).

## Typické dotazy

```text
state:TO_CONTROL
state:FOR_REVIEW
state:PARENT_OPEN
assigned_licenses:public
assigned_licenses:onsite
digital_library:mzk
labels:excel
labels:excel_incorrect_license
is_official:true
release_year:[* TO 2026]
state_date:[2026-07-01T00:00:00Z TO 2026-08-01T00:00:00Z]
```

## Atomické aktualizace a commity

Backend i web používají atomické operace `set`, `add`, `add-distinct` a `remove`. Harvester a updater po dávkách provádějí explicitní commit. Při přímém zásahu do Solru zachovejte povinný identifikátor a očekávaný typ pole; jinak může atomická aktualizace selhat nebo změnit tvar vícehodnotového pole.
