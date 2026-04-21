# Otevřené otázky k doplnění

Tento seznam obsahuje věci, které nejde spolehlivě uzavřít jen z aktuálního README a dostupného backendového kódu. Po doplnění odpovědí je vhodné dokumentaci zpřesnit.

## Webové UI

1. Jaké obrazovky má webová aplikace a jak se jmenují v uživatelském rozhraní?
2. Jak operátor vyhledává dokumenty k posouzení?
3. Jaké filtry jsou ve webu dostupné?
4. Jak přesně se v UI provádí změna stavu?
5. Má UI zvláštní pohled pro rodiče `PARENT_OPEN` a jejich děti?
6. Jak UI zobrazuje `state_history`, poznámky, štítky a licence?
7. Existují uživatelské role, například administrátor, kurátor, čtenář nebo operátor?

## Workflow a business pravidla

1. Mají se ve všech uživatelských materiálech používat enum názvy jako `TO_CONTROL`, nebo české názvy z README?
2. Jaký je finální význam stavů `CONTROLLED_UNKNOWN_PUBLIC`, `CONTROLLED_WO_RIGHTS` a `CONTROLLED_BLOCKED`?
3. Má stav `CONTROLLED_WO_RIGHTS` vždy znamenat veřejné zpřístupnění?
4. Jak přesně se má zacházet s díly bez známého autora?
5. Má rozhodovat rok vydání, rok úmrtí autora, nebo kombinace s prioritou autora?
6. Jaké je očekávané chování pro periodika a svazky periodik?
7. Kdy se má dokument přesunout do `FOR_REVIEW` místo rovnou do veřejného nebo neveřejného stavu?

## Konfigurace a provoz

1. Má být v `touchStoreConfig` klíč `baseDir`, nebo `folder`?
2. Jaké jsou produkční hodnoty `solr.url`, `publicYear` a `onsiteYear`?
3. Jaké instance Krameria mají být v produkci zapnuté?
4. Jaké mají být správné hodnoty `physicalLocations`?
5. Má se používat `file` touch store, nebo `solr` touch store?
6. Má scheduler v produkci spouštět i harvest, nebo pouze updater?
7. Jak často se má spouštět harvest?
8. Jak často se má spouštět updater?

## TSV/Excel import

1. Jaké jsou finální názvy a struktury tabulek, které budou operátoři importovat?
2. Má být datum v mapování `date_issued_str`, nebo `date.str`?
3. Má `public_since` něco měnit ve workflow, nebo je jen informativní?
4. Co má znamenat prázdná hodnota ve sloupci `Zveřejnit`?
5. Jaké hodnoty kromě `ano`, `ne` a `?` se v tabulkách reálně vyskytují?
6. Má importér zakládat nové dokumenty, nebo jen aktualizovat ty, které už existují z harvestu?
7. Má importér po řádkové chybě pokračovat, nebo celý běh zastavit?

## Solr a data

1. Jaké je finální Solr schema pro kolekce `titles` a `authors`?
2. Jsou pole typu `physical_locations` a `physical_locations.facet` záměrně odlišná?
3. Má být stav uložen jako enum název (`TO_CONTROL`), nebo jako textová hodnota (`to_control`)?
4. Jak se má řešit historie stavů při opakovaném importu stejných TSV souborů?
5. Má se `state_user` při TSV importu ukládat jako jeden uživatel, nebo seznam kontrolorů?

## Dokumentace k doplnění po odpovědích

Po doplnění informací doporučuji upravit hlavně:

- [03-kuratorske-stavy.md](./03-kuratorske-stavy.md) o finální české názvy a business výklad stavů.
- [04-import-tsv.md](./04-import-tsv.md) o přesný formát tabulek a význam sloupců.
- [05-planovac-a-updater.md](./05-planovac-a-updater.md) o reálné provozní intervaly.
- [07-provoz-a-reseni-problemu.md](./07-provoz-a-reseni-problemu.md) o konkrétní provozní postupy a kontakty.
