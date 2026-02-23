

export class Contract {
  id: string; 
  title: string;
  filename: string; 
  cislo_smlouvy: string; 
  id_smlouvy: string; 
  nazev_poskytovatele: string; 
  ico: string; 
  datum_uzavreni: string; 
  datum_platnosti: string; 
  kontaktni_osoby: string[]; 
  digitalni_knihovny: string[] = ['NK'];
  hlavni: boolean = true;
  hlavni_smlouva: string; 
  dodatky: string[] = [];
  cislo_dodatku: string;
  souhrn: string;
  license: string;
  ccnb: string[] = [];
  issn: string[] = [];

}


  