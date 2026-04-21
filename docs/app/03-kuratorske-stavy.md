# Kurátorské stavy a rozhodování

Aplikace ukládá stav dokumentu do pole `state`. Aktuální implementace zapisuje názvy enum hodnot, například `NONCONTROLLED_PUBLIC`, nikoli jejich textové hodnoty typu `noncontrolled_public`.

## Hlavní skupiny stavů

### Importní a automatické stavy

- `IMPORTED` výchozí stav nově vytvořeného workflow dokumentu před automatickým rozhodnutím.
- `NONCONTROLLED_PUBLIC` dokument je automaticky vyhodnocený jako veřejný; aplikace přiřazuje licenci `public`.
- `NONCONTROLLED_PRIVATE` dokument je automaticky vyhodnocený jako neveřejný/on-site; aplikace přiřazuje licenci `onsite`.
- `TO_CONTROL` dokument vyžaduje kurátorskou kontrolu.
- `FOR_REVIEW` dokument byl dříve kontrolovaný, ale časem nebo novými informacemi vznikl důvod k opětovnému posouzení.

### Kurátorské stavy

- `CONTROLLED_PUBLIC` kurátorem schválené veřejné dílo; aplikace přiřazuje licenci `public`.
- `CONTROLLED_PRIVATE` kurátorem označené neveřejné/on-site dílo; aplikace přiřazuje licenci `onsite`.
- `CONTROLLED_UNKNOWN` kurátor nezná stav autorských práv.
- `CONTROLLED_UNKNOWN_PUBLIC` stav neznámý, ale dokument má být zpřístupněný.
- `CONTROLLED_WO_RIGHTS` dílo bez autorských práv; aplikace přiřazuje licenci `public`.
- `CONTROLLED_BLOCKED` dílo je blokované/znepřístupněné z jiného důvodu.

### Hierarchické stavy rodičů

- `PARENT_OPEN` rodičovský dokument má alespoň jedno dítě ve stavu `TO_CONTROL`.
- `PARENT_CLOSED` rodičovský dokument nemá děti ve stavu `TO_CONTROL`.

## Automatické rozhodnutí po importu

Automatické rozhodnutí se počítá z:

- roku vydání,
- roku úmrtí posledního autora,
- typu/modelu dokumentu,
- informace, zda jde o hierarchický titul,
- hodnot `workflow.publicYear` a `workflow.onsiteYear`.

Při výchozí konfiguraci `publicYear = 110` a `onsiteYear = 70` se dokumenty rozdělují následovně:

- Rok vydání starší nebo rovný hranici `aktuální rok - 110`: `NONCONTROLLED_PUBLIC`.
- Rok vydání novější nebo rovný hranici `aktuální rok - 70`: `NONCONTROLLED_PRIVATE`.
- Monografie a monografické jednotky mezi těmito hranicemi: `TO_CONTROL`.
- Svazky periodik (`periodicalvolume`): `NONCONTROLLED_PRIVATE`.
- Ostatní modely mimo monografie: `NONCONTROLLED_PRIVATE`.

Hierarchické rodičovské dokumenty při prvotním výpočtu zůstávají na `IMPORTED`; jejich stav se později dopočítává podle dětí na `PARENT_OPEN` nebo `PARENT_CLOSED`.

## Přechody v čase

Updater a import změněných MODS mohou dokument posunout:

- `NONCONTROLLED_PRIVATE` může přejít na `TO_CONTROL`, pokud podle roku úmrtí autora nebo data vydání už nespadá do on-site okna a je potřeba posouzení.
- `NONCONTROLLED_PRIVATE` může přejít na `NONCONTROLLED_PUBLIC`, pokud už podle data vydání překročil veřejnou hranici a nejsou k dispozici přesnější autorské údaje.
- `TO_CONTROL` může automaticky přejít na `NONCONTROLLED_PUBLIC`, pokud rok úmrtí autora nebo rok vydání splňuje veřejnou hranici.
- `TO_CONTROL` může automaticky přejít na `NONCONTROLLED_PRIVATE`, pokud rok vydání stále spadá do on-site okna.
- `CONTROLLED_PRIVATE` může přejít na `FOR_REVIEW`, pokud časem překročí relevantní hranici.
- `CONTROLLED_UNKNOWN` může přejít na `FOR_REVIEW`, pokud se doplní informace, která dovoluje nové posouzení.
- `CONTROLLED_UNKNOWN` může přejít na `NONCONTROLLED_PRIVATE`, pokud nově zjištěné datum vydání ukáže, že jde o nedávné dílo.

## Kurátorské rozhodnutí

Kurátorský přechod dovoluje nastavit jen vybrané cílové stavy:

- `CONTROLLED_PRIVATE`
- `CONTROLLED_PUBLIC`
- `CONTROLLED_UNKNOWN`
- `CONTROLLED_BLOCKED`
- `CONTROLLED_WO_RIGHTS`
- `CONTROLLED_UNKNOWN_PUBLIC`

V dostupném backendu je kurátorské rozhodnutí zpracované hlavně přes TSV import. Webové UI pro ruční přepínání stavů není v tomto checkoutu k dispozici.

## Licence přiřazované aplikací

Workflow stav může aktualizovat pole `assigned_licenses`:

- Stav s přiřazením `PUBLIC` odstraní `onsite` a přidá `public`.
- Stav s přiřazením `ONSITE` odstraní `public` a přidá `onsite`.
- Stav s přiřazením `NONE` licenci nemění.
- Stav s přiřazením `LOCAL` je v kódu připravený, ale není implementovaný.

## Historie stavů

Každá provedená workflow změna nastavuje:

- `state`
- `state_date`
- `state_user`
- `state_reason`

Zároveň přidává položku do `state_history` ve formě JSON objektu s klíči:

- `date`
- `state`
- `user`
- `reason`

## Rodičovské stavy

Po importu se spouští přepočet rodičů. Mechanismus vyhledá dokumenty s `is_hierarchical:true`, seskupí jejich děti podle `root_pid` a nastaví:

- `PARENT_OPEN`, pokud některé dítě čeká na kontrolu,
- `PARENT_CLOSED`, pokud žádné dítě nečeká na kontrolu.

Prakticky to znamená, že operátor má u hierarchických titulů řešit hlavně otevřené rodiče, protože signalizují rozpracované nebo nezkontrolované podřízené části.
