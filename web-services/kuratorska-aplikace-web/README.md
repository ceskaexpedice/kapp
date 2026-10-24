# Kurátorská aplikace web

Webová a HTTP API část Kurátorské aplikace. Modul je součástí společného repozitáře v `web-services/kuratorska-aplikace-web` a provozně se nasazuje se zbytkem aplikace.

## Dokumentace

- [Uživatelská příručka](../../docs/guides/webova-aplikace.md)
- [Technická dokumentace webu a API](../../docs/architecture/web-a-api.md)
- [Architektura celé aplikace](../../docs/architecture/index.md)

## Sestavení

Z adresáře `kuappweb` spusťte:

```shell
mvn clean verify
```

Web má vlastní GitHub Actions workflow `.github/workflows/web.yml` a není součástí kořenového backendového Maven reactoru. Build běží pro relevantní push změny a pull requesty; po úspěšném pushi do výchozí větve publikuje image `ghcr.io/ceskaexpedice/kapp/kapp-web-build`.

## Nasazení

Web se nenasazuje samostatně. Repozitář `nkp-deploy` spouští společně:

- webovou aplikaci v Tomcatu,
- Solr,
- harvester,
- updater.

Web i backend používají společnou konfiguraci/úložiště `.kapp` a společné Solr cores.
