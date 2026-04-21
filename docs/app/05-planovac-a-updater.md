# Plánovač a updater

Modul `services:scheduler` používá Quartz a slouží ke spouštění naplánovaných úloh. V aktuálním kódu je aktivně naplánovaný updater; harvestovací úloha je připravená, ale v `main` metodě scheduleru je její registrace zakomentovaná.

## Hlavní třída

Scheduler startuje třídou:

```text
cz.inovatika.SchedulerMain
```

Orientační postup spuštění:

```powershell
.\gradlew :services:scheduler:run
```

Tento postup projekt spouští; zde je uvedený jen jako dokumentace pro operátora.

## Co scheduler aktuálně spouští

Při startu se volá:

```text
initUpdateJob(scheduler)
```

Harvestovací úloha:

```text
initHarvestJob(scheduler)
```

je v `main` metodě zakomentovaná.

## Updater job

Updater job používá třídu:

```text
cz.inovatika.UpdaterJob
```

Při spuštění:

1. Načte `<user-home>/.kapp/app.conf`.
2. Vytvoří `UpdaterImpl`.
3. Spustí aktualizaci nad Solrem.

Interval v aktuálním kódu je:

```text
365 * 24 hodin
```

Trigger zároveň používá `startNow()`, takže po startu scheduleru se úloha spustí hned a pak se opakuje zhruba jednou ročně.

## Co updater kontroluje

Updater prochází kolekci `titles` a hledá dokumenty:

- s `assigned_licenses:onsite`,
- s `date_issued_year` starším než hranice `aktuální rok - publicYear`.

Pro každý nalezený dokument znovu zavolá workflow pravidla nad aktuálním stavem.

Pokud pravidla vrátí nový stav, updater zapíše workflow přechod s uživatelem:

```text
Updater
```

Na konci provede commit kolekce `titles`.

## Harvest job

Harvest job používá třídu:

```text
cz.inovatika.HarvesterJob
```

Podporuje parametr:

```text
runType
```

Hodnoty:

- `INCREMENTAL` spustí harvester.
- `FULL` vyhodí chybu, protože plný harvest přes scheduler není implementovaný.

V aktuální konfiguraci scheduleru se harvest job automaticky neregistruje.

## Ruční trigger metody

`SchedulerMain` obsahuje statické metody:

- `fullHarvest()`
- `incrementalHarvest()`

Tyto metody předpokládají, že scheduler běží a že je zaregistrovaný odpovídající job. Nejde o samostatné CLI příkazy dostupné operátorovi bez další obslužné vrstvy.

## Docker a volumes

V repozitáři je `Dockerfile` pro scheduler a override soubor s volumes:

```yaml
services:
  scheduler:
    volumes:
      - C:/Users/<user>/.kapp:/root/.kapp
  solr:
    volumes:
      - ./../../kuratorska-aplikace-web/solr:/var/solr
  tomcat:
    ports:
      - "8080:8080"
    volumes:
      - C:/Users/<user>/.kapp:/root/.kapp
```

To znamená, že kontejnerové služby očekávají konfiguraci v `/root/.kapp`, mapovanou z lokálního profilu uživatele.

## Provozní doporučení

- Před zapnutím scheduleru si ověřte, jestli má opravdu běžet jen updater, nebo i harvest.
- Pokud má běžet harvest periodicky, je potřeba odkomentovat a nastavit `initHarvestJob`.
- U plného harvestu nepoužívejte `runType=FULL`, dokud nebude implementovaný.
- Po změně `workflow.publicYear` nebo `workflow.onsiteYear` je vhodné ručně promyslet dopad na updater, protože může hromadně změnit stavy.
