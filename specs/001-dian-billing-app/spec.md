# Feature Specification: Tinto DIAN Billing App

**Feature Branch**: `001-dian-billing-app`  
**Created**: 2026-01-29  
**Status**: Draft  
**Input**: User description: "Tinto será una aplicación de facturación en Colombia self-hosted que a través de contenedores monta un backend y un frontend siguiendo estándares de UI & UX modernos y user-friendly (este último es el pilar, debe ser muy sencillo de usar). Este también debería ser integrado fácilmente en IAs (MCP). Esta aplicación debe ser un puente con las APIs oficiales de la DIAN, véase www.dian.gov.co/impuestos/factura-electronica/Documents/Abece-FE-Facturador.pdf y aparentemente hay documentación de algunos servicios usando SOAP UI (https://www.dian.gov.co/impuestos/factura-electronica/Documents/Guia-Herramienta-para-el-Consumo-de-Web-Services.pdf). Ya hay un spec borrador de la aplicación, guíate del archivo @TINTO_SPEC.md."

## Clarifications

### Session 2026-01-29

- Q: Should the system encrypt all stored information and use a master key for access? → A: Yes, encrypt everything stored and start with master key authentication (separate from DIAN auth).
- Q: How should authentication work for local access vs API usage? → A: Master key for local access only; API access uses keys derived from the master key with payload-signed requests and role-based permissions.
- Q: What level of encryption coverage is required? → A: Encrypt data at rest, encrypt data in transit, and encrypt secrets in configuration.
- Q: Which API key roles should be supported? → A: Admin, operator, and auditor roles.
- Q: Should accounting features be included in scope? → A: Exclude accounting features (ledger and tax filing).
- Q: What qualifies as local access for the master key? → A: Direct host/console access only, no network.

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - Emitir factura electronica basica (Priority: P1)

Como operador de facturacion en Colombia, quiero emitir una factura electronica
valida ante DIAN desde una interfaz web simple, para cumplir con la obligacion
legal sin friccion tecnica.

**Why this priority**: La emision valida es el flujo principal y habilita el valor del producto.

**Independent Test**: E2E en dev y staging: crear una factura con datos reales de prueba,
emitirla, y verificar respuesta aceptada o en proceso por DIAN.

**Acceptance Scenarios**:

1. **Given** un usuario con credenciales validas y datos del emisor configurados,
   **When** completa el formulario de factura y confirma emision,
   **Then** el sistema registra el envio y muestra un estado de procesamiento.
2. **Given** una factura emitida con exito,
   **When** el usuario consulta el estado,
   **Then** ve el resultado oficial de DIAN con identificador verificable.

---

### User Story 2 - Seguimiento de estados y errores DIAN (Priority: P2)

Como operador, quiero ver el estado y los errores de mis documentos para
corregirlos rapidamente y mantener continuidad operativa.

**Why this priority**: Reduce reprocesos y permite operar sin soporte tecnico.

**Independent Test**: E2E en dev: emitir factura con error de validacion y
ver el detalle; repetir en staging con respuesta oficial de DIAN.

**Acceptance Scenarios**:

1. **Given** una factura rechazada por DIAN,
   **When** el usuario abre el detalle del documento,
   **Then** ve el motivo de rechazo y pasos sugeridos para corregirlo.

---

### User Story 3 - Integracion con asistentes de IA (MCP) (Priority: P3)

Como integrador, quiero usar una interfaz de IA para emitir y consultar
documentos de manera sencilla, para automatizar procesos de facturacion.

**Why this priority**: Amplia casos de uso y facilita integraciones en flujos modernos.

**Independent Test**: E2E en dev: ejecutar una accion de IA simulada para
emitir y luego consultar el estado del documento.

**Acceptance Scenarios**:

