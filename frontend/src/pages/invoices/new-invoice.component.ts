import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { InvoiceService, CreateInvoiceRequest, LineItemRequest } from '../../services/invoice.service';

/**
 * New Invoice Form Page
 *
 * Allows users to create a new electronic invoice by entering:
 * - Issuer NIT
 * - Customer information
 * - Line items (products/services)
 * - Totals calculation
 */
@Component({
  selector: 'app-new-invoice',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './new-invoice.component.html',
  styleUrls: ['./new-invoice.component.scss']
})
export class NewInvoiceComponent {
  // Form data signals
  issuerNit = signal('');
  customerIdentificationType = signal('NIT');
  customerIdentificationNumber = signal('');
  prefix = signal('');
  
  // Line items
  lineItems = signal<LineItemRequest[]>([]);
  
  // Current line item being edited
  currentLineItem = {
    description: '',
    quantity: 1,
    unitPrice: 0,
    taxRate: 19
  };
  
  // Calculated totals
  subtotal = signal(0);
  taxAmount = signal(0);
  total = signal(0);
  
  // UI state
  isSubmitting = signal(false);
  error = signal<string | null>(null);
  
  constructor(
    private invoiceService: InvoiceService,
    private router: Router
  ) {}
  
  /**
   * Add line item to invoice
   */
  addLineItem(): void {
    if (!this.currentLineItem.description || this.currentLineItem.quantity <= 0 || this.currentLineItem.unitPrice < 0) {
      this.error.set('Please fill in all line item fields correctly');
      return;
    }
    
    const newLineItem: LineItemRequest = {
      description: this.currentLineItem.description,
      quantity: this.currentLineItem.quantity,
      unitPrice: this.currentLineItem.unitPrice,
      taxRate: this.currentLineItem.taxRate
    };
    
    this.lineItems.update(items => [...items, newLineItem]);
    this.calculateTotals();
    
    // Reset form
    this.currentLineItem = {
      description: '',
      quantity: 1,
      unitPrice: 0,
      taxRate: 19
    };
    this.error.set(null);
  }
  
  /**
   * Remove line item from invoice
   */
  removeLineItem(index: number): void {
    this.lineItems.update(items => items.filter((_, i) => i !== index));
    this.calculateTotals();
  }
  
  /**
   * Calculate invoice totals
   */
  calculateTotals(): void {
    let subtotal = 0;
    let taxAmount = 0;
    
    this.lineItems().forEach(item => {
      const lineTotal = item.quantity * item.unitPrice;
      subtotal += lineTotal;
      
      if (item.taxRate) {
        taxAmount += (lineTotal * item.taxRate) / 100;
      }
    });
    
    this.subtotal.set(subtotal);
    this.taxAmount.set(taxAmount);
    this.total.set(subtotal + taxAmount);
  }
  
  /**
   * Create invoice
   */
  async createInvoice(): Promise<void> {
    if (!this.issuerNit() || !this.customerIdentificationNumber()) {
      this.error.set('Please enter issuer and customer information');
      return;
    }
    
    if (this.lineItems().length === 0) {
      this.error.set('Please add at least one line item');
      return;
    }
    
    this.isSubmitting.set(true);
    this.error.set(null);
    
    try {
      const request: CreateInvoiceRequest = {
        issuerNit: this.issuerNit(),
        customerIdentificationType: this.customerIdentificationType(),
        customerIdentificationNumber: this.customerIdentificationNumber(),
        prefix: this.prefix() || undefined,
        lineItems: this.lineItems(),
        totalAmount: this.total()
      };
      
      const invoice = await this.invoiceService.createInvoice(request);
      
      // Navigate to invoice details/review page
      this.router.navigate(['/invoices', invoice.id]);
      
    } catch (err: any) {
      this.error.set(err.message || 'Failed to create invoice');
      this.isSubmitting.set(false);
    }
  }
  
  /**
   * Navigate back to home
   */
  goHome(): void {
    this.router.navigate(['/']);
  }

  /**
   * Format currency
   */
  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('es-CO', {
      style: 'currency',
      currency: 'COP',
      minimumFractionDigits: 2
    }).format(amount);
  }
}
