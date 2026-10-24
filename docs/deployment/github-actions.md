# GitHub Actions, GHCR a Pages

Repozitář používá tři nezávislé workflow v `.github/workflows/`:

| Workflow | Kontrola změn | Výsledek po pushi do výchozí větve |
| --- | --- | --- |
| `backend.yml` | backendový Maven reactor | image `ghcr.io/<owner>/<repo>/kapp-backend-build` |
| `web.yml` | Maven a Angular web | image `ghcr.io/<owner>/<repo>/kapp-web-build` |
| `documentation.yml` | přísný MkDocs build | GitHub Pages |

Buildy běží také pro pull requesty. Publikování image a nasazení Pages je omezené na push do výchozí větve. Workflow lze spustit i ručně přes **Actions → vybrané workflow → Run workflow**; ruční backendový nebo webový běh pouze ověří build a image nepublikuje.

## 1. Nastavení repozitáře

1. Použijte repozitář `ceskaexpedice/kapp` a nastavte výchozí větev `main`.
2. V **Settings → Actions → General** povolte GitHub Actions. Pokud organizace omezuje povolené actions, povolte alespoň oficiální `actions/*` použité ve workflow.
3. Ve stejné sekci zkontrolujte, že workflow mohou používat `GITHUB_TOKEN`. Workflow deklarují pouze nezbytná oprávnění: `contents: read`, při publikování image `packages: write` a při nasazení dokumentace `pages: write` spolu s `id-token: write`.
4. Není potřeba vytvářet PAT ani ukládat registry heslo do repository secrets. GitHub pro každý běh vytvoří krátkodobý `GITHUB_TOKEN`; ten publikuje balíčky spojené se stejným repozitářem.

## 2. GitHub Container Registry

GHCR není potřeba předem zakládat. První úspěšný publish job vytvoří dva container packages:

```text
ghcr.io/ceskaexpedice/kapp/kapp-backend-build:latest
ghcr.io/ceskaexpedice/kapp/kapp-web-build:latest
```

Po prvním publikování otevřete profil organizace **Packages**, u každého package zkontrolujte:

- že je propojený s repozitářem `ceskaexpedice/kapp`,
- že repozitář má oprávnění **Write** nebo **Admin** pro GitHub Actions,
- požadovanou viditelnost package (`private`, `internal` nebo `public`),
- pravidla uchovávání starých verzí, pokud je organizace používá.

Workflow používá cestu odvozenou z `${{ github.repository }}`, převede ji na malá písmena, doplní `kapp-backend-build` nebo `kapp-web-build` a předá ji Jibu přes `jib.to.image`. Přejmenování nebo přesun repozitáře proto nevyžaduje úpravu workflow. Výchozí hodnoty v Maven POM odpovídají repozitáři `ceskaexpedice/kapp`.

### Přihlášení deployment serveru

Veřejný GHCR package lze stáhnout bez přihlášení. Pro neveřejné image vytvořte pro provozní účet personal access token **classic** pouze s oprávněním `read:packages`. Pokud organizace vyžaduje SSO, token pro ni autorizujte. Na deployment serveru:

```bash
export CR_PAT='token-vlozte-bezpecnym-zpusobem'
echo "$CR_PAT" | docker login ghcr.io -u GITHUB_UZIVATEL --password-stdin
unset CR_PAT
```

Token neukládejte do repozitáře ani do `docker-compose.yml`. Docker jej uloží do konfigurace uživatele, pod kterým následně běží `docker compose pull`.

V `nkp-deploy/docker-compose.yml` musí být nové GHCR cesty pro oba obrazy. Poté ověřte:

```bash
docker compose pull
docker compose up -d
docker compose ps
```

## 3. GitHub Pages

1. Otevřete **Settings → Pages**.
2. V části **Build and deployment** nastavte **Source: GitHub Actions**.
3. V **Settings → Environments → github-pages** ponechte deployment povolený pouze z výchozí větve.
4. Spusťte workflow **Documentation** nebo odešlete změnu dokumentace do `main`.
5. Ověřte adresu `https://ceskaexpedice.github.io/kapp/`.

Workflow nejprve provede `mkdocs build --strict`, nahraje adresář `public/` jako Pages artifact a teprve následný job jej nasadí. Privátní repozitář může používat Pages pouze podle možností tarifu a pravidel organizace; publikovaný Pages web může být veřejný.

## 4. Ochrana větve

Pro `main` doporučujeme pravidlo nebo ruleset s povinným pull requestem a zákazem force-push. Workflow používají path filtry, takže backendový nebo webový check nemusí pro nesouvisející změnu vůbec vzniknout. Nenastavujte proto path-filtered check jako globálně povinný bez ověření, že GitHub při přeskočeném workflow neblokuje merge.

Publikační joby přijímají oprávnění k zápisu až po úspěšném buildu a pouze na výchozí větvi. Pull requesty, včetně forků, tedy nemohou publikovat image ani nasazovat Pages.

## 5. Dokončení migrace

Po prvním úspěšném běhu všech workflow:

1. ověřte oba GHCR packages a Pages URL,
2. změňte image cesty v každém deployment prostředí,
3. proveďte `docker compose pull` a kontrolované nasazení,
4. odeberte staré GitLab registry přihlašovací údaje z deployment serverů,
5. archivujte nebo zakažte původní GitLab projekt až po ověření GitHub deploymentu.

Původní `.gitlab-ci.yml` a webová GitLab CI definice byly při této migraci odstraněny. V případě potřeby jsou dohledatelné v Git historii.
