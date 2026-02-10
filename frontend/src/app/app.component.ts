import { Component } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header class="app-header">
      <a routerLink="/" class="brand">Tinto DIAN</a>
      <nav>
        <a routerLink="/invoices/new">Nueva Factura</a>
      </nav>
    </header>
    <main class="app-content">
      <router-outlet />
    </main>
  `,
  styles: [`
    .app-header {
      display: flex;
      align-items: center;
      gap: 24px;
      padding: 12px 24px;
      background: #1976d2;
      color: #fff;
    }
    .app-header a {
      color: #fff;
      text-decoration: none;
    }
    .brand {
      font-size: 1.25rem;
      font-weight: 700;
    }
    nav a {
      opacity: 0.85;
      font-size: 0.875rem;
    }
    nav a:hover {
      opacity: 1;
    }
    .app-content {
      max-width: 960px;
      margin: 24px auto;
      padding: 0 16px;
    }
  `]
})
export class AppComponent {
  title = 'tinto-frontend';
}
