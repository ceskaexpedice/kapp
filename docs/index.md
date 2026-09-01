# Kurátorská aplikace

Kurátorská aplikace shromažďuje bibliografická metadata z více instancí Krameria do centrálního Solru. Nad společnými daty podporuje autorskoprávní workflow, kurátorské zásahy a předávání změn licencí zpět jednotlivým instancím Krameria.

Dokumentace je určená především kurátorům, administrátorům a vývojářům, kteří aplikaci provozují, konfigurují nebo integrují. Popisuje backendové služby i jejich vazbu na webový modul `kuratorska-aplikace-web`.

## Kde začít

<div class="grid cards" markdown>

-   :fontawesome-solid-sitemap:{ .lg .middle } __Architektura__

    ---

    Komponenty systému, hlavní datový tok a odpovědnosti jednotlivých služeb.

    [:octicons-arrow-right-24: Zobrazit architekturu](architecture/index.md)

-   :fontawesome-solid-sliders:{ .lg .middle } __Konfigurace__

    ---

    Nastavení workflow, Solru, zdrojových instancí Krameria a touch store.

    [:octicons-arrow-right-24: Zobrazit konfiguraci](configuration/index.md)

-   :fontawesome-solid-book:{ .lg .middle } __Návody__

    ---

    Práce ve webové aplikaci, sklizeň z Krameria, kurátorské workflow a řízené tabulkové importy.

    [:octicons-arrow-right-24: Zobrazit návody](guides/index.md)

-   :fontawesome-solid-server:{ .lg .middle } __Provoz__

    ---

    Plánované služby, updater, nasazení a diagnostika běžných problémů.

    [:octicons-arrow-right-24: Zobrazit provozní dokumentaci](operations/index.md)

-   :fontawesome-solid-book-open:{ .lg .middle } __Reference__

    ---

    Datový model Solru a dosud otevřená integrační rozhodnutí.

    [:octicons-arrow-right-24: Zobrazit referenci](reference/index.md)

-   :fontawesome-brands-github:{ .lg .middle } __Dokumentační web__

    ---

    Lokální spuštění MkDocs a automatické publikování přes GitHub Pages.

    [:octicons-arrow-right-24: Zobrazit postup nasazení](deployment/index.md)

</div>

## Základní integrační směr

```text
Kramerius A ─┐
Kramerius B ─┼─> harvester ─> Solr <─> webové UI / kurátorská práce
Kramerius N ─┘                     │
                                  └─> GET /api/changes ─> příslušný Kramerius
```

Harvester data z Krameria čte. Po automatickém nebo ručním rozhodnutí si Kramerius výsledek vyžádá z Kurátorské aplikace; aplikace změny aktivně neposílá.
