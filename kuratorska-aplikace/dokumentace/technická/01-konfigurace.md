# Konfigurace aplikace

Backendové služby čtou konfiguraci ze souboru:

```text
nkp-deploy/.kapp/app.conf
```

## Hlavní konfigurační bloky

Konfigurace se skládá z těchto částí:

- `solr` určuje URL Solru.
- `workflow` nastavuje časové hranice pro automatické rozhodování.
- `touchStoreConfig` určuje, kde se ověřuje existence dříve zpracovaných záznamů a kde se ukládá stav MODS.
- `instances` obsahuje seznam zdrojových instancí Krameria.
- `jobConfigs` je v modelu konfigurace připravené pole, ale v dostupném kódu se aktivně nepoužívá.

## Příklad konfigurace

```json
{
  "solr": {
    "url": "http://localhost:8983/solr"
  },
  "touchStoreConfig": {
    "factory": "file",
    "baseDir": "C:/Users/<user>/.kapp/touchstore"
  },
  "workflow": {
    "publicYear": 110,
    "onsiteYear": 70
  },
  "instances": [
    {
      "acronym": "mzk",
      "url": "https://kramerius.mzk.cz/search/api/v7.0/client",
      "apiVersion": "v7",
      "solrCloud": true,
      "physicalLocations": ["ABA000", "ABA001"],
      "ROWS": 100
    },
    {
      "acronym": "nkp",
      "url": "https://ndk.cz/search/api/v5.0",
      "apiVersion": "v5",
      "solrCloud": false,
      "physicalLocations": ["BOA001"],
      "ROWS": 100
    }
  ]
}
```

## `solr`

`solr.url` je základní URL Solru bez názvu kolekce.

Příklad:

```json
{
  "solr": {
    "url": "http://localhost:8983/solr"
  }
}
```

Aplikace zapisuje do kolekcí:

- `titles`
- `authors`

## `workflow`

`workflow.publicYear` říká, po kolika letech se dílo považuje za veřejně dostupné podle data vydání.

`workflow.onsiteYear` říká, po kolika letech dokument přestává být automaticky on-site od roku úmrtí autora nebo roku vydání.

Při hodnotách:

```json
{
  "publicYear": 110,
  "onsiteYear": 50
}
```

platí:

- dokumenty starší nebo rovné hranici `aktuální rok - 110` mohou automaticky získat veřejnou licenci,
- dokumenty novější nebo rovné hranici `aktuální rok - 50` mohou automaticky spadnout do on-site/neveřejného režimu,
- dokumenty mezi těmito hranicemi typicky vyžadují kontrolu.

Hranice se počítají vždy proti aktuálnímu roku při běhu služby.

## `touchStoreConfig`

`factory` může být:

- `file` používá lokální souborové úložiště - efektivnější, ale náročné na inodes filesystému.
- `solr` používá solr - vhodné na malých serverech, kde nefunguje možnost file.

Při `file` režimu se pro každý PID ukládají pomocné soubory:

- `pid.txt`
- `root_pid.txt`
- `mods.xml`
- `mods_md5.txt`
- `source.txt`
- `physicalLocations.txt`

Adresáře jsou rozdělené podle UUID bez prefixu `uuid:` a bez pomlček.

## `instances`

Každá instance popisuje jeden zdrojový Kramerius:

- `acronym` krátké označení digitální knihovny, zapisuje se do pole `digital_library` a do licenčních polí typu `licenses_<acronym>`.
- `url` základní URL Kramerius API.
- `apiVersion` podporované hodnoty jsou `v5` a `v7`.
- `solrCloud` ovlivňuje identifikátor pro cursor paging u API v7; při `true` se používá `compositeId`, jinak `pid`.
- `physicalLocations` seznam sigel, podle kterých se může rozhodovat, jestli se existující dokument z dané knihovny má aktualizovat.
- `ROWS` počet položek na jeden dotaz při harvestu; pokud není uvedeno, výchozí hodnota je `100`.

## Timestampy inkrementálního importu

Harvester si pro každou instanci ukládá čas posledního dokončeného běhu do:

```text
<user-home>/.kapp/timestamps/<acronym>/timestamp
```

Při dalším běhu se tento čas použije jako inkrementální filtr:

- API v5 používá pole `modified_date`.
- API v7 používá pole `indexed`.

Pokud timestamp neexistuje, harvester provede full harvest.

## Související soubory

Pro TSV import se používá také:

```text
<user-home>/.kapp/tsv-mappings.json
```

Výchozí adresář s TSV soubory je:

```text
<user-home>/.kapp/Data
```
