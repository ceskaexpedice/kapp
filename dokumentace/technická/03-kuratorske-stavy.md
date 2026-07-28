# Kurátorské stavy a rozhodování

Aktuální stav je v `state`, výsledek licenčního rozhodnutí v `assigned_licenses`. Zdrojové licence z Krameria zůstávají odděleně v `licenses` a `licenses_<acronym>`.

## Stavy

### Automatické a importní

- `IMPORTED` - záznam byl importován, ale nemá dokončený automatický výsledek.
- `NONCONTROLLED_PUBLIC` - automaticky veřejný, obvykle `public`.
- `NONCONTROLLED_PRIVATE` - automaticky on-site, obvykle `onsite`.
- `TO_CONTROL` - vyžaduje kurátorskou kontrolu.
- `FOR_REVIEW` - dříve kontrolovaný záznam vyžaduje nové posouzení.

### Kurátorské

- `CONTROLLED_PUBLIC`
- `CONTROLLED_PRIVATE`
- `CONTROLLED_UNKNOWN`
- `CONTROLLED_UNKNOWN_PUBLIC`
- `CONTROLLED_WO_RIGHTS`
- `CONTROLLED_BLOCKED`

Web používá stejné stavové hodnoty jako backend. České a anglické názvy zobrazené uživateli jsou pouze překlady těchto enum hodnot. Konkrétní volby ve webu určuje webová konfigurace; uživatelské významy jsou v
[uživatelské dokumentaci webu](../../kuratorska-aplikace-web/UZIVATELSKA_DOKUMENTACE.md#ruční-kurátorské-stavy).

### Hierarchické

- `PARENT_OPEN` - alespoň jedno dítě čeká na kontrolu.
- `PARENT_CLOSED` - žádné dítě nečeká na kontrolu.
- `PARENT_CONTROLLED` - rodič byl kurátorsky uzavřen.

## Vstupy automatického rozhodnutí

Workflow používá:

- `date_issued_year_kapp`, pokud existuje, jinak `date_issued_year`,
- `last_year_death`,
- model a `is_hierarchical`,
- `is_pseudonymous`, `is_anonymous`, `is_collective`, `is_official`,
- hranice `publicYear`, `onsiteYear`, `authorDeadEnoughYear`.

`date_issued_year_kapp` má přednost, protože představuje kurátorsky opravený rok a nesmí být přepsán další sklizní.

## Základní pravidla

- Velmi staré vydání může přejít na `NONCONTROLLED_PUBLIC`.
- Nové vydání spadá do `NONCONTROLLED_PRIVATE`.
- Monografie a monografické jednotky mezi hranicemi přecházejí na `TO_CONTROL`.
- `periodicalvolume` se vyhodnocuje podle roku vydání; bez roku vyžaduje kontrolu.
- Jiné modely jsou standardně neveřejné.
- Hierarchický root se dopočítá ze stavů dětí.
- Známý poslední autor se hodnotí podle `authorDeadEnoughYear` a současně podle data vydání.

## Pravidla přednostního uvolnění

Příznaky pseudonymního, anonymního, kolektivního a úředního díla aktivují přednostní pravidlo. Z příznaku a roku vydání se vypočte `release_year`. Pokud je rok již dosažen, workflow použije uvolněný cílový stav; jinak neveřejný stav nebo ponechá záznam k budoucímu přepočtu.

Při více příznacích má úřední dílo vyšší prioritu než ostatní kategorie. Pokud bez roku vydání nelze pravidlo spočítat, změna se neprovede a důvod tuto skutečnost zaznamená.

## Automatické přechody

Updater znovu vyhodnocuje všechny tituly, ne pouze tituly s `assigned_licenses:onsite`. Může například:

- posunout automaticky neveřejný titul ke kontrole nebo na public,
- označit kontrolovaný neveřejný/neurčitý titul jako `FOR_REVIEW`,
- přepočítat periodika,
- reagovat na kurátorskou opravu roku vydání nebo posledního autora,
- reagovat na příznaky anonymního, kolektivního, pseudonymního nebo úředního díla,
- opravit/doplnit `release_year`.

## Záznam změny

Provedený přechod nastaví:

- `state`,
- `state_date`,
- `state_user`,
- `state_reason`,
- položku v `state_history`,
- případnou změnu `assigned_licenses`,
- `release_year`,
- `last_modified_date`.

`state_date` je integrační timestamp používaný endpointem pro Kramerius k výpisu změn licencí. Ostatní vracená data jsou pro Kramerius pouze informativní.

## Licence ze smluv

Licence smluv jsou nezávislý doplňující mechanismus. Updater najde tituly podle ČČNB/ISSN a pro každou licenci s prefixem knihovny zkontroluje, zda titul pochází z odpovídající instance. Chybějící licence přidá do `assigned_licenses`; existující hodnoty neodstraňuje.
