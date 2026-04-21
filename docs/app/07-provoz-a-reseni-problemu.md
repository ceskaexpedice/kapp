# Provoz a řešení problémů

Tento dokument shrnuje praktické kontroly pro operátora. Příkazy jsou uvedené jako postup, projekt jsem při tvorbě dokumentace nespouštěl.

## Před spuštěním služby

Zkontrolujte:

- existuje `<user-home>/.kapp/app.conf`,
- `app.conf` obsahuje `solr.url`,
- Solr je dostupný na uvedené URL,
- existují kolekce `titles` a `authors`,
- instance Krameria jsou dostupné,
- pokud používáte `factory: "file"`, existuje a je zapisovatelný adresář `touchStoreConfig.baseDir`,
- pokud importujete TSV, existuje `<user-home>/.kapp/tsv-mappings.json`,
- TSV soubory mají skutečně příponu `.tsv`.

## Jak poznat úspěšný harvest

Po běhu harvesteru ověřte:

- v Solru přibyly nebo se aktualizovaly dokumenty v `titles`,
- dokumenty mají pole `state`,
- dokumenty mají `state_history`,
- dokumenty mají `biblio_mods_md5`,
- u importovaných instancí vznikl nebo se změnil timestamp v `.kapp/timestamps/<acronym>/timestamp`,
- v kolekci `authors` přibyly autoritní záznamy.

## Když se importuje příliš mnoho dat

Pravděpodobné příčiny:

- chybí timestamp instance,
- timestamp byl smazán,
- timestamp je nečitelný,
- změnilo se `acronym`, takže harvester hledá timestamp v jiné složce,
- zdrojové API vrací jako změněné více záznamů, než se čekalo.

Kontrola:

```text
<user-home>/.kapp/timestamps/<acronym>/timestamp
```

## Když se neimportuje nic

Pravděpodobné příčiny:

- timestamp je novější než data ve zdrojové instanci,
- zdrojové API je nedostupné,
- query pro danou verzi API neodpovídá očekávaným polím,
- v konfiguraci je špatná `apiVersion`,
- při API v7 je špatně nastavené `solrCloud`, a tím i identifikátor pro stránkování.

## Když selhává stahování MODS

Ověřte endpoint podle verze API:

API v5:

```text
<url>/item/<pid>/streams/BIBLIO_MODS
```

API v7:

```text
<url>/items/<pid>/metadata/mods
```

Harvester HTTP požadavek opakuje až 10krát. Pokud endpoint vrací jiný stav než `200`, MODS se pro daný PID nezpracuje.

## Když nefunguje file touch store

Zkontrolujte:

- `touchStoreConfig.factory` je `file`,
- `touchStoreConfig.baseDir` není prázdné,
- proces má právo do adresáře zapisovat,
- PID má platný tvar UUID, protože cesta se odvozuje z UUID bez `uuid:` a bez pomlček.

Při správném zápisu by u dokumentu měly existovat soubory:

- `pid.txt`
- `root_pid.txt`
- `mods.xml`
- `mods_md5.txt`
- `source.txt`
- `physicalLocations.txt`

## Když TSV import nezmění stav

Zkontrolujte:

- TSV soubor má odpovídající položku v `tsv-mappings.json`,
- název souboru přesně odpovídá `match`, nebo začíná prefixem z `matchStartsWith`,
- sloupec s UUID je namapovaný na `pid`,
- sloupec se zveřejněním je namapovaný na `public_string`,
- dokument s daným PID už existuje v Solru,
- dokument má pole `model`,
- požadovaný stav je mezi povolenými kurátorskými cíli.

## Když TSV import neparsuje datum

Aktuální kód zpracovává interní klíč:

```text
date_issued_str
```

Pokud mapování používá například:

```text
date.str
```

datum se nemusí zpracovat. Je potřeba sjednotit mapování nebo kód.

## Když rodič zůstává otevřený

Rodič má stav `PARENT_OPEN`, pokud alespoň jedno dítě má `state:TO_CONTROL`.

Postup:

1. Najděte děti podle `root_pid`.
2. Vyfiltrujte děti se `state:TO_CONTROL`.
3. Dokončete jejich kurátorskou kontrolu.
4. Spusťte přepočet rodičů přes harvest nebo odpovídající backendovou úlohu.

## Když updater mění nečekané dokumenty

Updater hledá dokumenty s:

```text
assigned_licenses:onsite
```

a zároveň:

```text
date_issued_year:{ * TO <aktuální rok - publicYear>}
```

Pokud se změnila hodnota `publicYear`, může se rozsah dokumentů výrazně změnit.

## Když scheduler nespouští harvest

V aktuálním kódu scheduler registruje updater, ale registrace harvest jobu je zakomentovaná. To je očekávané chování aktuální verze.

## Logy a chybové stavy

Služby používají hlavně Java logging a částečně `System.out`/`System.err`. Při provozním incidentu je vhodné hledat:

- `Failed to load configuration`
- `Harvesting problem`
- `Failed to get MODS`
- `Failed to parse date range`
- `Failed to send batch`
- `Scheduled task failed`
- `UpdaterJob failed`

## Bezpečný provozní postup při hromadné změně

1. Zálohujte Solr kolekce nebo snapshot dat.
2. Ověřte konfiguraci a hranice workflow.
3. Na menším vzorku ověřte očekávané přechody.
4. Spusťte import nebo updater.
5. Zkontrolujte počty dokumentů podle `state` a `assigned_licenses`.
6. Zkontrolujte `state_history` u několika reprezentativních dokumentů.
