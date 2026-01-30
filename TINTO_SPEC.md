# ☕ Tinto — Technical Specification

**Tinto** es un motor de facturación electrónica DIAN (Colombia) diseñado como infraestructura sidecar. Funciona como un componente agnóstico, *stateless* en su lógica core y *stateful* en su persistencia.

## 1. Design Principles

*   **Local-first**: Diseñado para correr en infraestructura propia (on-premise, cloud, o localhost). No depende de SaaS de terceros.
*   **Sync/Async Hybrid**: Soporta emisión bloqueante (útil para scripting/debugging) y no bloqueante (esencial para high-throughput).
*   **Persistence Agnostic**: Interfaces de almacenamiento abstractas. SQLite por defecto (single-node), extensible a Postgres/Redis.
*   **Unix Philosophy**:  Herramientas pequeñas que hacen una cosa bien. CLI pipeable.

---

## 2. DIAN Compliance Requirements

### 2.1 Endpoints Oficiales

| Ambiente | URL |
|----------|-----|
| **Habilitación** | `https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc` |
| **Producción** | `https://vpfe.dian.gov.co/WcfDianCustomerServices.svc` |

### 2.2 Operaciones SOAP Soportadas

| Operación | Descripción | Prioridad |
|-----------|-------------|-----------|
| `SendBillSync` | Envío síncrono de documento | **Core** |
| `SendBillAsync` | Envío asíncrono (retorna TrackId) | **Core** |
| `GetStatus` | Consulta estado por CUFE | **Core** |
| `GetStatusZip` | Consulta estado por TrackId | **Core** |
| `SendTestSetAsync` | Envío de set de pruebas (habilitación) | v0.2 |
| `GetNumberingRange` | Consulta rangos de numeración autorizados | v0.3 |

### 2.3 Tipos de Documento Electrónico

| Código | Tipo | Soporte |
|--------|------|---------|
| `01` | Factura Electrónica de Venta | ✅ v0.1 |
| `02` | Factura de Exportación | 🔜 v1.1 |
| `03` | Factura de Contingencia (Facturador) | 🔜 v1.2 |
| `04` | Factura de Contingencia DIAN | 🔜 v1.2 |
| `91` | Nota Crédito | ✅ v0.1 |
| `92` | Nota Débito | ✅ v0.1 |

### 2.4 CUFE/CUDE Calculation

El Código Único de Factura Electrónica es obligatorio y se calcula como: 

```
CUFE = SHA-384(
  NumFac +      // Número de factura
  FecFac +      // Fecha de factura (YYYY-MM-DD)
  HorFac +      // Hora de factura (HH:MM: SS-05:00)
  ValFac +      // Valor antes de impuestos
  CodImp1 +     // Código impuesto 1 (01 = IVA)
  ValImp1 +     // Valor impuesto 1
  CodImp2 +     // Código impuesto 2 (04 = INC)
  ValImp2 +     // Valor impuesto 2
  CodImp3 +     // Código impuesto 3 (03 = ICA)
  ValImp3 +     // Valor impuesto 3
  ValTot +      // Valor total
  NitOFE +      // NIT Facturador (sin DV)
  NumAdq +      // NIT/CC Adquiriente
  ClTec +       // Clave técnica (asignada por DIAN)
  TipoAmbiente  // 1=Producción, 2=Habilitación
)
```

> **Nota**: Para Notas Crédito/Débito se usa **CUDE** con fórmula similar.

### 2.5 Formato de Envío

