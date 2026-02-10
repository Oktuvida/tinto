<!--
Sync Impact Report
- Version change: 0.1.0 -> 0.2.0
- Modified principles: [PRINCIPLE_1_NAME] -> I. E2E-First Development
  [PRINCIPLE_2_NAME] -> II. Environment Parity, No Mocks
  [PRINCIPLE_3_NAME] -> III. Business Logic as Acceptance Criteria
  [PRINCIPLE_4_NAME] -> IV. Promotion Gates by E2E Results
  [PRINCIPLE_5_NAME] -> V. Test Data Safety and Traceability
  [PRINCIPLE_6_NAME] -> VI. Reproducible, Declarative, Container-First (NEW)
- Added sections: Principle VI
- Removed sections: none
- Templates requiring updates: ✅ .specify/templates/plan-template.md
  ✅ .specify/templates/spec-template.md
  ✅ .specify/templates/tasks-template.md
  ✅ .specify/templates/checklist-template.md
  ⚠ pending: .specify/templates/commands/*.md (directory missing)
- Follow-up TODOs: TODO(RATIFICATION_DATE): original adoption date unknown
-->
# tinto Constitution

## Core Principles

### I. E2E-First Development
All changes MUST be driven by end-to-end tests that cover full user journeys.
E2E tests MUST be written or updated before implementation and must fail
before code changes. Rationale: this enforces business outcomes and prevents
false confidence from narrow tests.

### II. Environment Parity, No Mocks
Tests MUST run against real services in dev and staging environments; mocks
and fakes are prohibited for business logic validation. Environments MUST be
kept as close as possible to production (configs, data shape, integrations).
Rationale: parity avoids regression gaps between test and production behavior.

### III. Business Logic as Acceptance Criteria
Acceptance scenarios MUST reflect business rules and be executable as E2E
tests. Each user story MUST define at least one independent E2E scenario that
verifies business logic in the target environment. Rationale: success is
measured by business behavior, not internal implementation.

### IV. Promotion Gates by E2E Results
Promotion between dev -> staging -> prod MUST require passing E2E suites for
the affected user stories. Failing suites block promotion until resolved.
Rationale: release confidence depends on validated end-to-end behavior.

### V. Test Data Safety and Traceability
E2E tests MUST use controlled data with clear provenance. Test runs MUST leave
auditable traces (test identifiers, environment, and version under test) to
allow reproduction. Rationale: safe data handling protects environments while
keeping results trustworthy.

### VI. Reproducible, Declarative, Container-First
All environments, including local development, MUST be defined declaratively
and run inside containers. Infrastructure and service dependencies MUST be
expressed as code (e.g., Compose files, Containerfiles) so that any developer
can reproduce the full stack with a single command. Host-installed runtimes or
databases are prohibited as primary dev targets; the container definition is
the source of truth. Rationale: reproducibility eliminates "works on my
machine" failures and ensures every environment is auditable, portable, and
identical from dev through production.

## Testing & Environments

- E2E suites MUST exist for each P1 user story and run in dev and staging.
- Staging MUST mirror production integrations and configuration defaults.
- Production validation is by promotion gates, not by ad-hoc manual testing.
- Mocking external systems is allowed only for non-business behavior (e.g.,
  resilience drills) and MUST be documented as not qualifying for acceptance.

## Development Workflow & Quality Gates

- Each change MUST include updated E2E coverage or a documented justification.
- Code review MUST verify: scenarios exist, run in dev/staging, and fail/pass
  sequence is demonstrated.
- Releases MUST include an E2E report linking test runs to the deployed build.

## Governance

- This constitution supersedes all other project practices.
- Amendments require a documented proposal, rationale, and migration plan.
- Versioning follows semantic rules: MAJOR for incompatible changes, MINOR for
  new or expanded principles, PATCH for clarifications only.
- Compliance reviews MUST occur in every plan/spec review and before release.
- Runtime guidance lives in `.specify/templates/` and MUST stay aligned.

**Version**: 0.2.0 | **Ratified**: TODO(RATIFICATION_DATE): original adoption date unknown | **Last Amended**: 2026-02-10
