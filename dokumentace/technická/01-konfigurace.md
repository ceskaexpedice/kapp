# Konfigurace aplikace

Společné nasazení vychází z repozitáře `nkp-deploy`. Jeho adresář `.kapp` se připojuje do backendových a webových kontejnerů jako `/root/.kapp`.

## Backendová konfigurace

Java služby čtou:

```text
<user-home>/.kapp/app.conf
```

V Docker Compose jde o `/root/.kapp/app.conf`, na hostiteli o `nkp-deploy/.kapp/app.conf`.

```json
{
  "workflow": {
    "publicYear": 110,
    "onsiteYear": 50,
    "authorDeadEnoughYear": 70
  },
  "solr": {
    "url": "http://solr-kappScheduler:8983/solr"
  },
  "workflowUpdate": {
    "url": "http://updater:8081",
    "port": 8081,
    "token": "",
    "maxBatchSize": 100,
    "responseTimeoutSeconds": 30
  },
  "instances": [
    {
      "acronym": "nkp",
      "url": "https://api.kramerius7.nkp.cz/search/api/client/v7.0",
      "apiVersion": "v7",
      "solrCloud": true,
      "physicalLocations": ["BOA001"],
      "ROWS": 100
    }
  ],
  "touchStoreConfig": {
    "baseDir": "/touchstore",
    "factory": "solr"
  }
}
```

### `workflow`

- `publicYear` je počet let od vydání, po kterém lze titul bez přesnějšího autorského údaje automaticky považovat za veřejný.
- `onsiteYear` vymezuje období od vydání, ve kterém titul zůstává automaticky on-site.
- `authorDeadEnoughYear` je počet let od úmrtí posledního autora od kdy je dílo považované za veřejné.

Hranice se počítají proti aktuálnímu kalendářnímu roku. Změna hodnot ovlivní následující běh updateru nad existujícími daty.

### `solr`

`solr.url` je základní URL bez názvu core. Scheduler, harvester a updater k ní připojují názvy `titles`, `authors` a `contracts`.

### `workflowUpdate`

- `url` používá webová aplikace pro interní požadavky na cílený přepočet workflow,
- `port` je interní HTTP port služby updateru,
- `token` je volitelný sdílený Bearer token; prázdná hodnota vypne kontrolu tokenu,
- `maxBatchSize` omezuje počet PIDů v jednom požadavku,
- `responseTimeoutSeconds` určuje, jak dlouho endpoint čeká na zpracování fronty, než vrátí HTTP 202.

Volání je best-effort. Neúspěšný požadavek nezruší změnu provedenou ve webové aplikaci; titul později zpracuje úplný běh updateru.

### `instances`

Každá položka představuje jednu zdrojovou instanci Krameria:

- `acronym` je stabilní identifikátor zdroje, například `nkp` nebo `mzk`; zapisuje se do `digital_library` a používá se v polích `licenses_<acronym>`,
- `url` je základní klientské API,
- `apiVersion` podporuje `v5` nebo `v7` a označuje verzi Krameria,
- `solrCloud` určuje stránkovací identifikátor API v7 (`compositeId` místo `pid`),
- `physicalLocations` jsou sigly relevantní pro zdroj,
- `ROWS` je velikost stránky, výchozí hodnota je `100`.

Změna `acronym` vytvoří novou větev timestampů a z pohledu harvesteru nový zdroj.

### `touchStoreConfig`

- `factory: "solr"` zjišťuje existenci dokumentů přímo v `titles`; je to konfigurace použitá v `nkp-deploy`,
- `factory: "file"` používá `baseDir` pro soubory PID, root PID, MODS, MD5, zdroje a sigel.

Souborový režim může u velkého objemu vyčerpat inody. `baseDir` musí být v kontejneru trvale připojený.

## Timestampy harvestu

Každá instance má čas posledního úspěšného běhu v:

```text
<user-home>/.kapp/timestamps/<acronym>/timestamp
```

API v5 filtruje podle `modified_date`, API v7 podle `indexed`. Chybějící timestamp znamená plnou sklizeň dané instance.

Konfigurace webu, Keycloaku a webových oprávnění je popsána v
[technické dokumentaci submodulu](../../kuratorska-aplikace-web/TECHNICKA_DOKUMENTACE.md).

## Smlouvy

PDF smluv jsou v adresáři připojeném přes `.kapp/contracts`. Metadata jsou v core `contracts`. Licence smlouvy určená pro konkrétní Kramerius musí mít prefix knihovny a podtržítko:

```text
nkp_<licence>
mzk_<licence>
```

Updater prefix použije k výběru pole `licenses_<acronym>` a přidá chybějící licenci titulům odpovídajícím podle ČČNB nebo ISSN.

## Tabulkové importy

TSV importer očekává:

```text
<user-home>/.kapp/tsv-mappings.json
<user-home>/.kapp/Data
```

Samostatný last-author updater přijímá vstupní TSV/Excel export a vlastní CLI parametry; viz [04-import-tsv.md](./04-import-tsv.md).

## Citlivé hodnoty

Do Git repozitáře nepatří reálné přístupové tokeny, Keycloak secrets ani produkční klíče pro analýzu smluv. Soubor `.kapp/config.json` musí mít produkční API klíč dodaný provozním secret mechanismem.
