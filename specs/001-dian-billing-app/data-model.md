# Data Model: Tinto DIAN Billing App

## Entities

### Issuer
- id
- tax_id
- tax_regime
- legal_name
- address
- contact_email

### Customer
- id
- tax_id
- id_type
- legal_name
- email
- address

### Invoice
- id
- issuer_id
- customer_id
- document_type
- prefix
- number
- issue_date
- issue_time
- currency
- totals
- status
- cufe_or_cude
- created_at
- updated_at

### Line Item
- id
- invoice_id
- description
- quantity
- unit_price
- tax_rate
- tax_amount
- line_total

### DIAN Submission
- id
- invoice_id
- track_id
- zip_key
- status
- status_message
- response_payload_ref
- created_at
- updated_at

### API Key
- id
- role
- derived_from_master_key
- created_at
- last_used_at
- status

### Request Signature
- id
- api_key_id
- payload_hash
- created_at

### Master Access Key
- id
- created_at
- status

### Environment
- id
- name (dev, staging, production)
- dian_endpoint

## Relationships

- Issuer has many Invoices.
- Customer has many Invoices.
- Invoice has many Line Items.
- Invoice has many DIAN Submissions.
- API Key has many Request Signatures.
- Invoice belongs to Environment.

## Validation Rules

- Invoice totals must match sum of line items and taxes.
- Issue date/time must be within DIAN-allowed window.
- Document type must be one of supported DIAN codes.
- CUFE/CUDE must be deterministic for a given invoice.

## State Transitions

- draft -> validated -> signed -> sent -> accepted
- draft -> validated -> signed -> sent -> rejected
- any -> error (retryable)
