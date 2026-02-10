---

description: "Task list template for feature implementation"
---

# Tasks: Tinto DIAN Billing App

**Input**: Design documents from `/specs/001-dian-billing-app/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: The examples below include test tasks. E2E tests are REQUIRED for P1 user stories.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- **Web app**: `backend/src/`, `frontend/src/`
- **Mobile**: `api/src/`, `ios/src/` or `android/src/`
- Paths shown below assume single project - adjust based on plan.md structure

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Create project structure per implementation plan in backend/ and frontend/
- [x] T002 Initialize Spring Boot Kotlin project in backend/build.gradle.kts
- [x] T003 Initialize Angular workspace in frontend/angular.json
- [x] T004 [P] Configure Kotlin linting in backend/.editorconfig
- [x] T005 [P] Configure frontend linting in frontend/.eslintrc.json

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T006 Setup database migrations framework in backend/src/main/resources/db/migration/
- [x] T007 Implement encryption utilities in backend/src/main/kotlin/security/EncryptionService.kt
- [x] T008 Implement master key local access guard in backend/src/main/kotlin/security/MasterKeyGuard.kt
- [x] T009 Implement API key derivation and signature validation in backend/src/main/kotlin/security/ApiKeyAuthService.kt
- [x] T010 [P] Create API key role model in backend/src/main/kotlin/domain/auth/Role.kt
- [x] T011 Configure secure configuration loading in backend/src/main/kotlin/security/SecretsConfig.kt
- [x] T012 Setup DIAN SOAP client wrapper in backend/src/main/kotlin/services/dian/DianSoapClient.kt
- [x] T013 Configure DIAN environment endpoints in backend/src/main/kotlin/services/dian/DianEndpoints.kt
- [x] T014 Setup backend error handling in backend/src/main/kotlin/api/ErrorHandler.kt
- [x] T015 Setup frontend API client base in frontend/src/services/api-client.ts

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Emitir factura electronica basica (Priority: P1) 🎯 MVP

**Goal**: Issue a DIAN-compliant invoice from a simple UI and track initial status.

**Independent Test**: E2E in dev and staging: create and issue an invoice and verify DIAN processing response.

### Tests for User Story 1 (REQUIRED for P1) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T016 [P] [US1] E2E test for invoice issuance in backend/tests/e2e/invoice-issue.e2e.kt
- [ ] T017 [P] [US1] E2E test run in dev environment (report link) in specs/001-dian-billing-app/checklists/us1-e2e-dev.md
- [ ] T018 [P] [US1] E2E test run in staging environment (report link) in specs/001-dian-billing-app/checklists/us1-e2e-staging.md

### Implementation for User Story 1

- [x] T019 [P] [US1] Create issuer entity in backend/src/main/kotlin/domain/billing/Issuer.kt
- [x] T020 [P] [US1] Create customer entity in backend/src/main/kotlin/domain/billing/Customer.kt
- [x] T021 [P] [US1] Create invoice entity in backend/src/main/kotlin/domain/billing/Invoice.kt
- [x] T022 [P] [US1] Create line item entity in backend/src/main/kotlin/domain/billing/LineItem.kt
- [x] T023 [P] [US1] Create DIAN submission entity in backend/src/main/kotlin/domain/billing/DianSubmission.kt
- [x] T024 [US1] Implement invoice repository in backend/src/main/kotlin/repository/Repositories.kt
- [x] T025 [US1] Implement UBL XML builder in backend/src/main/kotlin/services/dian/UblXmlBuilder.kt
- [x] T026 [US1] Implement CUFE/CUDE calculator in backend/src/main/kotlin/services/dian/CufeCalculator.kt
- [x] T027 [US1] Implement XAdES-EPES signer in backend/src/main/kotlin/services/dian/XmlSigner.kt
- [x] T028 [US1] Implement ZIP packager in backend/src/main/kotlin/services/dian/ZipPackager.kt
- [x] T029 [US1] Implement DIAN send flow in backend/src/main/kotlin/services/dian/DianSubmissionService.kt
- [x] T030 [US1] Implement invoice issuance service in backend/src/main/kotlin/services/invoice/InvoiceService.kt
- [x] T031 [US1] Implement POST /v1/invoices in backend/src/main/kotlin/api/InvoiceController.kt
- [x] T032 [P] [US1] Create invoice form page in frontend/src/pages/invoices/new-invoice.component.ts
- [x] T033 [P] [US1] Create invoice review component in frontend/src/components/invoices/invoice-review.component.ts
- [x] T034 [US1] Wire invoice submit flow in frontend/src/services/invoice.service.ts
- [x] T035 [US1] Add validation and error handling in backend/src/main/kotlin/api/InvoiceController.kt

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Seguimiento de estados y errores DIAN (Priority: P2)

**Goal**: Show invoice status and DIAN error details to enable quick correction.

**Independent Test**: E2E in dev: issue invalid invoice, retrieve status with DIAN error details, verify user guidance.

### Implementation for User Story 2

- [x] T036 [US2] Implement status retrieval service in backend/src/main/kotlin/services/invoice/InvoiceStatusService.kt
- [x] T037 [US2] Implement GET /v1/invoices/{id} in backend/src/main/kotlin/api/InvoiceController.kt
- [x] T038 [P] [US2] Create invoice status page in frontend/src/pages/invoices/invoice-status.component.ts
- [x] T039 [US2] Map DIAN errors to user guidance in backend/src/main/kotlin/services/dian/DianErrorMapper.kt
- [x] T040 [US2] Render DIAN error guidance in frontend/src/components/invoices/invoice-errors.component.ts

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Integracion con asistentes de IA (MCP) (Priority: P3)

**Goal**: Allow AI-driven issuance and status queries via MCP integration.

**Independent Test**: E2E in dev: submit MCP request to issue an invoice and retrieve status.

### Implementation for User Story 3

- [ ] T041 [US3] Implement MCP server entrypoint in backend/src/main/kotlin/mcp/McpServer.kt
- [ ] T042 [US3] Implement MCP tool for invoice issuance in backend/src/main/kotlin/mcp/tools/EmitInvoiceTool.kt
- [ ] T043 [US3] Implement MCP tool for status query in backend/src/main/kotlin/mcp/tools/GetStatusTool.kt
- [ ] T044 [US3] Add MCP request authentication using API keys in backend/src/main/kotlin/mcp/McpAuth.kt

**Checkpoint**: All user stories should now be independently functional

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T045 [P] Documentation updates in docs/
- [ ] T046 Code cleanup and refactoring in backend/src/ and frontend/src/
- [ ] T047 Performance optimization across all stories in backend/src/ and frontend/src/
- [ ] T048 Security hardening review in backend/src/main/kotlin/security/
- [ ] T049 Run quickstart.md validation in specs/001-dian-billing-app/quickstart.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Uses US1 data but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - Uses US1 APIs but should be independently testable

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- P1 user stories MUST include E2E tests in dev and staging
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "E2E test for invoice issuance in backend/tests/e2e/invoice-issue.e2e.kt"
Task: "E2E test run in dev environment (report link) in specs/001-dian-billing-app/checklists/us1-e2e-dev.md"
Task: "E2E test run in staging environment (report link) in specs/001-dian-billing-app/checklists/us1-e2e-staging.md"

# Launch all models for User Story 1 together:
Task: "Create issuer entity in backend/src/main/kotlin/domain/billing/Issuer.kt"
Task: "Create customer entity in backend/src/main/kotlin/domain/billing/Customer.kt"
Task: "Create invoice entity in backend/src/main/kotlin/domain/billing/Invoice.kt"
Task: "Create line item entity in backend/src/main/kotlin/domain/billing/LineItem.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
