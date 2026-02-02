import { Injectable } from '@angular/core';
import { ApiClient } from './api-client';

/**
 * Invoice Service
 *
 * Handles all invoice-related API calls
 */
@Injectable({
  providedIn: 'root'
})
export class InvoiceService {
  constructor(private apiClient: ApiClient) {}

  /**
   * Create a new invoice
   */
  async createInvoice(request: CreateInvoiceRequest): Promise<InvoiceResponse> {
    return this.apiClient.post<InvoiceResponse>('/v1/invoices', request);
  }

  /**
   * Issue invoice to DIAN
   */
  async issueInvoice(invoiceId: string): Promise<InvoiceResponse> {
    return this.apiClient.post<InvoiceResponse>(`/v1/invoices/${invoiceId}/issue`, {});
  }

  /**
   * Get invoice by ID
   */
  async getInvoice(invoiceId: string, includeLineItems: boolean = false): Promise<InvoiceResponse | DetailedInvoiceResponse> {
    const params = includeLineItems ? '?includeLineItems=true' : '';
    return this.apiClient.get<InvoiceResponse | DetailedInvoiceResponse>(`/v1/invoices/${invoiceId}${params}`);
  }

  /**
   * List invoices by issuer
   */
  async listInvoices(issuerNit: string): Promise<InvoiceResponse[]> {
    return this.apiClient.get<InvoiceResponse[]>(`/v1/invoices?issuerNit=${encodeURIComponent(issuerNit)}`);
  }
}

/**
 * Type definitions
 */

export interface CreateInvoiceRequest {
  issuerNit: string;
  customerIdentificationType: string;
  customerIdentificationNumber: string;
  prefix?: string;
  issueDate?: string; // ISO date string
  dueDate?: string; // ISO date string
  currencyCode?: string;
  lineItems: LineItemRequest[];
  totalAmount?: number;
}

export interface LineItemRequest {
  description: string;
  quantity: number;
  unitPrice: number;
  taxRate?: number;
  itemCode?: string;
}

export interface InvoiceResponse {
  id: string;
  invoiceNumber: string;
  prefix: string | null;
  number: number;
  issuerNit: string;
  issuerName: string;
  customerIdentification: string;
  customerName: string;
  issueDate: string;
  dueDate: string | null;
  currency: string;
  subtotal: number;
  taxAmount: number;
  totalAmount: number;
  status: InvoiceStatus;
  cufe: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DetailedInvoiceResponse {
  invoice: InvoiceResponse;
  lineItems: LineItemResponse[];
}

export interface LineItemResponse {
  lineNumber: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  taxRate: number | null;
  taxAmount: number | null;
}

export type InvoiceStatus =
  | 'DRAFT'
  | 'PENDING_SIGNATURE'
  | 'SIGNED'
  | 'SUBMITTED_TO_DIAN'
  | 'ACCEPTED_BY_DIAN'
  | 'REJECTED_BY_DIAN'
  | 'CANCELLED';

/**
 * Status labels in Spanish
 */
export const INVOICE_STATUS_LABELS: Record<InvoiceStatus, string> = {
  DRAFT: 'Borrador',
  PENDING_SIGNATURE: 'Pendiente de Firma',
  SIGNED: 'Firmada',
  SUBMITTED_TO_DIAN: 'Enviada a DIAN',
  ACCEPTED_BY_DIAN: 'Aceptada por DIAN',
  REJECTED_BY_DIAN: 'Rechazada por DIAN',
  CANCELLED: 'Cancelada'
};

/**
 * Status colors for UI
 */
export const INVOICE_STATUS_COLORS: Record<InvoiceStatus, string> = {
  DRAFT: 'gray',
  PENDING_SIGNATURE: 'orange',
  SIGNED: 'blue',
  SUBMITTED_TO_DIAN: 'purple',
  ACCEPTED_BY_DIAN: 'green',
  REJECTED_BY_DIAN: 'red',
  CANCELLED: 'gray'
};
