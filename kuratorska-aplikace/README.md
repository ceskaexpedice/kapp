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

## Automatické přechody
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
    KONTROLOVANE_NEVEREJNE --> KE_KONTROLE : Uplynutí rozhodného roku
    KE_KONTROLE --> KONTROLOVANE_VOLNE : Posouzení kurátora
    KE_KONTROLE --> KONTROLOVANÉ_NEVEŘEJNÉ : Posouzení kurátora
```

## Možné přechody u existujících dokumentů
Všechny stavy jsou možné


## Vícesvazkové monografie a periodika

Na úrovni vícesvazku nebo periodika bude pouze příznak/rodičovský stav.  Který bude jenom určovat zda je celek určený ke kontrole nebo je zkontrolován. 


