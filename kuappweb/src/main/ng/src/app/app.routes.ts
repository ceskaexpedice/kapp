import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Results } from './pages/results/results';
import { Admin } from './pages/admin/admin';
import { Login } from './pages/login/login';
import { Batches } from './pages/batches/batches';

export const routes: Routes = [
    { path: '', component: Home},
    { path: 'login', component: Login },
    { path: 'home', component: Home},
    { path: 'admin', component: Admin},
    { path: 'batches', component: Batches },
    { path: 'titles', component: Results},
    { path: 'authors', component: Results}
];
