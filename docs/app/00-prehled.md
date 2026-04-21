# Přehled kurátorské aplikace

Tato dokumentace popisuje aktuální stav projektu podle README a dostupného kódu v repozitáři. Webová část `kuratorska-aplikace-web` je v tomto checkoutu pouze neinicializovaný submodul, proto zde nejsou detailně popsané konkrétní obrazovky webového UI. Popsané ovládání se opírá o dostupné backendové moduly, konfiguraci, importní dávky a stavový model.

## K čemu aplikace slouží

Kurátorská aplikace shromažďuje bibliografická metadata z instancí Krameria, ukládá je do Solru a nad nimi vede workflow dostupnosti dokumentů. Cílem je určit, jestli má být dokument veřejný, neveřejný/on-site, nebo jestli vyžaduje kurátorskou kontrolu.

Aplikace pracuje hlavně s dokumenty typu:

- `monograph`
- `monographunit`
- `periodical`
- `periodicalvolume`

## Základní části systému

Projekt je vícemodulová Java/Gradle aplikace:

- `services:harvester` stahuje dokumenty z Krameria, načítá MODS metadata, vypočítává počáteční workflow stav a zapisuje záznamy do Solru.
- `services:Updater` pravidelně hledá záznamy, kterým se časem změnily podmínky dostupnosti, a posouvá je do dalšího stavu.
- `services:scheduler` spouští naplánované úlohy přes Quartz.
- `services:init` importuje kurátorské výsledky z TSV souborů, typicky exportů z Excelu.
- `services:common` obsahuje sdílené parsování dat, autorů, datumů a Solr helpery.
- `kuratorska-aplikace-web` má být webová část aplikace, ale v tomto checkoutu není dostupný zdrojový kód.

## Hlavní datový tok

1. Operátor připraví konfiguraci v uživatelském adresáři v `.kapp/app.conf`.
2. Harvester projde nakonfigurované instance Krameria.
3. Pro každý nalezený záznam stáhne stručná metadata ze search endpointu a následně MODS XML.
4. Z MODS XML vytěží datum vydání, autory, identifikátory a fyzické lokace.
5. Podle workflow pravidel určí stav dokumentu.
6. Záznam uloží do Solru do kolekce `titles`; autority ukládá do kolekce `authors`.
7. U hierarchických titulů dopočítá stav rodiče podle stavů podřízených jednotek.
8. Updater později kontroluje dokumenty s licencí `onsite`, kterým mohl časem vzniknout nárok na přeposouzení nebo zveřejnění.
9. TSV import doplňuje nebo mění kurátorská rozhodnutí podle tabulek.

## Co aplikace ukládá

Pro tituly se ukládají zejména:

- PID a root PID.
- Model dokumentu.
- Název a root název.
- MODS XML a jeho MD5 hash.
- Datum vydání a odvozený rok.
- Autoři a identifikátory autorů.
- Zdrojová digitální knihovna.
- Licence nalezené v Krameriovi.
- Licence přiřazené aplikací.
- Workflow stav, datum změny, uživatel, důvod změny a historie změn.
- Štítky a poznámky z importovaných tabulek.

## Jak číst další dokumenty

- Pokud nastavujete prostředí, začněte dokumentem [01-konfigurace.md](./01-konfigurace.md).
- Pokud řešíte import z Krameria, pokračujte na [02-import-z-krameria.md](./02-import-z-krameria.md).
- Pokud řešíte kurátorské rozhodování a stavy, použijte [03-kuratorske-stavy.md](./03-kuratorske-stavy.md).
- Pokud importujete výsledky kontrol z Excelu/TSV, použijte [04-import-tsv.md](./04-import-tsv.md).
- Pokud řešíte plánované úlohy, použijte [05-planovac-a-updater.md](./05-planovac-a-updater.md).
- Pokud potřebujete znát data v Solru, použijte [06-solr-data.md](./06-solr-data.md).
- Pokud něco selhává, použijte [07-provoz-a-reseni-problemu.md](./07-provoz-a-reseni-problemu.md).
- Seznam bodů k doplnění je v [08-otevrene-otazky.md](./08-otevrene-otazky.md).
