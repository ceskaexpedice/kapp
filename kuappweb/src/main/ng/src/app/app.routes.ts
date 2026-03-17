import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Results } from './pages/results/results';
import { Admin } from './pages/admin/admin';
import { Login } from './pages/login/login';
import { Batches } from './pages/batches/batches';
import { Auth } from './components/auth/auth';
import { Contracts } from './pages/contracts/contracts';
import { AuthGuard } from './auth.guard';

export const routes: Routes = [
    { path: '', component: Home},
    { path: 'login', component: Login },
    { path: 'keycloak', component: Auth },
    { path: 'home', component: Home},
    { path: 'admin', component: Admin},
    { path: 'batches', component: Batches, canActivate: [AuthGuard] },
    { path: 'titles', component: Results},
    { path: 'authors', component: Results},
    { path: 'contracts', component: Contracts, canActivate: [AuthGuard]}
];
