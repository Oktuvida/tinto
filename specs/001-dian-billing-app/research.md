# Research: Tinto DIAN Billing App

**Date**: 2026-01-29
**Scope**: Technology choices and DIAN integration constraints

## Decisions

### Decision 1: Core stack alignment
**Decision**: Use Spring Boot 4.0.2 with Kotlin and Gradle Kotlin DSL, Java 25 runtime; Angular 21 for UI; PostgreSQL 18 for persistence.
**Rationale**: Matches stated stack requirements and supports SOAP/XML workflows and strong typing.
**Alternatives considered**: Java-only Spring Boot, Quarkus, Micronaut, Node/Go backend; MySQL or SQL Server.

### Decision 2: DIAN SOAP integration flow
**Decision**: Implement SOAP 1.2 over HTTPS with WS-Security UsernameToken; build UBL 2.1 XML, sign with XAdES-EPES, ZIP payloads, and use SendBillAsync + GetStatusZip polling.
**Rationale**: Required by DIAN protocol and `TECH_SPECS.md` guidance.
**Alternatives considered**: Proxy through a certified DIAN provider to offload SOAP/signing.

### Decision 3: XML signing and CUFE/CUDE
**Decision**: Generate CUFE/CUDE with SHA-384 and sign XML with RSA-SHA256 XAdES-EPES enveloped signatures using X.509 certificates.
**Rationale**: Explicit DIAN compliance requirement and a critical validation step.
**Alternatives considered**: External signing service or HSM-backed signing.

### Decision 4: Encryption coverage and secrets handling
**Decision**: Encrypt data at rest and in transit; encrypt secrets in configuration; master key only for direct host/console access.
**Rationale**: Security clarifications and sensitivity of invoice data.
**Alternatives considered**: Rely on DB-level encryption only; external secret manager only.

### Decision 5: API key model and roles
**Decision**: Derive API keys from the master key; require payload signatures; roles are admin, operator, and auditor.
**Rationale**: Aligns with clarified auth model and least-privilege access.
**Alternatives considered**: OAuth/OIDC or JWT-only without payload signatures.

### Decision 6: As-Code configuration
**Decision**: Use database migrations and configuration as code with encrypted secrets.
**Rationale**: Repeatable self-hosted deployment and compliance requirements.
**Alternatives considered**: Manual DB scripts; runtime-only configuration.

### Decision 7: Environment separation
**Decision**: Treat DIAN habilitacion and produccion as distinct environments with strict endpoint separation and validation rules.
**Rationale**: Different WSDL endpoints and compliance requirements.
**Alternatives considered**: Single environment flags without enforced separation.
