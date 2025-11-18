# NKP - Kurátorská aplikace

Aplikace zajistí komplexní správu veřejných a neveřejných dokumentů, včetně jejich zveřejnění v systému 
Kramerius.

Data jsou importováná z jednotlivých instancí krameria a dokumenty postupují nasledující stavy


## Import dokumentů

```mermaid
stateDiagram-v2
    direction LR

    state Imported <<start>>
    state Konec <<end>>

    state NEKONTROLOVANÉ_ZPŘÍSTUPNĚNÉ : Automaticky přidělovaná licence public - Nedochází ke kurátorské kontrole (Monografie pred rokem 1900 a rocniky starsi nez 110 let)
    state NEKONTROLOVANÉ : Automaticky přidělovaná licence onsite - Nedochází ke kurátorské kontrole (Mapy, monografie a hudebniny vydané před méně než 70 lety)
    state KE_KONTROLE : Nutná kurátorská kontrola

    state KONTROLOVANÉ_VOLNÉ : Public (Volné AP)
    state KONTROLOVANÉ_NEVEŘEJNÉ : Private (Běží AP, s rozhodným rokem)
    state KONTROLOVANÉ_NEZNÁMÉ : Private (Neznámý stav AP)
    state KONTROLOVANÉ_NEZNÁMÉ_ZPŘÍSTUPNĚNÉ : Public (Neznámé AP, ale zpřístupněno)
    state KONTROLOVANÉ_NEVEŘEJNÉ_LICENCE : Public/Private (Neveřejné, ale s licencí)
    state KONTROLOVANÉ_BEZ_AP : Public (Dílo bez AP)
    state KONTROLOVANÉ_ZNEPŘÍSTUPNĚNÉ : Private (Znepřístupněno)

    %% Pocatecni prechody 
    [*] --> Imported
    Imported --> NEKONTROLOVANE_ZPRISTUPNENE : Automatický přechod - starší než 110 nebo 1900 
    Imported --> NEKONTROLOVANE : Automatický přechod - mladší než 70
    Imported --> KE_KONTROLE : Nutná kurátorská kontrola

```
## Kurátorská kontrola

```mermaid
stateDiagram-v2
    direction LR

    state Imported <<start>>
    state Konec <<end>>

    state NEKONTROLOVANÉ_ZPŘÍSTUPNĚNÉ : Automaticky přidělovaná licence public - Nedochází ke kurátorské kontrole (Monografie pred rokem 1900 a rocniky starsi nez 110 let)
    state NEKONTROLOVANÉ : Automaticky přidělovaná licence onsite - Nedochází ke kurátorské kontrole (Mapy, monografie a hudebniny vydané před méně než 70 lety)
    state KE_KONTROLE : Nutná kurátorská kontrola

    state KONTROLOVANÉ_VOLNÉ : Public (Volné AP)
    state KONTROLOVANÉ_NEVEŘEJNÉ : Private (Běží AP, s rozhodným rokem)
    state KONTROLOVANÉ_NEZNÁMÉ : Private (Neznámý stav AP)
    state KONTROLOVANÉ_NEZNÁMÉ_ZPŘÍSTUPNĚNÉ : Public (Neznámé AP, ale zpřístupněno)
    state KONTROLOVANÉ_NEVEŘEJNÉ_LICENCE : Public/Private (Neveřejné, ale s licencí)
    state KONTROLOVANÉ_BEZ_AP : Public (Dílo bez AP)
    state KONTROLOVANÉ_ZNEPŘÍSTUPNĚNÉ : Private (Znepřístupněno)


    %% Přechody po kurátorské kontrole
    KE_KONTROLE --> KONTROLOVANE_VOLNE : Kontrola - Volné
    KE_KONTROLE --> KONTROLOVANE_NEVEREJNE : Kontrola -  Běží AP, známý rok kdy vyprší 
    KE_KONTROLE --> KONTROLOVANE_NEZNAME : Kontrola - Neznámý stav AP 
    KE_KONTROLE --> KONTROLOVANE_NEZNAME_ZPRISTUPNENE : Neznámý stav AP ale bylo zpřístupněno dle jiné podmínky 
    KE_KONTROLE --> KONTROLOVANE_NEVEREJNE_LICENCE : Kontrola - Knihovna na něj vlastní licenci - ? 
    KE_KONTROLE --> KONTROLOVANE_BEZ_AP : Kontrola - Bez AP - Public
    KE_KONTROLE --> KONTROLOVANE_ZNEPRISTUPNENE : Kontrola -  Jiný důvod znepřístupnění  
```

## Automatické prechody
```mermaid
stateDiagram-v2
    direction LR

    state Imported <<start>>
    state Konec <<end>>

    state NEKONTROLOVANÉ_ZPŘÍSTUPNĚNÉ : Automaticky přidělovaná licence public - Nedochází ke kurátorské kontrole (Monografie pred rokem 1900 a rocniky starsi nez 110 let)
    state NEKONTROLOVANÉ : Automaticky přidělovaná licence onsite - Nedochází ke kurátorské kontrole (Mapy, monografie a hudebniny vydané před méně než 70 lety)
    state KE_KONTROLE : Nutná kurátorská kontrola

    state KONTROLOVANÉ_VOLNÉ : Public (Volné AP)
    state KONTROLOVANÉ_NEVEŘEJNÉ : Private (Běží AP, s rozhodným rokem)
    state KONTROLOVANÉ_NEZNÁMÉ : Private (Neznámý stav AP)
    state KONTROLOVANÉ_NEZNÁMÉ_ZPŘÍSTUPNĚNÉ : Public (Neznámé AP, ale zpřístupněno)
    state KONTROLOVANÉ_NEVEŘEJNÉ_LICENCE : Public/Private (Neveřejné, ale s licencí)
    state KONTROLOVANÉ_BEZ_AP : Public (Dílo bez AP)
    state KONTROLOVANÉ_ZNEPŘÍSTUPNĚNÉ : Private (Znepřístupněno)


    %% Automatické přechody v čase
    KONTROLOVANE_NEVEREJNE --> KONTROLOVANE_VOLNE : Uplynutí rozhodného roku
    KONTROLOVANE_NEVEREJNE_LICENCE --> KONTROLOVANE_VOLNE : Uplynutí rozhodného roku
```



## Contributing
State if you are open to contributions and what your requirements are for accepting them.

For people who want to make changes to your project, it's helpful to have some documentation on how to get started. Perhaps there is a script that they should run or some environment variables that they need to set. Make these steps explicit. These instructions could also be useful to your future self.

You can also document commands to lint the code or run tests. These steps help to ensure high code quality and reduce the likelihood that the changes inadvertently break something. Having instructions for running tests is especially helpful if it requires external setup, such as starting a Selenium server for testing in a browser.

## Authors and acknowledgment
Show your appreciation to those who have contributed to the project.

## License
For open source projects, say how it is licensed.

## Project status
If you have run out of energy or time for your project, put a note at the top of the README saying that development has slowed down or stopped completely. Someone may choose to fork your project or volunteer to step in as a maintainer or owner, allowing your project to keep going. You can also make an explicit request for maintainers.
