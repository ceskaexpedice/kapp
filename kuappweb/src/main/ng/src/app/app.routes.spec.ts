import { routes } from './app.routes';
import { AuthGuard } from './auth.guard';

describe('application routes', () => {
  it('protects the admin route with the authentication guard', () => {
    const adminRoute = routes.find(route => route.path === 'admin');

    expect(adminRoute).toBeDefined();
    expect(adminRoute?.canActivate).toContain(AuthGuard);
  });
});
