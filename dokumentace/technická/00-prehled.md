# Přehled kurátorské aplikace

Tato dokumentace popisuje celý společně nasazovaný systém, včetně webového modulu `kuratorska-aplikace-web`. Web je vedený jako Git submodule jen kvůli oddělenému vývoji; provozně je součástí Kurátorské aplikace.

## Účel systému

Kurátorská aplikace shromažďuje bibliografická metadata z více instancí Krameria do jednoho centrálního Solru. Nad společnými daty vede autorskoprávní workflow, umožňuje kurátorské zásahy a poskytuje změny zpět jednotlivým instancím Krameria.

Základní integrační směr je:

```text
Kramerius A ─┐
Kramerius B ─┼─> harvester ─> Solr <─> webové UI / kurátorská práce
Kramerius N ─┘                     │
                                  └─> GET /api/changes ─> příslušný Kramerius
```

Harvester tedy data z Krameria čte. Po automatickém nebo ručním rozhodnutí si Kramerius výsledek vyžádá z Kurátorské aplikace. Kurátorská aplikace změny aktivně neposílá.

## Části systému

- `kuratorska-aplikace-web` je webové UI a servletová API vrstva. Její uživatelská a technická dokumentace zůstává v submodulu.
- `services:harvester` sklízí metadata z API Krameria v5/v7, načítá MODS, počítá počáteční workflow a zapisuje tituly a autory.
- `services:updater` znovu vyhodnocuje workflow, dopočítává rok uvolnění, označuje chybějící data a aplikuje licence ze smluv.
- `services:scheduler` spouští harvester nebo updater jako dlouho běžící Quartz službu.
- `single-use-services:excel-importer` importuje kurátorské tabulky TSV a obsahuje import posledních autorů z Excelu.
- `single-use-services:last-author-updater` páruje údaje o posledním autorovi z Excelu s autory a tituly v Solru.
- `services:common` obsahuje sdílené datové modely, normalizaci, parsování a Solr helpery.
- `nkp-deploy` skládá celý systém pomocí Docker Compose a obsahuje provozní konfiguraci i Solr cores.

## Hlavní datový tok

1. `nkp-deploy` spustí Solr, web, samostatný harvester a samostatný updater.
2. Harvester projde všechny položky `instances` v `.kapp/app.conf`.
3. Z každé instance načte změněné dokumenty, MODS, licence a zdrojovou knihovnu.
4. Metadata normalizuje a uloží do centrálních kolekcí `titles` a `authors`.
5. Workflow určí počáteční stav, přiřazené licence a rok uvolnění.
6. Kurátoři upravují společná data ve webovém UI. Oprávnění k titulům se odvozují z rolí knihoven a sigel.
7. Updater periodicky přepočítá kandidáty, doplní licence plynoucí ze smluv a uloží změny.
8. Změna výsledné licence nastaví `state_date`. Kramerius si licenční změny za časový interval vyžádá přes `GET /api/changes`.
9. Kramerius z odpovědi převezme licence pro daný PID a uloží si poslední úspěšně zpracovaný čas.

## Sdílená data

Solr v nasazení obsahuje zejména cores:

- `titles` - tituly, zdrojová metadata, kurátorská data, workflow a licence,
- `authors` - autoři vytěžení z MODS a upravení v KAPP,
- `aut` - autoritní index používaný webem,
- `contracts` - metadata smluv a jejich licence,
- `configuration` - dynamická konfigurace webu, štítky a vazby knihovna-sigla.

PDF smluv a provozní konfigurace jsou ve volume `.kapp`.

## Dokumentace

- [01-konfigurace.md](./01-konfigurace.md) - konfigurace backendu a vazba na nasazení.
- [02-import-z-krameria.md](./02-import-z-krameria.md) - sklizeň z více Krameriů.
- [03-kuratorske-stavy.md](./03-kuratorske-stavy.md) - workflow, licence a rok uvolnění.
- [04-import-tsv.md](./04-import-tsv.md) - tabulkové importy a poslední autor.
- [05-planovac-a-updater.md](./05-planovac-a-updater.md) - plánované služby a updater.
- [06-solr-data.md](./06-solr-data.md) - Solr cores a důležitá pole.
- [07-provoz-a-reseni-problemu.md](./07-provoz-a-reseni-problemu.md) - deployment a diagnostika.
- [08-otevrene-otazky.md](./08-otevrene-otazky.md) - integrační rozhodnutí, která musí být dohodnuta.
- [Webová uživatelská dokumentace](../../kuratorska-aplikace-web/UZIVATELSKA_DOKUMENTACE.md) - práce ve webovém UI.
- [Webová technická dokumentace](../../kuratorska-aplikace-web/TECHNICKA_DOKUMENTACE.md) - servletová vrstva, oprávnění a pull API pro Kramerius.
