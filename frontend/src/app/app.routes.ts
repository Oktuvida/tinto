import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'invoices/new',
    pathMatch: 'full'
  },
  {
    path: 'invoices/new',
    loadComponent: () =>
      import('../pages/invoices/new-invoice.component').then(
        (m) => m.NewInvoiceComponent
      )
  },
  {
    path: 'invoices/:id',
    loadComponent: () =>
      import('../pages/invoices/invoice-status.component').then(
        (m) => m.InvoiceStatusComponent
      )
  }
];
