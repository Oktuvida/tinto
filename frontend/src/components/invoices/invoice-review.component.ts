import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  InvoiceResponse,
  DetailedInvoiceResponse,
  LineItemResponse,
  INVOICE_STATUS_LABELS,
  INVOICE_STATUS_COLORS
} from '../../services/invoice.service';

/**
 * Invoice Review Component
 *
 * Displays invoice details in a read-only format for review before submission
 */
@Component({
  selector: 'app-invoice-review',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './invoice-review.component.html',
  styleUrls: ['./invoice-review.component.scss']
})
export class InvoiceReviewComponent {
  @Input() invoice!: InvoiceResponse;
  @Input() lineItems?: LineItemResponse[];
  @Input() showActions: boolean = true;
  
  @Output() issueToDIAN = new EventEmitter<void>();
  @Output() cancel = new EventEmitter<void>();
  
  statusLabels = INVOICE_STATUS_LABELS;
  statusColors = INVOICE_STATUS_COLORS;
  
  /**
   * Get status badge class
   */
  getStatusClass(): string {
    const color = this.statusColors[this.invoice.status];
    return `status-badge status-${color}`;
  }
  
  /**
   * Check if invoice can be issued to DIAN
   */
  canIssue(): boolean {
    return this.invoice.status === 'DRAFT' || this.invoice.status === 'SIGNED';
  }
  
  /**
   * Format currency
   */
  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: this.invoice.currency,
      minimumFractionDigits: 2
    }).format(amount);
  }
  
  /**
   * Format date
   */
  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat('es-CO', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    }).format(date);
  }
  
  /**
   * Format date and time
   */
  formatDateTime(dateString: string): string {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat('es-CO', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    }).format(date);
  }
  
  /**
   * Handle issue button click
   */
  onIssue(): void {
    this.issueToDIAN.emit();
  }
  
  /**
   * Handle cancel button click
   */
  onCancel(): void {
    this.cancel.emit();
  }
}
