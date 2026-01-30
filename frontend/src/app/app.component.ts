import { Component } from '@angular/core';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [],
  template: `
    <div class="container">
      <h1>Tinto DIAN</h1>
      <p>Sistema de Facturación Electrónica</p>
    </div>
  `,
  styles: [`
    .container {
      text-align: center;
      padding: 40px;
    }
    h1 {
      color: #1976d2;
      margin-bottom: 20px;
    }
  `]
})
export class AppComponent {
  title = 'tinto-frontend';
}
