import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ErrorGuidance } from '../../services/invoice.service';

/**
 * DIAN Error Guidance Component
 *
 * Renders DIAN error details with:
 * - Error category and explanation
 * - Suggested corrective actions
 * - Retryable indicator
 *
 * Usage:
 *   <app-invoice-errors [guidance]="errorGuidance" />
 */
@Component({
  selector: 'app-invoice-errors',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './invoice-errors.component.html',
  styleUrls: ['./invoice-errors.component.scss']
})
export class InvoiceErrorsComponent {
  @Input({ required: true }) guidance!: ErrorGuidance;
  @Input() errorCode: string | null = null;
  @Input() errorMessage: string | null = null;

  /**
   * Map error category to a user-friendly Spanish label
   */
  getCategoryLabel(): string {
    const labels: Record<string, string> = {
      XML_STRUCTURE: 'Estructura XML',
      SIGNATURE: 'Firma Digital',
      CUFE_MISMATCH: 'CUFE No Coincide',
      IDENTIFICATION: 'Identificacion',
      NUMBERING: 'Numeracion',
      TAX_CALCULATION: 'Calculo de Impuestos',
      DATE_TIME: 'Fecha / Hora',
      DUPLICATE: 'Factura Duplicada',
      AUTHORIZATION: 'Autorizacion',
      DIAN_SERVICE_ERROR: 'Error del Servicio DIAN',
      UNKNOWN: 'Error Desconocido'
    };
    return labels[this.guidance.category] || this.guidance.category;
  }

  /**
   * Get severity level for styling
   */
  getSeverity(): 'error' | 'warning' {
    return this.guidance.retryable ? 'warning' : 'error';
  }
}
