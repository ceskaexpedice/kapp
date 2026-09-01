# Webová a API vrstva

Tato stránka popisuje technické části v `web-services/kuratorska-aplikace-web`: servletovou vrstvu, Angular UI, autentizaci a HTTP rozhraní pro Kramerius. Harvester, workflow, updater a ostatní backendové části popisuje [hlavní architektura](index.md).

## Postavení v systému

Webový modul je samostatně vyvíjená, ale společně nasazovaná část Kurátorské aplikace. `nkp-deploy` spouští web v kontejneru `tomcat` a připojuje stejné `.kapp` volume a stejný Solr jako backendovým službám.

Web pracuje zejména s cores:

- `titles`,
- `authors`,
- `aut`,
- `contracts`,
- `configuration`.

## Sestavení a CI

Webová aplikace vyžaduje Javu 21, Maven a Node.js 20. Sestavuje se nezávisle na kořenovém backendovém Maven reactoru:

```powershell
cd web-services/kuratorska-aplikace-web/kuappweb
mvn clean verify
```

Změny pod `web-services/kuratorska-aplikace-web` spouštějí samostatný GitLab child pipeline. Ten ověří webový Maven build a nabízí ruční publikování image `registry.gitlab.com/inovatika/nkp/kuratorska-aplikace-web/kuappweb`. Backendový pipeline webovou aplikaci nesestavuje.

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
GET /api/changes?from=<utc-date-time>&to=<utc-date-time>&licenses=<license>&source=<digital-library>
```

V aktuálním Compose nasazení je endpoint dostupný například jako
`http://localhost:8088/api/changes`.

### Parametry

| Parametr | Povinný | Význam |
| --- | --- | --- |
| `licenses` | ne | Jedna přiřazená licence, `public` nebo `onsite`; filtruje Solr pole `assigned_licenses`. |
| `source` | ne | Zdrojová digitální knihovna; filtruje Solr pole `digital_library`. |
| `from` | ne | Inkluzivní začátek intervalu jako UTC ISO 8601 date-time. Při vynechání nemá interval dolní mez. |
| `to` | ne | Inkluzivní konec intervalu jako UTC ISO 8601 date-time. Při vynechání se použije aktuální čas serveru. |

Konec nesmí být před začátkem. Obě hranice jsou inkluzivní:

Jiný query parametr než `licenses`, `source`, `from` nebo `to` je odmítnut odpovědí `400`.

```text
state_date:[<from> TO <to>]
```

### Odpověď

```json
[
  {
    "pid": "uuid:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "title": "Název díla",
    "assigned_licenses": ["public"],
    "licenses": ["onsite"],
    "source": "mzk",
    "stateDate": "2026-06-30T10:15:20Z",
    "stateUser": "kurator"
  }
]
```

Pole jsou vždy přítomná; pokud chybí ve zdrojovém dokumentu, mají hodnotu `null`.
Výjimkou je `assigned_licenses`: chybějící Solr pole znamená prázdné přiřazení a
API je vrací jako `[]`. To platí zejména pro stavy `FOR_REVIEW` a `TO_CONTROL`.

Kramerius používá z odpovědi licenční údaje. `pid` slouží k identifikaci titulu; název, datum a uživatel jsou informativní a diagnostické. Filtrování podle `state_date` je mechanismus pro výpis změn licencí. Pole `source` je odvozeno z `digital_library`; pokud je zdrojové pole vícehodnotové, API vrací první hodnotu.

Chyba vstupu vrací `400`:

```json
{"code":"invalid_request","message":"Parameter 'from' must be a valid UTC ISO 8601 date-time."}
```

Chyba při čtení Solru vrací `500`:

```json
{"code":"internal_error","message":"Unable to retrieve licence changes."}
```

Odpovědi mají typ `application/json;charset=UTF-8` a zakázanou cache.

### Stránkování

Servlet čte `titles` přes Solr `cursorMark`, řadí podle `pid asc` a interně načítá stránky po 1000. Klientovi vrací jeden kompletní JSON array; HTTP stránkování není implementované.

### Doporučený odběr

Každá instance Krameria má vlastní checkpoint:

1. Načte poslední úspěšný konec intervalu.
2. Před dotazem zvolí nový pevný čas `to`.
3. Vyžádá inkluzivní interval.
4. Vybere záznamy relevantní pro svou instanci.
5. Idempotentně aplikuje licence podle PID.
6. Checkpoint posune až po úspěšném zpracování celé odpovědi.

Kvůli inkluzivním hranicím se záznam na hranici může vrátit dvakrát. Odběr musí deduplikovat alespoň podle `pid` a `stateDate`.

## Více instancí Krameria

Kurátorská aplikace má jeden společný Solr pro více Krameriů. Každý odběratel může použít parametr `source`, který odpovídá hodnotě `digital_library`, a odebírat jen změny vlastní instance.

## Známá omezení API

- endpoint nevyžaduje autentizaci,
- nemá klientské stránkování,
- význam a použití polí `licenses_<acronym>` pro jednotlivé instance Krameria ještě není uzavřený.
