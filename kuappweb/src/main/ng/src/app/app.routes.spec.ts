import { routes } from './app.routes';
import { AuthGuard } from './auth.guard';
import { KappAdminGuard } from './kapp-admin.guard';

describe('application routes', () => {
  it('protects the admin route with the authentication guard', () => {
    const adminRoute = routes.find(route => route.path === 'admin');

    expect(adminRoute).toBeDefined();
    expect(adminRoute?.canActivate).toContain(AuthGuard);
  });

  it('protects library rights with authentication and the kappAdmin role', () => {
    const libraryRightsRoute = routes.find(route => route.path === 'library-rights');

    expect(libraryRightsRoute).toBeDefined();
    expect(libraryRightsRoute?.canActivate).toEqual([AuthGuard, KappAdminGuard]);
  });
});
