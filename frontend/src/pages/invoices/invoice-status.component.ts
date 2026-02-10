import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { InvoiceErrorsComponent } from '../../components/invoices/invoice-errors.component';
import {
  InvoiceService,
  InvoiceStatusDetail,
  InvoiceResponse,
  INVOICE_STATUS_LABELS,
  INVOICE_STATUS_COLORS,
  DIAN_STATUS_LABELS,
  DIAN_STATUS_COLORS
} from '../../services/invoice.service';

/**
 * Invoice Status Page
 *
 * Displays:
 * - Invoice summary (number, parties, amount)
 * - Current status with DIAN submission details
 * - Error guidance when invoice is rejected/errored
 * - Action buttons: retry, refresh status
 */
@Component({
  selector: 'app-invoice-status',
  standalone: true,
  imports: [CommonModule, RouterLink, InvoiceErrorsComponent],
  templateUrl: './invoice-status.component.html',
  styleUrls: ['./invoice-status.component.scss']
})
export class InvoiceStatusComponent implements OnInit {
  invoiceId = '';
  invoice = signal<InvoiceResponse | null>(null);
  statusDetail = signal<InvoiceStatusDetail | null>(null);
  loading = signal(true);
  refreshing = signal(false);
  error = signal<string | null>(null);

  statusLabels = INVOICE_STATUS_LABELS;
  statusColors = INVOICE_STATUS_COLORS;
  dianStatusLabels = DIAN_STATUS_LABELS;
  dianStatusColors = DIAN_STATUS_COLORS;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private invoiceService: InvoiceService
  ) {}

  ngOnInit(): void {
    this.invoiceId = this.route.snapshot.paramMap.get('id') || '';
    if (!this.invoiceId) {
      this.error.set('ID de factura no proporcionado');
      this.loading.set(false);
      return;
    }
    this.loadStatus();
  }

  /**
   * Load invoice and status details
   */
  async loadStatus(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);

    try {
      const [invoice, statusDetail] = await Promise.all([
        this.invoiceService.getInvoice(this.invoiceId) as Promise<InvoiceResponse>,
        this.invoiceService.getInvoiceStatus(this.invoiceId)
      ]);

      this.invoice.set(invoice);
      this.statusDetail.set(statusDetail);
    } catch (err: any) {
      this.error.set(err.message || 'Error al cargar la factura');
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * Refresh status by polling DIAN
   */
  async refreshStatus(): Promise<void> {
    this.refreshing.set(true);

    try {
      const statusDetail = await this.invoiceService.refreshInvoiceStatus(this.invoiceId);
      this.statusDetail.set(statusDetail);
    } catch (err: any) {
      this.error.set(err.message || 'Error al actualizar el estado');
    } finally {
      this.refreshing.set(false);
    }
  }

  /**
   * Issue invoice to DIAN
   */
  async issueInvoice(): Promise<void> {
    this.refreshing.set(true);
    this.error.set(null);

    try {
      await this.invoiceService.issueInvoice(this.invoiceId);
      await this.loadStatus();
    } catch (err: any) {
      this.error.set(err.message || 'Error al enviar a la DIAN');
      this.refreshing.set(false);
    }
  }

  /**
   * Format currency
   */
  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: this.invoice()?.currency || 'COP',
      minimumFractionDigits: 2
    }).format(amount);
  }

  /**
   * Format date
   */
  formatDate(dateString: string): string {
    return new Intl.DateTimeFormat('es-CO', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    }).format(new Date(dateString));
  }

  /**
   * Format date and time
   */
  formatDateTime(dateString: string): string {
    return new Intl.DateTimeFormat('es-CO', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(new Date(dateString));
  }
}
