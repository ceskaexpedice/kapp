# Nasazení dokumentace

Dokumentační web je čistě statický. Zdrojové stránky jsou ve složce `docs/`, konfigurace v `mkdocs.yml` a GitHub Actions vytváří výsledný web ve složce `public/`.

## Lokální spuštění ve Windows 11

Požadavkem je nainstalovaný Python 3. V PowerShellu spusťte z kořenové složky repozitáře:

```powershell
py -m venv '.venv'
.\.venv\Scripts\python.exe -m pip install --requirement 'requirements.txt'
.\tools\run_mkdocs.ps1
```

Web bude dostupný na adrese:

```text
http://127.0.0.1:8000/kapp/
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

Skript sestaví projekt `C:\VA\Projects\VA\documentation-chat\documentation-chat-ui` a aktualizuje soubory pod `docs/assets/documentation-chat-ui/` a `docs/assets/profiles/`. Tyto soubory patří do Git repozitáře, protože GitHub Pages workflow Node ani Angular nespouští.

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

Workflow `.github/workflows/documentation.yml` se při změnách dokumentace spustí ve výchozí větvi:

1. připraví Python 3.13,
2. nainstaluje přesnou verzi MkDocs Material z `requirements.txt`,
3. provede přísný build do `public/`,
4. nahraje Pages artifact,
5. samostatným jobem jej nasadí na GitHub Pages.

Před prvním během otevřete **Settings → Pages** a jako zdroj zvolte **GitHub Actions**. Workflow používá prostředí `github-pages`, které lze chránit pravidly nasazení.

Výsledná adresa je:

```text
https://ceskaexpedice.github.io/kapp/
```

GitHub Pages web může být veřejný i při privátním repozitáři; dostupnost závisí na tarifu a pravidlech organizace. Před publikováním proto ověřte, že dokumentace neobsahuje neveřejná data.

Kompletní nastavení Actions, oprávnění, GHCR a přihlášení deployment serveru popisuje [návod pro GitHub Actions a registry](github-actions.md).

## Přidání stránky

1. Vytvořte Markdown soubor v odpovídající podsložce `docs/`.
2. Přidejte stránku do sekce `nav` v `mkdocs.yml`.
3. Doplňte odkaz z příslušného rozcestníku.
4. Spusťte lokální server a přísný build.

Veškerá udržovaná technická i uživatelská dokumentace je soustředěná v `docs/` a publikuje se společně tímto GitHub Pages buildem.
