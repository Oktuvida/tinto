# Quickstart: Tinto DIAN Billing App

## Goal
Validate a minimal end-to-end invoice issuance flow in dev and staging.

## Prerequisites
- DIAN habilitacion credentials and certificates
- Access to DIAN endpoints for dev and staging
- Master key for local access

## Steps
1. Configure environment values for dev.
2. Start backend and frontend containers.
3. Create an issuer profile and enter customer data.
4. Issue a basic invoice and capture the track id.
5. Poll invoice status until accepted or rejected.
6. Repeat steps 1-5 in staging.

## Expected Results
- A track id is returned for each issuance.
- Invoice status updates to accepted or rejected.
- Results are auditable with environment and test run identifiers.
