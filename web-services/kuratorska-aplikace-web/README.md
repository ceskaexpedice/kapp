# Kurátorská aplikace web

Webová a HTTP API část Kurátorské aplikace. Modul je vyvíjený v samostatném repozitáři a připojený jako Git submodule backendového repozitáře, ale provozně jde o jednu aplikaci.

## Dokumentace

- [Uživatelská dokumentace](./UZIVATELSKA_DOKUMENTACE.md)
- [Technická dokumentace webu a API](./TECHNICKA_DOKUMENTACE.md)
- [Backendová technická dokumentace](../dokumentace/technická/00-prehled.md)

## Nasazení

Web se nenasazuje samostatně. Repozitář `nkp-deploy` spouští společně:

- webovou aplikaci v Tomcatu,
- Solr,
- harvester,
- updater.

Web i backend používají společnou konfiguraci/úložiště `.kapp` a společné Solr cores.
