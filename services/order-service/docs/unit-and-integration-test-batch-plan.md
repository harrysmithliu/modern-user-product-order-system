# Unit and Integration Test Batch Plan for `order-service`

## Goal

Build a complete Java testing foundation for `order-service` in five incremental batches.

The plan is designed to cover the current order workflow with:

- `JUnit` for test structure and assertions
- `Mockito` for isolated unit tests
- `RestAssured` for HTTP API validation
- `JaCoCo` for coverage reporting and enforcement

Each batch should remain commit-friendly and independently verifiable.

## Why This Plan

`order-service` already contains business logic for order creation, payment, cancellation, approval, rejection, outbox persistence, and async event relay. The test stack should therefore be introduced in layers:

- establish the harness first
- cover business logic with mocks
- verify persistence behavior
- validate HTTP APIs end to end
- enforce coverage in the pipeline

This keeps the work easy to review and safe to merge batch by batch.

## Batch 1 - Java Test Foundation

### Goal

Set up the Java testing baseline so `order-service` can run unit and integration tests consistently, with clear execution boundaries between the two.

### Scope

- verify or add the Java test dependencies needed for `JUnit` and `Mockito`
- add `JaCoCo` to the Maven build
- establish Maven test-layer conventions:
  - unit and slice tests run in `test`
  - future integration tests run in `verify`
- introduce `Surefire` / `Failsafe` separation so API and database-heavy tests do not block fast local feedback
- create or standardize `src/test/java`
- add a minimal Spring web-slice test that proves the harness works without requiring a full external runtime
- confirm `mvn test` and `mvn verify` can run locally

### Output

- a working Java test harness
- a generated JaCoCo report
- a clear unit-vs-integration execution convention
- a stable baseline for later batches

## Batch 2 - Mockito Unit Coverage

### Goal

Use `Mockito` to cover the core order business logic in isolation.

### Scope

- create service-level unit tests for order creation, payment, cancellation, approval, and rejection
- mock repository, client, publisher, lock manager, and external workflow collaborators
- cover happy paths and failure paths
- include idempotency, validation, duplicate-request fallback, and state transition checks

### Output

- deterministic service unit tests
- mocked dependencies for business-rule isolation
- faster feedback on core order behavior

## Batch 3 - Persistence and Transaction Integration

### Goal

Verify that the order workflow persists the right data and transaction boundaries behave correctly.

### Scope

- add repository tests for `OrderRepository`, `OrderOutboxRepository`, and related persistence objects
- verify order state transitions against the database
- verify outbox record creation inside the same transaction
- cover rollback or compensation behavior where it matters
- run the persistence tests against an isolated in-memory test database

### Output

- repository and transaction-level integration tests
- confidence that the core order write path is consistent
- stronger validation of database-backed workflow behavior
- repeatable `verify` coverage without depending on an external MySQL instance

## Batch 4 - RestAssured API Coverage

### Goal

Validate the external HTTP contract with `RestAssured`.

### Scope

- add API-level integration tests for order creation, payment, cancellation, approval, and rejection
- assert HTTP status codes, response payloads, and critical business fields
- validate the admin and user order flows through the actual web layer
- pass the request-user headers exactly as the production controller contract expects

### Output

- HTTP contract tests for the main order endpoints
- repeatable API checks against a test-started Spring Boot application
- evidence that the public order surface works end to end
- executable examples of the current user/admin header-based request contract

## Batch 5 - JaCoCo Enforcement

### Goal

Turn coverage reporting into a build quality gate.

### Scope

- configure JaCoCo coverage thresholds
- fail the build if coverage falls below the agreed minimum
- wire the coverage step into CI if needed
- keep the thresholds realistic for the current codebase and batch history
- use an initial bundle-level gate that matches the current test maturity:
  - `LINE >= 55%`
  - `METHOD >= 65%`
  - keep branch coverage visible in reports, but do not gate on it yet

### Output

- coverage reporting
- coverage verification in `verify`
- a CI-friendly quality gate for future changes
- a documented first-step threshold baseline that can be raised in later batches

## Acceptance Criteria

This plan is complete when all of the following are true:

- `mvn test` passes for `services/order-service`
- `mvn verify` passes for `services/order-service`
- `JaCoCo` generates a usable coverage report
- `Mockito` is used in order-service unit tests
- `RestAssured` is used in order-service API tests
- the tests cover meaningful order flows instead of only trivial startup checks

## Relation To JD

If these five batches are completed as planned, then the `order-service` side of the JD requirement is effectively covered for:

- `JUnit`
- `Mockito`
- `RestAssured`
- `JaCoCo`

That means the Java testing part of the requirement is in place, but there are two important caveats:

- this plan covers `order-service` only, not the Python services
- the JD may still require additional breadth such as Python `pytest`, broader system integration tests, or rule-engine work like `Drools`

So the honest answer is: yes, these five batches can fully cover the Java testing stack named in the JD for `order-service`, but they do not by themselves guarantee the entire JD is fully satisfied across the whole repository.
