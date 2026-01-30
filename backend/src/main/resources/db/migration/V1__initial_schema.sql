-- Tinto DIAN Billing App - Initial Schema
-- Migration: V1__initial_schema.sql
-- Description: Create core tables for invoice management, DIAN submissions, and security

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Environments table (dev, staging, production)
CREATE TABLE environments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(50) NOT NULL UNIQUE,
    dian_endpoint VARCHAR(255) NOT NULL,
    is_production BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Master access keys (encrypted, local-only access)
CREATE TABLE master_access_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key_hash VARCHAR(128) NOT NULL UNIQUE, -- SHA-512 hash
    encrypted_key_data TEXT NOT NULL, -- Encrypted with system key
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- API key roles (Admin, Operator, Auditor)
CREATE TYPE api_key_role AS ENUM ('ADMIN', 'OPERATOR', 'AUDITOR');

-- API keys (derived from master key)
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    role api_key_role NOT NULL,
    key_hash VARCHAR(128) NOT NULL UNIQUE, -- SHA-512 hash of derived key
    encrypted_key_data TEXT NOT NULL, -- Encrypted storage
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES master_access_keys(id)
);

-- Issuers (companies issuing invoices)
CREATE TABLE issuers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    nit VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    trade_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    department VARCHAR(100),
    country VARCHAR(2) DEFAULT 'CO',
    tax_regime VARCHAR(50),
    -- DIAN certificate info (encrypted)
    certificate_data TEXT, -- Encrypted X.509 certificate
    certificate_password_encrypted TEXT,
    certificate_expires_at TIMESTAMP,
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Customers (invoice recipients)
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    identification_type VARCHAR(20) NOT NULL, -- NIT, CC, CE, etc.
    identification_number VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    city VARCHAR(100),
    department VARCHAR(100),
    country VARCHAR(2) DEFAULT 'CO',
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(identification_type, identification_number)
);

-- Invoice statuses
CREATE TYPE invoice_status AS ENUM (
    'DRAFT',
    'PENDING_SIGNATURE',
    'SIGNED',
    'SUBMITTED_TO_DIAN',
    'ACCEPTED_BY_DIAN',
    'REJECTED_BY_DIAN',
    'CANCELLED'
);

-- Invoices
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- Invoice identification
    prefix VARCHAR(10),
    number BIGINT NOT NULL,
    full_number VARCHAR(20) GENERATED ALWAYS AS (COALESCE(prefix || '-', '') || number) STORED,
    -- Relationships
    issuer_id UUID NOT NULL REFERENCES issuers(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    environment_id UUID NOT NULL REFERENCES environments(id),
    -- Invoice data
    issue_date DATE NOT NULL,
    due_date DATE,
    currency VARCHAR(3) DEFAULT 'COP',
    -- Amounts (stored as integers in cents/centavos)
    subtotal BIGINT NOT NULL, -- Before taxes
    tax_amount BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    -- CUFE (Código Único de Factura Electrónica)
    cufe VARCHAR(96), -- SHA-384 hash
    -- Status
    status invoice_status NOT NULL DEFAULT 'DRAFT',
    -- XML data (encrypted at rest)
    ubl_xml_encrypted TEXT, -- Encrypted UBL XML
    signed_xml_encrypted TEXT, -- Encrypted signed XML
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES api_keys(id),
    UNIQUE(issuer_id, prefix, number)
);

-- Line items (invoice products/services)
CREATE TABLE line_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    line_number INT NOT NULL,
    -- Product/Service info
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(12, 4) NOT NULL,
    unit_price BIGINT NOT NULL, -- In cents/centavos
    -- Amounts
    line_total BIGINT NOT NULL,
    tax_rate NUMERIC(5, 2), -- Percentage (e.g., 19.00 for 19%)
    tax_amount BIGINT,
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(invoice_id, line_number)
);

-- DIAN submission statuses
CREATE TYPE dian_submission_status AS ENUM (
    'PENDING',
    'SUBMITTED',
    'PROCESSING',
    'ACCEPTED',
    'REJECTED',
    'ERROR'
);

-- DIAN submissions (track async submission process)
CREATE TABLE dian_submissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    environment_id UUID NOT NULL REFERENCES environments(id),
    -- DIAN tracking
    track_id VARCHAR(100), -- From SendBillAsync response
    status dian_submission_status NOT NULL DEFAULT 'PENDING',
    -- Request/Response data (encrypted)
    zip_file_encrypted TEXT, -- Encrypted ZIP file sent to DIAN
    dian_response_encrypted TEXT, -- Encrypted DIAN ApplicationResponse XML
    error_code VARCHAR(50),
    error_message TEXT,
    -- Timestamps
    submitted_at TIMESTAMP,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Request signatures (for API authentication)
CREATE TABLE request_signatures (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    api_key_id UUID NOT NULL REFERENCES api_keys(id),
    signature_hash VARCHAR(128) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    request_timestamp TIMESTAMP NOT NULL,
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Prevent replay attacks: signature + timestamp must be unique
    UNIQUE(signature_hash, request_timestamp)
);

-- Indexes for performance
CREATE INDEX idx_invoices_issuer ON invoices(issuer_id);
CREATE INDEX idx_invoices_customer ON invoices(customer_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_issue_date ON invoices(issue_date);
CREATE INDEX idx_invoices_cufe ON invoices(cufe);
CREATE INDEX idx_line_items_invoice ON line_items(invoice_id);
CREATE INDEX idx_dian_submissions_invoice ON dian_submissions(invoice_id);
CREATE INDEX idx_dian_submissions_status ON dian_submissions(status);
CREATE INDEX idx_api_keys_active ON api_keys(is_active);
CREATE INDEX idx_request_signatures_api_key ON request_signatures(api_key_id);
CREATE INDEX idx_request_signatures_timestamp ON request_signatures(request_timestamp);

-- Insert default environment (Habilitación - DIAN test environment)
INSERT INTO environments (name, dian_endpoint, is_production) VALUES
    ('habilitacion', 'https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc?wsdl', FALSE),
    ('produccion', 'https://vpfe.dian.gov.co/WcfDianCustomerServices.svc?wsdl', TRUE);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_issuers_updated_at BEFORE UPDATE ON issuers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_customers_updated_at BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_invoices_updated_at BEFORE UPDATE ON invoices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_dian_submissions_updated_at BEFORE UPDATE ON dian_submissions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
