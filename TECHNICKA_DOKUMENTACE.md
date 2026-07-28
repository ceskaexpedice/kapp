# Webová a API vrstva - technická dokumentace

Tento dokument popisuje technické části vlastněné submodulem `kuratorska-aplikace-web`: servletovou vrstvu, Angular UI, autentizaci a HTTP rozhraní pro Kramerius. Harvester, workflow, updater a backendová konfigurace jsou popsány v `../dokumentace/technická`.

## Postavení v systému

Webový modul je samostatně vyvíjená, ale společně nasazovaná část Kurátorské aplikace. `nkp-deploy` spouští web v kontejneru `tomcat` a připojuje stejné `.kapp` volume a stejný Solr jako backendovým službám.

Web pracuje zejména s cores:

- `titles`,
- `authors`,
- `aut`,
- `contracts`,
- `configuration`.

## Konfigurace

Serverová část načítá URL Solru a další nastavení z konfigurace dostupné přes `Options`. Angular klient používá `src/assets/config.json` pro Keycloak, facety, sloupce, stavy a externí odkazy.

Dynamická konfigurace v core `configuration` obsahuje mimo jiné:

- seznamy štítků a licencí,
- viditelnost štítků,
- seznam digitálních knihoven,
- dokumenty `config_type:library_physical_locations`.

U mapování oprávnění odpovídá `library` názvu Keycloak role a `physical_locations` obsahuje sigly povolené této roli.

## Autentizace a autorizace

Přihlášení zajišťuje Keycloak. Veřejné prohlížení titulů a autorů nevyžaduje přihlášení, zapisující operace ano.

Pro editaci titulů platí:

- `kappAdmin` může upravit libovolný titul,
- běžný uživatel musí mít roli knihovny,
- alespoň jedna `physical_locations` titulu musí být v siglách mapovaných na tuto roli,
- u hromadné operace musí být oprávnění splněné pro všechny požadované PID.

Správa mapování knihoven na sigly je dostupná pouze roli `kappAdmin`. Uživatel vidí své role a odvozené sigly na stránce **Oprávnění**.

## Pull API pro Kramerius

Web poskytuje veřejný read-only endpoint:

```http
GET <web-context>/state-changes?startDate=<unix-seconds>&endDate=<unix-seconds>
```

`<web-context>` je kontext nasazeného WAR, v Compose typicky `/kuappweb`.

### Parametry

| Parametr | Povinný | Význam |
| --- | --- | --- |
| `startDate` | ano | Začátek intervalu jako Unix timestamp v sekundách (UTC). |
| `endDate` | ne | Konec intervalu jako Unix timestamp v sekundách (UTC). Při vynechání se použije aktuální čas serveru. |

Konec nesmí být před začátkem. Obě hranice jsou inkluzivní:

```text
state_date:[<start> TO <end>]
```

### Odpověď

```json
[
  {
    "pid": "uuid:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "title": "Název díla",
    "assigned_licenses": ["public"],
    "licenses": ["onsite"],
    "stateDate": "2026-06-30T10:15:20Z",
    "stateUser": "kurator"
  }
]
```

Pole je vždy přítomné; pokud chybí ve zdrojovém dokumentu, má hodnotu `null`.

Kramerius používá z odpovědi licenční údaje. `pid` slouží k identifikaci titulu; název, datum a uživatel jsou informativní a diagnostické. Filtrování podle `state_date` je záměrný mechanismus pro výpis změn licencí. Aktuální odpověď neobsahuje `state` ani `digital_library`.

Chyba vstupu vrací `400`:

```json
{"error":"startDate is required"}
```

Chyba při čtení Solru vrací `500`:

```json
{"error":"Unable to retrieve state changes"}
```

Odpovědi mají typ `application/json;charset=UTF-8` a zakázanou cache.

### Stránkování

Servlet čte `titles` přes Solr `cursorMark`, řadí podle `pid asc` a interně načítá stránky po 1000. Klientovi vrací jeden kompletní JSON array; HTTP stránkování není implementované.

### Doporučený odběr

Každá instance Krameria má vlastní checkpoint:

1. Načte poslední úspěšný konec intervalu.
2. Před dotazem zvolí nový pevný `endDate`.
3. Vyžádá inkluzivní interval.
4. Vybere záznamy relevantní pro svou instanci.
5. Idempotentně aplikuje licence podle PID.
6. Checkpoint posune až po úspěšném zpracování celé odpovědi.

Kvůli inkluzivním hranicím se záznam na hranici může vrátit dvakrát. Odběr musí deduplikovat alespoň podle `pid` a `stateDate`.

## Více instancí Krameria

Kurátorská aplikace má jeden společný Solr pro více Krameriů. Endpoint aktuálně serverově nefiltruje podle instance. Dokud nebude doplněn zdrojový filtr nebo `digital_library` v odpovědi, musí každý odběratel ignorovat cizí PID nebo použít jiné dohodnuté rozlišení.

## Známá omezení API

- endpoint nevyžaduje autentizaci,
- nefiltruje podle knihovny,
- nemá klientské stránkování,
- význam a použití polí `licenses_<acronym>` pro jednotlivé instance Krameria ještě není uzavřený,
- `../openapi/openapi.yaml` popisuje odlišný starší návrh `/api/changes` a musí být před publikací sjednocen s implementací.