```
┌─────────────────────────────────────────┐
│            SOAP Envelope                │
│  ┌───────────────────────────────────┐  │
│  │         WS-Security Header        │  │
│  │   (Timestamp + BinarySecurityToken)│  │
│  └───────────────────────────────────┘  │
│  ┌───────────────────────────────────┐  │
│  │            SOAP Body              │  │
│  │  ┌─────────────────────────────┐  │  │
│  │  │    contentFile (Base64)     │  │  │
│  │  │  ┌───────────────────────┐  │  │  │
│  │  │  │     archivo.zip       │  │  │  │
│  │  │  │  ┌─────────────────┐  │  │  │  │
│  │  │  │  │ factura.xml     │  │  │  │  │
│  │  │  │  │ (UBL 2.1 firmado)│  │  │  │  │
│  │  │  │  └─────────────────┘  │  │  │  │
│  │  │  └───────────────────────┘  │  │  │
│  │  └─────────────────────────────┘  │  │
│  └───────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

---

## 3. Architecture Stack

### 3.1 Core Components

```
                    ┌─────────────────────────────────────┐
                    │            INGRESS LAYER            │
                    │  ┌─────┐  ┌─────┐  ┌─────────────┐  │
                    │  │ API │  │ CLI │  │ MCP Server  │  │
                    │  │REST │  │     │  │ (AI Agents) │  │
                    │  └──┬──┘  └──┬──┘  └──────┬──────┘  │
                    └─────┼───────┼────────────┼──────────┘
                          │       │            │
                          ▼       ▼            ▼
                    ┌─────────────────────────────────────┐
                    │           CORE ENGINE               │
                    │  ┌──────────────────────────────┐   │
                    │  │     Document Builder          │   │
                    │  │  (JSON → UBL 2.1 XML)        │   │
                    │  └──────────────┬───────────────┘   │
                    │                 ▼                   │
                    │  ┌──────────────────────────────┐   │
                    │  │     CUFE Calculator          │   │
                    │  │  (SHA-384 según spec DIAN)   │   │
                    │  └──────────────┬───────────────┘   │
                    │                 ▼                   │
                    │  ┌──────────────────────────────┐   │
                    │  │     XML Signer               │   │
                    │  │  (XAdES-BES / X.509)         │   │
                    │  └──────────────┬───────────────┘   │
                    │                 ▼                   │
                    │  ┌──────────────────────────────┐   │
                    │  │     ZIP Packager             │   │
                    │  │  (Compresión + Base64)       │   │
                    │  └──────────────────────────────┘   │
                    └─────────────────┬───────────────────┘
                                      ▼
                    ┌─────────────────────────────────────┐
                    │         TRANSPORT LAYER             │
                    │  ┌──────────────────────────────┐   │
                    │  │     DIAN SOAP Client         │   │
                    │  │  - WS-Security               │   │
                    │  │  - SendBillSync/Async        │   │
                    │  │  - GetStatus/GetStatusZip    │   │
                    │  └──────────────────────────────┘   │
                    └─────────────────┬───────────────────┘
                                      ▼
                    ┌─────────────────────────────────────┐
                    │         PERSISTENCE LAYER           │
                    │  ┌────────────┐  ┌──────────────┐   │
                    │  │  Metadata  │  │ Blob Storage │   │
                    │  │  (SQLite)  │  │ (Filesystem) │   │
                    │  └────────────┘  └──────────────┘   │
                    └─────────────────────────────────────┘
