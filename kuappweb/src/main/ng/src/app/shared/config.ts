
export interface Sort { label: string; field: string; dir: string; entity?: string[]};


export class Configuration {

  deployPath: string;
  coreBaseUrl: string;
  clientApiBaseUrl: string;
  defaultLang: string;
  
  userClientBaseUrl: string;
  keycloak: {clientId: string, loginType: string, logoutUrl: string};
  
  states: string[];
  licenses: string[];
}
