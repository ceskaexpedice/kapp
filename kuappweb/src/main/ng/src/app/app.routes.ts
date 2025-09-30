import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Results } from './pages/results/results';
import { Admin } from './pages/admin/admin';

export const routes: Routes = [
    { path: '', component: Home},
    { path: 'home', component: Home},
    { path: 'admin', component: Admin},
    { path: 'results', component: Results}
];
