# Import z Krameria

Import z Krameria zajišťuje modul `services:harvester`. Jeho úkolem je načíst záznamy z nakonfigurovaných instancí, stáhnout MODS metadata, uložit data do Solru a určit počáteční workflow stav.

## Co musí být připravené

Před importem musí být připravené:

- běžící Solr s kolekcemi `titles` a `authors`,
- soubor `<user-home>/.kapp/app.conf`,
- funkční URL zdrojových instancí Krameria,
- zapisovatelný adresář pro touch store, pokud je `touchStoreConfig.factory` nastaveno na `file`.

## Jak se import ovládá

V dostupném kódu je hlavní třída harvesteru:

```text
cz.inovatika.impl.HarvesterImpl
```

Harvester je pouštěn primárně skrze scheduler. (`services:scheduler`)
Jednorázové spuštění je možné přes Gradle úlohu modulu `services:harvester`, případně přes sestavený fat JAR.

```powershell
.\gradlew :services:harvester:run
```

Nebo po sestavení shadow JAR:

```powershell
java -jar services/harvester/build/libs/<harvester-jar>.jar
```

Služba při startu automaticky čte konfiguraci z `<user-home>/.kapp/app.conf`.

## Průběh importu

Pro každou instanci v `instances` harvester provede:

1. Nastaví cestu k timestampu pro danou instanci.
2. Načte poslední timestamp, pokud existuje.
3. Sestaví cursor dotaz do Krameria.
4. Stránkuje výsledky přes `cursorMark`.
5. Z každého výsledku vytvoří interní položku s PID, root PID, modelem, názvem a licencemi.
6. U monografií dohledá, jestli mají podřízené `monographunit`; periodika označuje jako hierarchická.
7. Pro každý záznam stáhne MODS XML.
8. Spočítá MD5 MODS XML.
9. Porovná, zda už dokument existuje a zda se MODS změnilo.
10. Vytěží metadata a zapíše je do Solru.
11. Vypočítá nebo aktualizuje workflow stav.
12. Po dávkách odesílá změny do kolekce `titles` a autority do kolekce `authors`.
13. Na konci aktualizuje stav rodičů hierarchických dokumentů.
14. Uloží nový timestamp.

## Podporované verze Krameria

### API v5

Používá se pro starší Kramerius. Importní dotaz pracuje s poli:

- `PID`
- `pid_path`
- `root_pid`
- `root_title`
- `fedora.model`
- `dc.title`
- `dnnt-labels`
- `dostupnost`

MODS se stahuje z endpointu:

```text
<url>/item/<pid>/streams/BIBLIO_MODS
```

Inkrementální filtr používá:

```text
modified_date:[<lastUpdated> TO *]
```

### API v7

Používá se pro novější Kramerius. Importní dotaz pracuje s poli:

- `pid`
- `own_pid_path`
- `root.pid`
- `root.title`
- `model`
- `titles.search`
- `licenses_of_ancestors`
- `licenses`

MODS se stahuje z endpointu:

```text
<url>/items/<pid>/metadata/mods
```

Inkrementální filtr používá:

```text
indexed:[<lastUpdated> TO *]
```

## Jak se pozná nový a existující dokument

Harvester používá `TouchStore`.

Při `factory: "file"` se existence pozná podle souboru `mods_md5.txt` v lokálním touch store. Pokud dokument neexistuje, vytvoří se celý Solr dokument. Pokud existuje, harvester připraví atomickou aktualizaci.

Při `factory: "solr"` se existence kontroluje proti dokumentu v kolekci `titles`.

## Kdy se existující dokument aktualizuje

U existujícího dokumentu se kontroluje:

- zda dokument patří k povoleným `physicalLocations`,
- zda se změnilo MODS XML podle MD5,
- zda se změnou MODS nemá změnit automatický stav.

Pokud je seznam fyzických lokací z předchozího zpracování prázdný, harvester se pokusí najít lokace přes root PID.

## Co se ukládá z MODS

Z MODS se vytěžuje zejména:

- bibliografické MODS XML,
- MD5 MODS XML,
- datum vydání,
- autoři,
- identifikátory,
- fyzické lokace,
- poslední rok úmrtí autora, pokud jej autoritní parsování umí odvodit,
- normalizované identifikátory autorů.

## Dávkování

Harvester odesílá:

- tituly po dávkách `200`,
- autority po dávkách `250`, v části kódu je ale pro flush použit společný limit `200`.

## Očekávaný výsledek importu

Po úspěšném importu by mělo platit:

- nové nebo změněné tituly jsou v kolekci `titles`,
- autoři jsou v kolekci `authors`,
- každý zpracovaný titul má `state`,
- workflow změna má záznam ve `state_history`,
- aplikací přiřazená licence je v `assigned_licenses`,
- timestamp instance je aktualizovaný.