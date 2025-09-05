import { Routes } from '@angular/router';
import { Home } from './pages/home/home';
import { Results } from './pages/results/results';

export const routes: Routes = [
    { path: '', component: Home},
    { path: 'home', component: Home},
    { path: 'results', component: Results}
];
