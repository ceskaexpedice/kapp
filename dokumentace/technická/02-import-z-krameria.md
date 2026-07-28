# Import z Krameria

Modul `services:harvester` sklízí metadata z více nakonfigurovaných instancí Krameria do společného Solru. Podporuje API v5 a v7.

## Předpoklady

- běžící Solr s cores `titles` a `authors`,
- platný `<user-home>/.kapp/app.conf`,
- dostupná API všech položek `instances`,
- funkční a perzistentní touch store,
- zapisovatelný adresář timestampů.

V produkci spouští harvester samostatná Compose služba. Lokálně:

```powershell
.\gradlew :services:scheduler:run --args="harvester"
```

## Běh přes instance

Pro každou instanci harvester:

1. nastaví `.kapp/timestamps/<acronym>/timestamp`,
2. načte poslední úspěšný čas, pokud existuje,
3. sestaví dotaz a inkrementální filtr odpovídající verzi API,
4. stránkuje přes cursor a stabilní identifikátor,
5. doplní vazby monografií na `monographunit`,
6. pro každý dokument stáhne MODS,
7. vypočte MD5 a porovná jej s touch store,
8. vytěží rok vydání, autory, identifikátory a sigly,
9. vytvoří nový dokument nebo atomickou aktualizaci,
10. vyhodnotí workflow a zapíše změnu stavu,
11. odešle dávky titulů a autorů,
12. přepočítá hierarchické rodiče,
13. explicitně commituje `titles` a `authors`,
14. uloží nový timestamp.

Timestamp se nesmí posunout po neúspěšném neúplném běhu.

## API v5

Výběrový dotaz omezuje model na:

```text
fedora.model:(monograph OR monographunit)
```

Používá starší pole, například `PID`, `pid_path`, `root_pid`, `root_title`, `fedora.model`, `dc.title`, `dnnt-labels` a `dostupnost`.

MODS:

```text
<url>/item/<pid>/streams/BIBLIO_MODS
```

Inkrementální filtr:

```text
modified_date:[<lastUpdated> TO *]
```

## API v7

Výběrový dotaz zahrnuje:

```text
model:(monograph OR monographunit OR periodical OR periodicalvolume)
```

Používá pole `pid`, `own_pid_path`, `root.pid`, `root.title`, `model`, `titles.search`, `licenses_of_ancestors` a `licenses`.

MODS:

```text
<url>/items/<pid>/metadata/mods
```

Inkrementální filtr:

```text
indexed:[<lastUpdated> TO *]
```

Při `solrCloud:true` se pro cursor používá `compositeId`, jinak `pid`.

## Omezení počtu požadavků

API v7 respektuje hlavičku `Retry-After`. Po jejím obdržení nastaví globální prodlevu mezi dalšími požadavky; prodleva přetrvá během zpracování instance a před další instancí se vynuluje. Chyby při odesílání Solr dávky se opakují až desetkrát.

## Nový a existující titul

Touch store určuje, zda PID už byl zpracován:

- v režimu `solr` dotazem do `titles`,
- v režimu `file` pomocí uloženého MD5 a pomocných souborů.

U existujícího titulu se kontroluje změna MODS a relevantní sigly. Kurátorsky spravovaná pole, zejména `date_issued_year_kapp`, musí zůstat oddělená od znovu sklizených hodnot.

## Normalizace data vydání

Harvester čte text data z MODS, normalizuje známé nepřesné zápisy a parsuje rozsah. Pokud vstup vyžaduje zvláštní zásah nebo jej nelze bezpečně vyložit, přidá diagnostický štítek namísto tichého odhadu.

Výsledkem je zejména:

- `date_issued_str`,
- `date_issued_year`,
- případné diagnostické `labels`.

## Autoři

Autoři z MODS se ukládají do `authors_kramerius` a současně se doplňují do core `authors`. Autoritní identifikátory jsou normalizované. Kurátorské autory v `authors_kapp` sklizeň nemá přepsat.

## Hierarchie

Monografie mohou mít podřízené `monographunit`; API v7 podporuje také periodika a svazky. Root tituly mají `is_hierarchical:true`. Po sklizni se stav rodiče přepočte podle dětí na `PARENT_OPEN` nebo `PARENT_CLOSED`.

## Výsledek úspěšného běhu

- změněné tituly jsou commitnuté v `titles`,
- noví autoři jsou commitnutí v `authors`,
- nové tituly mají workflow stav, historii a přiřazenou licenci,
- zdroj je zachovaný v `digital_library` a licenčních polích instance,
- timestamp instance odpovídá dokončenému běhu.