1. **Given** una integracion MCP configurada,
   **When** el agente solicita emitir una factura con datos validos,
   **Then** el sistema crea el documento y retorna identificadores de seguimiento.

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- Que ocurre cuando la DIAN no responde o hay caidas temporales del servicio.
- Que ocurre cuando los datos del cliente estan incompletos o invalidos.
- Que ocurre cuando se intenta emitir un documento duplicado.
- Que ocurre si el usuario pierde conectividad durante la emision.

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST allow users to create and issue electronic invoices compliant with DIAN rules.
- **FR-002**: System MUST provide a simple web flow to enter issuer, customer, and line items and review before issuing.
- **FR-003**: System MUST provide a server component for invoice issuance and status retrieval.
- **FR-004**: System MUST support a self-hosted deployment with separate user interface and server components.
- **FR-005**: System MUST integrate with official DIAN services for document submission and status checks.
- **FR-006**: System MUST store invoice status history and allow users to view it.
- **FR-007**: System MUST surface DIAN errors and guidance in user-friendly language.
- **FR-008**: System MUST offer an integration surface for AI agents via MCP.
- **FR-009**: System MUST ensure data is handled in compliance with Colombian invoicing regulations.
- **FR-010**: System MUST encrypt all stored information due to sensitive data handling.
- **FR-011**: System MUST require a master access key for local access only (separate from DIAN auth).
- **FR-012**: System MUST issue API keys derived from the master key and require payload-signed requests.
- **FR-013**: System MUST enforce role-based permissions for API keys.
- **FR-014**: System MUST encrypt data in transit between components and clients.
- **FR-015**: System MUST encrypt secrets stored in configuration.
- **FR-016**: System MUST support admin, operator, and auditor roles for API keys.
- **FR-017**: System MUST restrict master key usage to direct host/console access only.

### Key Entities *(include if feature involves data)*

- **Issuer**: Company identity, tax ID, and configuration needed to issue invoices.
- **Customer**: Buyer identity, tax ID, contact data, and billing address.
- **Invoice**: Commercial document with totals, line items, and issuance metadata.
- **Line Item**: Product or service line with quantity, price, and tax category.
- **DIAN Submission**: Record of a submission attempt, response codes, and timestamps.
- **Environment**: Dev, staging, or production context tied to tests and submissions.
- **AI Integration Request**: Invocation metadata for MCP-driven actions.
- **Master Access Key**: Credential required to access the system.
- **API Key**: Derived credential for API access with assigned role.
- **Role**: Permission set that governs API key capabilities.
- **Admin**: Role with full operational access including configuration.
- **Operator**: Role for issuing and managing invoices.
- **Auditor**: Role for read-only access and compliance views.
- **Request Signature**: Proof that an API request payload is authorized.

### Acceptance Criteria

- **FR-001**: Users can issue a DIAN-valid electronic invoice end-to-end.
- **FR-002**: Users can complete issuance using a guided, low-friction form.
- **FR-003**: Users can request issuance and later retrieve status results.
- **FR-004**: The product runs fully in a self-hosted setup with UI and server parts.
- **FR-005**: Official DIAN responses are retrieved and displayed to users.
- **FR-006**: Users can view the full status history per invoice.
- **FR-007**: Users see DIAN error details in clear, actionable language.
- **FR-008**: AI integrations can issue and query documents successfully.
- **FR-009**: Handling of invoice data aligns with applicable Colombian regulations.
- **FR-010**: Stored invoice data is protected by encryption.
- **FR-011**: Local access requires a master access key.
- **FR-012**: API requests include a valid payload signature tied to an API key.
- **FR-013**: API operations are allowed only when permitted by the key's role.
- **FR-014**: Data exchanged between components and clients is encrypted in transit.
- **FR-015**: Secrets stored in configuration are encrypted.
- **FR-016**: API keys can be assigned admin, operator, or auditor permissions.
- **FR-017**: Master key usage is limited to direct host or console access.

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: 90% of first-time users can issue a basic invoice within 5 minutes.
- **SC-002**: 95% of issued invoices reach a terminal DIAN status without manual support.
- **SC-003**: Average time to understand and resolve DIAN errors is under 10 minutes.
- **SC-004**: AI-driven issuance succeeds end-to-end in at least 80% of attempts.

## Dependencies

- Access to DIAN certification and production environments for submission and status checks.
- Valid issuer credentials and certificates provided by the deploying organization.

## Assumptions

- The initial release focuses on core invoice issuance and credit/debit notes.
- Primary users are small-to-mid businesses seeking a simple self-hosted option.
- Users have access to valid DIAN credentials and certificates.
- The UI emphasizes simplicity over advanced configuration.
- Accounting features such as ledger management and tax filing are out of scope.