```

### 3.2 Module Responsibilities

| Módulo | Input | Output | Responsabilidad |
|--------|-------|--------|-----------------|
| **Document Builder** | JSON (invoice data) | UBL 2.1 XML | Mapeo de campos, validación de schema |
| **CUFE Calculator** | Datos fiscales | Hash SHA-384 | Cálculo determinístico del CUFE/CUDE |
| **XML Signer** | XML + Certificado | XML firmado | Firma XAdES-BES según política DIAN |
| **ZIP Packager** | XML firmado | Base64 string | Compresión y encoding para transporte |
| **DIAN Client** | Base64 ZIP | DIAN Response | Comunicación SOAP + WS-Security |
| **State Manager** | Events | State transitions | Máquina de estados del documento |

### 3.3 Persistence Layers

*   **Metadata**:  SQLite (archivo local) o PostgreSQL. 
*   **Blob Storage**:  Filesystem local (default) o S3-compatible interface (futuro) para XMLs y PDFs. 

---

## 4. Interfaces

### 4.1 API REST

#### `POST /v1/invoices`
Endpoint principal de emisión. 

*   **Headers**:
    *   `Content-Type: application/json`
    *   `Authorization: Bearer <API_KEY>`
    *   `X-Sync-Mode`: `true` (espera respuesta DIAN) | `false` (default, retorna 202).
*   **Request Body**:
    ```json
    {
      "document_type": "01",  // 01=Factura, 91=NC, 92=ND
      "prefix": "SETT",
      "number": 1234,
      "issue_date": "2026-01-21",
      "issue_time": "10:30:00-05:00",
      "currency": "COP",
      "supplier":  {
        "id": "900123456",
        "id_type": "31",  // 31=NIT
        "name": "Mi Empresa SAS",
        "tax_scheme": "ZZ",
        "address": { ...  }
      },
      "customer":  {
        "id": "1234567890",
        "id_type": "13",  // 13=Cédula
        "name":  "Juan Pérez",
        "email": "juan@email.com",
        "address": { ... }
      },
      "lines": [
        {
          "description": "Servicio de consultoría",
          "quantity": 1,
          "unit_price": 1000000,
          "tax_rate": 19,  // IVA 19%
          "tax_amount": 190000
        }
      ],
      "totals": {
        "line_extension": 1000000,
        "tax_exclusive": 1000000,
        "tax_inclusive": 1190000,
        "payable":  1190000
      }
    }
    ```
*   **Response (Async - Default)**:
    ```json
    HTTP/1.1 202 Accepted
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "track_id": "abc123.. .",  // Para consulta con GetStatusZip
      "status": "processing",
      "poll_url": "/v1/invoices/550e8400-e29b-41d4-a716-446655440000"
    }
    ```

#### `GET /v1/invoices/: id`
Consulta de estado (Polling).

*   **Response**:
    ```json
    HTTP/1.1 200 OK
    {
      "id":  "550e8400-.. .",
      "status": "accepted",
      "cufe": "a1b2c3d4e5f6.. .",
      "track_id":  "abc123...",
      "xml_url": "/v1/files/550e8400-. ../xml",
      "pdf_url": "/v1/files/550e8400-.../pdf",
      "dian_response": {
        "status_code": "00",
        "status_description":  "Procesado Correctamente",
        "status_message": "Documento validado por la DIAN",
        "xml_response": "..."  // ApplicationResponse XML (opcional)
      },
      "errors": []
    }
    ```

#### `GET /v1/invoices/:id/cufe`
Consulta estado directamente a DIAN por CUFE (proxy a `GetStatus`).

#### `POST /v1/numbering-ranges/sync`
Sincroniza rangos de numeración autorizados desde DIAN.

#### `GET /v1/health`
Liveness probe.  Verifica: 
- Conexión a DB
- Certificado digital válido y no expirado
- Conectividad con endpoint DIAN

### 4.2 CLI

```bash
# Emisión básica
tinto emit invoice. json --sync

# Pipes & Filters
cat invoice_data.json | tinto emit --format=json > result.json

# Consulta de estado
tinto status --id 550e8400-...  
tinto status --cufe a1b2c3d4... 

# Gestión de Certificados
tinto certs add --path ./cert.p12 --alias prod --password env:PFX_PASS
tinto certs list
tinto certs verify --alias prod

# Sincronizar rangos de numeración
tinto numbering sync --nit 900123456

# Diagnóstico
tinto diagnose network --env production
tinto diagnose cert --alias prod

# Set de pruebas (habilitación)
tinto test-set run --nit 900123456 --software-id xxx
```

### 4.3 MCP Server (Model Context Protocol)

Implementación del protocolo MCP para permitir que Agentes de IA (Claude, Cursor, Windsurf) interactúen nativamente con Tinto.

*   **Transport**: `stdio` (Standard Input/Output) sobre el binario o Docker.
*   **Role**: Exponer las capacidades de facturación como "Tools" para LLMs. 

#### Exposed Tools

| Tool | Description | Args |
|------|-------------|------|
| `tinto_emit_invoice` | Emite factura electrónica | `document_type`, `customer`, `items`, `async` |
| `tinto_emit_credit_note` | Emite nota crédito | `referenced_invoice`, `reason`, `items` |
| `tinto_get_status` | Consulta estado DIAN | `id` o `cufe` |
| `tinto_validate_json` | Valida estructura antes de emitir | `invoice_data` |
| `tinto_list_errors` | Errores recientes para debugging | `limit`, `since` |
| `tinto_explain_error` | Explica código de error DIAN | `error_code` |

#### Example Usage (AI Prompt)
> "Tinto, emite una factura para Juan Pérez (CC 123456) por $500. 000 + IVA de servicios de consultoría.  Si falla, explícame el error."

---

## 5. State Machine

```
                          ┌──────────────────────────────────────────┐
                          │                                          │
                          ▼                                          │
