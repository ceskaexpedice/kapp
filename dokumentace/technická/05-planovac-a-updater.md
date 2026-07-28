# Plánovač a updater

Modul `services:scheduler` používá Quartz. Stejný image se v `nkp-deploy` spouští dvakrát s jiným argumentem.

## Režimy scheduleru

`cz.inovatika.SchedulerMain` vyžaduje právě jeden argument:

- `harvester` - zaregistruje jen harvest job,
- `updater` - zaregistruje jen updater job,
- `all` - zaregistruje oba joby v jednom procesu.

Bez argumentu nebo s jinou hodnotou proces skončí chybou. Compose používá oddělené služby:

```yaml
harvester:
  command: ["harvester"]

updater:
  command: ["updater"]
```

To umožňuje obě služby restartovat a sledovat nezávisle.

## Časování

Oba triggery používají `startNow()`. Harvester má interval 24 hodin, updater `365 * 24` hodin. Praktický důsledek je:

- po startu/restartu kontejneru se úloha spustí ihned,
- harvester poté běží přibližně denně,
- updater poté přibližně ročně.

Interval je v současnosti zapsaný v kódu, není v `app.conf`.

## Harvester job

`cz.inovatika.HarvesterJob` načte konfiguraci a spustí inkrementální sklizeň. Chybějící timestamp způsobí plnou sklizeň dané instance. Dřívější ruční metody `fullHarvest()` a `incrementalHarvest()` nejsou aktivní veřejné CLI.

## Updater job

`cz.inovatika.UpdaterJob` načte konfiguraci a spustí `UpdaterImpl`.

Aktuální updater:

1. Projde smlouvy v core `contracts`.
2. Podle ČČNB/ISSN najde odpovídající tituly.
3. Licence s prefixem knihovny doplní do `assigned_licenses`, pokud daný titul skutečně náleží odpovídající knihovně.
4. Cursor pagingem projde všechny dokumenty v `titles` po 100 vstupech.
5. Zvolí kurátorsky opravený nebo sklizený rok vydání.
6. Znovu vyhodnotí workflow včetně posledního autora a zvláštních typů autorství.
7. Doplní chybějící štítek pro titul bez `root_title`.
8. Aktualizuje `release_year`, i když se stav nemění.
9. Změny zapisuje po dávkách 200 a explicitně commituje `titles`.

Změny workflow se zapisují s uživatelem `Updater`.

## Kandidátní filtr

Kód obsahuje builder kandidátního filtru podle roků a příznaků. V aktuálním běhu však není filtr připojen k Solr dotazu; updater záměrně prochází všechny tituly. Při odhadu času a zatížení proto vycházejte z celé velikosti core `titles`.

## Last-author updater

`services:last-author-updater` je samostatná jednorázová aplikace, nikoli Quartz job. Páruje hodnoty `last_author_excel` s core `authors`, zapisuje upravená autoritní data k titulům a může připravit podklady pro další workflow přepočet.

Spuštění a volby jsou popsané v [04-import-tsv.md](./04-import-tsv.md).

## Ruční spuštění

Pro lokální Gradle běh:

```powershell
.\gradlew :services:scheduler:run --args="harvester"
.\gradlew :services:scheduler:run --args="updater"
.\gradlew :services:last-author-updater:run --args="<volby>"
```

V produkci preferujte příslušnou Compose službu, protože používá správné volume a síťové názvy.
