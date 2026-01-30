# Implementation Plan: Tinto DIAN Billing App

**Branch**: `001-dian-billing-app` | **Date**: 2026-01-29 | **Spec**: specs/001-dian-billing-app/spec.md
**Input**: Feature specification from `/specs/001-dian-billing-app/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Build a self-hosted DIAN electronic invoicing application with a simple UI, an API,
and MCP integration, acting as a bridge to DIAN SOAP services with strict security.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Kotlin (backend), Java 25 runtime
**Primary Dependencies**: Spring Boot 4.0.2, Gradle Kotlin DSL, Angular 21
**Storage**: PostgreSQL 18 (primary), encrypted file storage for documents
**Testing**: E2E suites in dev + staging (no mocks for business logic)
**Target Platform**: Linux containers (self-hosted)
**Project Type**: Web application (backend + frontend)
**Performance Goals**: Support typical SME invoicing throughput with responsive UX
**Constraints**: All data encrypted at rest, in transit, and secrets in config; master key local-only
**Scale/Scope**: Small-to-mid business deployments; core issuance + status + MCP

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Confirm E2E tests are planned per P1 user story (dev + staging).
- Verify no mocks are proposed for business logic acceptance.
- Ensure promotion gates rely on E2E results for dev -> staging -> prod.
- Document test data strategy and traceability for E2E runs.

Status: Pass

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
backend/
├── src/
│   ├── domain/
│   ├── services/
│   ├── api/
│   └── security/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/
```

**Structure Decision**: Web application with separate backend and frontend roots.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
