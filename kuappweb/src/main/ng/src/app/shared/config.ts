
export interface Sort { label: string; field: string; dir: string; entity?: string[]};


export class Configuration {

  deployPath: string;
  keycloakUrl: string;
  coreBaseUrl: string;
  clientApiBaseUrl: string;
  defaultLang: string;
  
  userClientBaseUrl: string;
  keycloak: {clientId: string, loginType: string, logoutUrl: string};
  
  states: string[];
  titleColumns: {field: string, selected: boolean}[];
  licenses: string[];
  digitalni_knihovny: string[];
}
