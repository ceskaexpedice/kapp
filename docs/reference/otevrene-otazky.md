# Otevřená backendová integrační rozhodnutí

Detaily současného HTTP endpointu a jeho webová omezení jsou vedené pouze v
[technické dokumentaci webové a API vrstvy](../architecture/web-a-api.md).

## Rozlišení zdrojů

Backend ukládá více Krameriů do jednoho core `titles`. Je potřeba potvrdit stabilní význam `digital_library`, `licenses` a `licenses_<acronym>` pro export změn a smluvní licence. Akronym instance nesmí být měněn bez migračního plánu.

## Checkpoint a hranice

`from` i `to` jsou inkluzivní. Odběratel musí:

- uložit checkpoint až po úspěšném zpracování celé odpovědi,
- při opakování intervalu deduplikovat záznamy,
- používat UTC ISO 8601 date-time,
- počítat s více změnami se stejným časem.

## Licence konkrétní instance

Použití polí `licenses_<acronym>` pro rozlišení nebo filtrování licencí jednotlivých instancí Krameria zůstává otevřené. Samotný feed správně používá `state_date`, protože Kramerius odebírá pouze změny licencí; ostatní data odpovědi jsou informativní.

## Provozní odpovědnost

Je potřeba určit:

- kdo spravuje seznam instancí a jejich akronymy,
- kdo spravuje Keycloak role a mapování knihovna-sigla,
- kdo nastavuje checkpoint na straně každého Krameria,
- jak se řeší replay po obnově Solru,
- jak dlouho musí být historie změn dostupná.
