
export interface Sort { label: string; field: string; dir: string; entity?: string[]};


export class Configuration {
  context: string;
  defaultLang: string;
  
  userClientBaseUrl: string;
  keycloak: {clientId: string, loginType: string};
  
  states: string[];
  licenses: string[];
}
