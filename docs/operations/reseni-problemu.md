# Provoz a řešení problémů

## Společný deployment

Produkční jednotkou je repozitář `nkp-deploy`. Aktuální `docker-compose.yml` spouští:

- `solr` na hostitelském portu `8983`,
- `tomcat` s webem/API na portu `8088`,
- `harvester` z image `kappscheduler:latest` s argumentem `harvester`,
- `updater` ze stejného image s argumentem `updater`.

Oba backendové kontejnery čekají na healthcheck Solru. Všechny aplikační kontejnery sdílejí `./.kapp:/root/.kapp`.

## První spuštění

V kořeni `nkp-deploy`:

```bash
sudo chown -R 8983:8983 solr/
docker compose pull
docker compose up -d
docker compose ps
```

Přihlášení do registru je nutné podle nastavení GitLab registry. Hostitelský Tomcat není potřeba; web běží v kontejneru služby `tomcat`.

## Před spuštěním

Ověřte:

- Docker a Compose,
- čitelnost `.kapp/app.conf` a webové konfigurace,
- práva uživatele 8983 k `solr/`,
- cores `titles`, `authors`, `aut`, `contracts`, `configuration`,
- dostupnost všech URL v `instances`,
- perzistenci `.kapp/contracts` a případného file touch store,
- Keycloak URL, realm/client a role,
- že produkční secrets nejsou nahrazené ukázkovými hodnotami.

## Aktualizace

```bash
git pull
docker compose pull
docker compose up -d
docker compose ps
```

Změny Solr schémat kontrolujte před restartem. `nkp-deploy` obsahuje i data cores; neprovádějte slepé nahrazení produkčních indexových adresářů obsahem pracovního repozitáře.

## Logy

```bash
docker compose logs -f harvester
docker compose logs -f updater
docker compose logs -f tomcat
docker compose logs -f solr
```

Harvest job používá `startNow()`, takže restart `harvester` okamžitě spustí
sklizeň a po ní úplný updater. Restart samostatné služby `updater` pouze obnoví
interní API pro cílené přepočty.

## Kontrola harvestu

- změnil se `.kapp/timestamps/<acronym>/timestamp`,
- v `titles` přibyly/změnily se dokumenty a MODS MD5,
- v `authors` přibyly autority,
- nové tituly mají `state`, `state_history` a `assigned_licenses`,
- log obsahuje commity `titles` a `authors`.

Chybějící timestamp způsobí full harvest. Nesprávné `apiVersion`, `url` nebo `solrCloud` může způsobit prázdný či opakující se cursor.

## Kontrola updateru

- log nejdřív zpracuje smlouvy a poté tituly,
- změny stavu mají `state_user:Updater`,
- `release_year` odpovídá opravenému nebo sklizenému roku,
- licence ze smluv se přidaly pouze titulům se shodným ČČNB/ISSN a správnou knihovnou,
- po běhu proběhl commit `titles`.

Updater aktuálně prochází celý core. U velkého indexu sledujte dobu běhu a zatížení Solru.

Provozní kontrola webového API a diagnostika oprávnění je v
[technické dokumentaci webu](https://gitlab.com/inovatika/nkp/nkp-kuratorska-aplikace/-/blob/main/kuratorska-aplikace-web/TECHNICKA_DOKUMENTACE.md).

## Smlouvy

Když updater nepřidá licenci:

- smlouva musí mít ČČNB nebo ISSN,
- licence musí obsahovat prefix a `_`, například `mzk_dnnt`,
- titul musí mít shodný identifikátor,
- odpovídající `licenses_<acronym>` musí potvrdit danou knihovnu,
- zkontrolujte log „Skipping contract license“.

## File touch store

Při `factory: "file"` zkontrolujte trvalý a zapisovatelný `baseDir`. Pro PID se ukládají `pid.txt`, `root_pid.txt`, `mods.xml`, `mods_md5.txt`, `source.txt` a `physicalLocations.txt`. Při problémech s inody použijte `factory: "solr"`.

## Bezpečný hromadný zásah

1. Pořiďte Solr backup/snapshot.
2. Ověřte konfiguraci a počty kandidátů.
3. Vyzkoušejte reprezentativní vzorek.
4. Spusťte příslušnou službu/import.
5. Porovnejte počty stavů a licencí.
6. Zkontrolujte `state_history`, `state_date`, `release_year` a smluvní licence.
7. Teprve poté posuňte checkpoint odebírajícího Krameria.