┌─────────┐  validate  ┌───────────┐  build+sign  ┌────────┐  send  ┌──────┐
│  DRAFT  │ ─────────▶ │ VALIDATED │ ───────────▶ │ SIGNED │ ─────▶│ SENT │
└─────────┘            └───────────┘              └────────┘        └──────┘
     │                       │                        │                 │
     │                       │                        │                 │
     ▼                       ▼                        ▼                 ▼
┌─────────┐            ┌─────────┐              ┌─────────┐      ┌──────────┐
│  ERROR  │◀───────────│  ERROR  │◀─────────────│  ERROR  │      │ ACCEPTED │
│(schema) │            │ (build) │              │ (sign)  │      └──────────┘
└─────────┘            └─────────┘              └─────────┘            │
                                                                       │
                            ┌──────────────────────────────────────────┘
                            │
                            ▼
                      ┌──────────┐
                      │ REJECTED │
                      │  (DIAN)  │
                      └──────────┘
```

| Estado | Descripción | Terminal |
|--------|-------------|----------|
| `draft` | Documento recibido, pendiente validación | No |
| `validated` | Schema JSON válido | No |
| `signed` | XML construido, CUFE calculado, firmado | No |
| `sent` | Enviado a DIAN, esperando respuesta | No |
| `accepted` | DIAN respondió código 00 | **Sí** |
| `rejected` | DIAN rechazó (códigos 02, 04, 99) | **Sí** |
| `error` | Error técnico (red, certificado, etc.) | Retriable |

---

## 6.  DIAN Response Codes

### Códigos Principales

| Código | Estado | Descripción |
|--------|--------|-------------|
| `00` | `accepted` | Procesado correctamente |
| `02` | `rejected` | Documento con errores |
| `04` | `rejected` | Documento duplicado (CUFE ya existe) |
| `66` | `pending` | Procesamiento asíncrono en curso |
| `99` | `rejected` | Error de validación de firma |

### Manejo de Errores Comunes

| Error DIAN | Causa | Acción Tinto |
|------------|-------|--------------|
| `FAD06` | CUFE inválido | Recalcular con datos corregidos |
| `FAJ32` | Fecha fuera de rango | Validar fecha ≤ 10 días |
| `FAJ42` | NIT no habilitado | Verificar proceso de habilitación |
| `FAK25` | Rango de numeración inválido | Sincronizar rangos |
| `ZZZ` | Error de firma | Verificar certificado |

---

## 7. Notification & Retrieval Strategy

### 7.1 Pull (Polling) - **Default**
El cliente consulta `GET /v1/invoices/:id` periódicamente. 
*   **Recomendado para**: Desarrollo local, servidores detrás de NAT/VPN, integraciones simples.
*   **Intervalo sugerido**:  Backoff exponencial (1s, 2s, 5s, 10s, 30s).

### 7.2 Push (Webhooks) - **Opcional**
Si se configura `WEBHOOK_URL`, Tinto realiza un POST ante cambios de estado terminales. 

```json
POST {WEBHOOK_URL}
{
  "event": "invoice.accepted",
  "id": "550e8400-.. .",
  "cufe": "a1b2c3d4.. .",
  "timestamp": "2026-01-21T10:35:00Z"
}
```

*   **Retry Logic**: 3 intentos con backoff exponencial si el endpoint falla.
*   **Signature**: Header `X-Tinto-Signature` con HMAC-SHA256 del payload.

---

## 8. Security Specs

### 8.1 Secret Management
*   Passwords de certificados **nunca** se persisten en DB.
*   Se inyectan vía `ENV VARS` al proceso en runtime.
*   Soporte para integración con Vault/AWS Secrets Manager (futuro).

### 8.2 API Security
*   Autenticación vía `Bearer Token` (API Key estática definida en entorno).
*   Rate limiting configurable.
*   CORS restringido configurable.

### 8.3 Certificado Digital
*   Formatos soportados: PKCS#12 (. p12, .pfx), PEM.
*   Validación automática de expiración.
*   Alertas configurables (30, 15, 7 días antes de vencimiento).

### 8.4 Audit
*   Logs estructurados (JSON) de todas las interacciones con DIAN.
*   Retención configurable para cumplimiento legal (5 años mínimo Colombia).

---

## 9. Configuration

### Environment Variables

```bash
# Core
TINTO_ENV=production|habilitacion
TINTO_API_PORT=8080
TINTO_API_KEY=your-secret-api-key

