export class User {

  
         ///user.realm_access.roles

  authenticated: boolean = false;
  sid: string;
  name: string;
  preferred_username: string;
  realm_access: {roles: string[]};
  resource_access: {[resource: string]: {roles: string[]}};
  roles: string[];
  licenses: string[];
  session: any;

  static fromJson(json: any): User {
    if (json) {
      const user = new User();
      user.authenticated = !!json['authenticated'];
      user.name = (json['name'] || '').trim();
      user.sid = json['sid'];
      user.preferred_username = json['preferred_username'];
      user.realm_access = json['realm_access'] || {roles: []};
      user.resource_access = json['resource_access'] || {};
      user.roles = json['roles'] || [];
      user.session = json['session'] || {};
      user.licenses = json['licenses'] || [];
      return user;
    }
    return null;
  }

  constructor() {
  }

  isLoggedIn(): boolean {
    return this.authenticated;
  }

  /*
  isAdmin(): boolean {
    return this.roles.indexOf('kramerius_admin') >= 0;
  }*/
}
