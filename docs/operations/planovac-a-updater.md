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

## Cílený přepočet z webové aplikace a harvesteru

Proces updateru poslouchá na interním portu `8081`:

```http
POST /internal/workflow/recalculate
Content-Type: application/json
Authorization: Bearer <workflowUpdate.token>
```

```json
{
  "pids": ["uuid:..."],
  "requestedBy": "uzivatel",
  "cause": "last_year_death"
}
```

Požadavky z webové aplikace, harvesteru a úplný plánovaný běh používají jednu
jednovláknovou frontu. Výpočty workflow proto neběží souběžně. Cílený přepočet
načte aktuální dokument a při zápisu používá Solr `_version_`; konflikt způsobí
nové načtení a nejvýše tři pokusy. Změna stavu potom vyvolá také přepočet
kořenového titulu.

Endpoint vrací HTTP 200 s výsledkem jednotlivých PIDů. Pokud požadavek čeká ve
frontě déle než `workflowUpdate.responseTimeoutSeconds`, vrátí HTTP 202 a
zpracování pokračuje. Fronta není perzistentní a volání z webové aplikace je
best-effort; úplný běh updateru zůstává záložním mechanismem.

Webová aplikace při těchto změnách zapisuje pouze vstupní údaje, například
`last_year_death`, `date_issued_year_kapp` a příznaky autorství. Odvozené pole
`release_year` počítá a zapisuje výhradně updater. Ruční kurátorská změna stavu
zůstává samostatnou explicitní operací webové aplikace.

Harvester po zapsání a commitnutí sklizených titulů odešle jejich PIDy na stejný
endpoint v dávkách po `workflowUpdate.maxBatchSize`. U existujících titulů už
workflow nevyhodnocuje nad neúplným atomickým dokumentem; cílený updater jej
vyhodnotí až nad sloučeným dokumentem načteným ze Solru. Nové tituly dostanou
počáteční stav během importu a updater následně doplní odvozená pole a případné
navazující změny. Odeslání je best-effort a jeho selhání nezruší sklizeň.

## Kandidátní filtr

Kód obsahuje builder kandidátního filtru podle roků a příznaků. V aktuálním běhu však není filtr připojen k Solr dotazu; updater záměrně prochází všechny tituly. Při odhadu času a zatížení proto vycházejte z celé velikosti core `titles`.

## Last-author updater

`services:last-author-updater` je samostatná jednorázová aplikace, nikoli Quartz job. Páruje hodnoty `last_author_excel` s core `authors`, zapisuje upravená autoritní data k titulům a může připravit podklady pro další workflow přepočet.

Spuštění a volby jsou popsané v části [Tabulkové importy a poslední autor](../guides/import-tsv.md).

## Ruční spuštění

Pro lokální Gradle běh:

```powershell
.\gradlew :services:scheduler:run --args="harvester"
.\gradlew :services:scheduler:run --args="updater"
.\gradlew :services:last-author-updater:run --args="<volby>"
```

V produkci preferujte příslušnou Compose službu, protože používá správné volume a síťové názvy.