# DIAN
DIAN_SOFTWARE_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
DIAN_SOFTWARE_PIN=12345
DIAN_TEST_SET_ID=xxxxxxxx  # Solo para habilitación

# Certificate
CERT_PATH=/path/to/certificate.p12
CERT_PASSWORD=env:PFX_PASSWORD  # Referencia a otra env var

# Storage
DB_PATH=./tinto.db  # SQLite
STORAGE_PATH=./storage  # XMLs, PDFs

# Webhooks (opcional)
WEBHOOK_URL=https://your-app.com/webhooks/tinto
WEBHOOK_SECRET=your-webhook-secret
```

---

## 10. Development Roadmap

### v0.1 - Core Engine 🎯
- [ ] JSON Schema validation (invoice, credit note, debit note)
- [ ] UBL 2.1 XML Builder
- [ ] CUFE/CUDE Calculator (SHA-384)
- [ ] XAdES-BES Signer
- [ ] ZIP Packager
- [ ] Unit tests coverage > 90%

### v0.2 - DIAN Integration
- [ ] SOAP Client con WS-Security
- [ ] `SendBillSync` / `SendBillAsync`
- [ ] `GetStatus` / `GetStatusZip`
- [ ] Manejo de errores DIAN
- [ ] Integration tests contra ambiente de habilitación

### v0.3 - API & Persistence
- [ ] HTTP Server (REST API)
- [ ] SQLite persistence
- [ ] State machine implementation
- [ ] Polling endpoint
- [ ] Webhook notifications

### v0.4 - CLI & DevX
- [ ] CLI completo (`emit`, `status`, `certs`, `diagnose`)
- [ ] Modo interactivo para debugging
- [ ] Colorized output

### v1.0 - Production Ready 🚀
- [ ] Docker image (multi-arch)
- [ ] MCP Server para AI agents
- [ ] API documentation (OpenAPI 3.0)
- [ ] User guide
- [ ] `SendTestSetAsync` para proceso de habilitación

### v1.1+ - Future
- [ ] PostgreSQL support
- [ ] S3-compatible blob storage
- [ ] Factura de exportación
- [ ] Documento soporte
- [ ] Nómina electrónica

---

## 11. References

*   [Resolución 000042 de 2020 - Facturación Electrónica](https://www.dian.gov.co/normatividad/Normatividad/Resolución%20000042%20de%2005-05-2020.pdf)
*   [Anexo Técnico Factura Electrónica v1.9](https://www.dian.gov.co/impuestos/factura-electronica/Documents/Anexo_tecnico_factura_electronica_vr_1_9.pdf)
*   [Guía de Implementación DIAN](https://www.dian.gov.co/impuestos/factura-electronica/Documents/Guia_uso_facturacion_gratuita_DIAN. pdf)
*   [UBL 2.1 OASIS Standard](http://docs.oasis-open.org/ubl/UBL-2.1.html)
*   [XAdES Signatures - ETSI](https://www.etsi.org/deliver/etsi_ts/101900_101999/101903/01.04.02_60/ts_101903v010402p.pdf)