# Nasazení dokumentace

Dokumentační web je čistě statický. Zdrojové stránky jsou ve složce `docs/`, konfigurace v `mkdocs.yml` a GitLab CI vytváří výsledný web ve složce `public/`.

## Lokální spuštění ve Windows 11

Požadavkem je nainstalovaný Python 3. V PowerShellu spusťte z kořenové složky repozitáře:

```powershell
py -m venv '.venv'
.\.venv\Scripts\python.exe -m pip install --requirement 'requirements.txt'
.\tools\run_mkdocs.ps1
```

Web bude dostupný na adrese:

```text
http://127.0.0.1:8000/nkp/nkp-kuratorska-aplikace/
```

Server průběžně sleduje změny v `docs/` i `mkdocs.yml`. Ukončíte ho klávesami ++ctrl+c++.

Výchozí spuštění používá vzdálené Documentation Chat API. Lokální API zapnete přepínačem:

```powershell
.\tools\run_mkdocs.ps1 -LocalChatApi
```

## Aktualizace chatovacího UI

Chatovací Web Component se vyvíjí v samostatném Angular projektu. Produkční bundle a profilová loga zkopírujete do MkDocs dokumentace příkazem:

```powershell
.\tools\build_documentation_chat_ui.ps1
```

Skript sestaví projekt `C:\VA\Projects\VA\documentation-chat\documentation-chat-ui` a aktualizuje soubory pod `docs/assets/documentation-chat-ui/` a `docs/assets/profiles/`. Tyto soubory patří do Git repozitáře, protože GitLab Pages job Node ani Angular nespouští.

Launcher používá profil `kuratorska-aplikace`. Produkční API je nastavené v `docs/assets/documentation-chat-launcher.js`, lokální konfigurace v `docs/assets/documentation-chat-local-config.js`.

## Dokumentace pro LLM asistenta

Markdown stránky z `docs/` sloučíte příkazem:

```powershell
.\.venv\Scripts\python.exe '.\tools\build_kuratorska_aplikace_doc.py'
```

Výstup `out/kuratorska-aplikace-doc.md` lze publikovat do Documentation Chat API příkazem:

```powershell
.\.venv\Scripts\python.exe '.\tools\publish_kuratorska_aplikace_doc.py'
```

Publisher posílá dokument metodou `PUT` na `/api/assistants/kuratorska-aplikace/documents/kuratorska-aplikace` a vyžaduje API klíč.

## Kontrola produkčního sestavení

Před odesláním změn spusťte stejný přísný build jako v CI:

```powershell
.\.venv\Scripts\python.exe -m mkdocs build --strict --site-dir 'public'
```

Po úspěšném sestavení musí existovat `public/index.html`. Složka `public/` je generovaná a nepatří do Git repozitáře.

## Automatické publikování

Job `deploy-pages` v `.gitlab-ci.yml` se spustí pouze ve výchozí větvi:

1. použije Python image,
2. nainstaluje přesnou verzi MkDocs Material z `requirements.txt`,
3. provede přísný build do `public/`,
4. předá výstup službě GitLab Pages.

Po prvním úspěšném běhu otevřete v projektu **Deploy → Pages**. Pokud chcete používat předvídatelnou adresu nastavenou v `mkdocs.yml`, vypněte **Use unique domain**.

Výsledná adresa je:

```text
https://inovatika.gitlab.io/nkp/nkp-kuratorska-aplikace/
```

Pokud má být web veřejný a repozitář zůstane privátní, nastavte v **Settings → General → Visibility, project features, permissions** přístup k Pages na **Everyone**. Dostupnost této volby může být omezená nastavením nadřazené skupiny.

## Přidání stránky

1. Vytvořte Markdown soubor v odpovídající podsložce `docs/`.
2. Přidejte stránku do sekce `nav` v `mkdocs.yml`.
3. Doplňte odkaz z příslušného rozcestníku.
4. Spusťte lokální server a přísný build.

Veškerá udržovaná technická i uživatelská dokumentace je soustředěná v `docs/` a publikuje se společně tímto GitLab Pages buildem.
