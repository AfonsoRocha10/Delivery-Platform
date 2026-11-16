# Decisions

A log of the technical decisions made in this project: what was chosen,
what else was considered and why. New decisions are added at the end;
decisions that are later changed are marked as superseded rather than
deleted.

| # | Decision | Status |
| - | -------- | ------ |
| 1 | [Java 25 LTS and Spring Boot 4.1.2](#1-java-25-lts-and-spring-boot-412) | Accepted |
| 2 | [Commit the Maven Wrapper](#2-commit-the-maven-wrapper) | Accepted |
| 3 | [Enforce LF line endings for scripts](#3-enforce-lf-line-endings-for-scripts) | Accepted |
| 4 | [Ignore secrets and state files from the start](#4-ignore-secrets-and-state-files-from-the-start) | Accepted |
| 5 | [YAML configuration](#5-yaml-configuration) | Accepted |
| 6 | [Expose only the health Actuator endpoint](#6-expose-only-the-health-actuator-endpoint) | Accepted |
| 7 | [In-memory storage in the first version](#7-in-memory-storage-in-the-first-version) | Accepted; replacement planned for Phase 2 |
| 8 | [RFC 9457 problem details for errors](#8-rfc-9457-problem-details-for-errors) | Accepted |
| 9 | [Write tests together with the code](#9-write-tests-together-with-the-code) | Accepted |
| 10 | [Package by feature, flat](#10-package-by-feature-flat) | Accepted |
| 11 | [Health check over real HTTP in tests](#11-health-check-over-real-http-in-tests) | Accepted |
| 12 | [Defer loading Mockito as a Java agent](#12-defer-loading-mockito-as-a-java-agent) | Deferred |

---

## 1. Java 25 LTS and Spring Boot 4.1.2

**Context:** A new project with no legacy constraints.

**Decision:** Java 25, the current long-term-support release, and Spring
Boot 4.1.2, the latest stable release.

**Alternatives:** Java 21 LTS (older, but more widely deployed); Spring
Boot 4.2 (still a release candidate at the time).

**Consequences:** Longest support window and current APIs. Build agents and
container base images must provide Java 25.

## 2. Commit the Maven Wrapper

**Context:** The project will be built on the developer machine, on
Jenkins and inside Docker.

**Decision:** Commit `mvnw`, `mvnw.cmd` and `.mvn/wrapper/`, pinning
Maven 3.10.0.

**Alternatives:** Require Maven to be installed on every machine.

**Consequences:** Every environment uses the same Maven version and only
needs a JDK. The first build downloads Maven once.

## 3. Enforce LF line endings for scripts

**Context:** Development happens on Windows; builds will run on Linux
(Jenkins agents, Docker, VMs). A shell script checked out with Windows
(CRLF) line endings fails on Linux with `/bin/sh^M: bad interpreter`.

**Decision:** A root `.gitattributes` forces LF for `mvnw` and `*.sh`.

**Consequences:** Scripts work on Linux regardless of the developer's Git
settings.

## 4. Ignore secrets and state files from the start

**Context:** Later phases introduce Terraform state, Ansible vault files
and credentials.

**Decision:** `.gitignore` already excludes build output, IDE files,
`.env` and key files, Terraform state and Ansible vault files.

**Consequences:** Accidental commits of secrets are prevented before those
tools are even added. Automated secret scanning in CI is still planned
(Phase 7).

## 5. YAML configuration

**Decision:** `application.yaml` instead of `application.properties`.

**Reason:** The same format as Docker Compose, Ansible, Prometheus and
Kubernetes configuration used later, and easier to read for nested
settings.

## 6. Expose only the health Actuator endpoint

**Context:** Spring Boot Actuator offers endpoints such as `env`, `beans`
and `configprops` that reveal internal details.

**Decision:** Only `health` is exposed over HTTP. Liveness and readiness
groups (`/actuator/health/liveness`, `/actuator/health/readiness`) are
enabled by default in Spring Boot 4.

**Consequences:** Nothing internal leaks. The `prometheus` endpoint will
be added deliberately when monitoring is introduced. A test fails if other
endpoints become exposed (see [11](#11-health-check-over-real-http-in-tests)).

## 7. In-memory storage in the first version

**Context:** The focus of the project is the delivery pipeline, not the
application itself.

**Decision:** Deliveries are stored in a `ConcurrentHashMap`. Status
updates use `computeIfPresent`, so concurrent updates of the same delivery
cannot overwrite each other.

**Alternatives:** PostgreSQL from the start (adds a second service to
build, deploy, back up and monitor); an embedded database such as H2.

**Consequences:** Data is lost on restart, and only one instance can run.

**Planned replacement (start of Phase 2, before the Dockerfile and
Compose file):**

- **PostgreSQL** with Spring Data JPA replacing the in-memory repository.
  Concurrent status updates are protected by optimistic locking (a
  version column).
- **Flyway migrations** for the schema, instead of Hibernate generating
  tables. Schema changes become versioned files deployed by the pipeline,
  and must stay compatible with the previous application version so that
  rollback (Phase 3) keeps working.
- **A demo dataset**, separate from the schema: a few hundred realistic
  deliveries across all statuses, loaded only when a `demo` profile is
  active, so sample data never reaches a production-like environment by
  accident.
- **Testcontainers** for database tests, so tests run against a real
  PostgreSQL rather than an in-memory substitute such as H2, whose
  behaviour differs.

**Why then:** Docker Compose (Phase 2) is the natural place to run the
application with its database; adding it earlier would load the first CI
setup with database concerns, adding it later would mean writing the
Compose setup twice. The database also gives later phases real work:
a stateful service to configure (Ansible), a real secret to protect
(security), and data to chart (monitoring).

**Cost:** One extra sub-step, and a slower build because tests start
PostgreSQL; the new build time will be measured against the
[baseline](deployment.md#baseline).

## 8. RFC 9457 problem details for errors

**Decision:** All errors are returned as `application/problem+json`
(`ProblemDetail`), including framework errors such as malformed JSON.
Validation errors add an `errors` map of field → message.

**Reason:** A standard, consistent error format that clients can rely on,
instead of each error having its own shape.

## 9. Write tests together with the code

**Decision:** Each change includes the tests that cover it; tests use real
objects rather than mocks.

**Alternatives:** Add tests after the features are complete.

**Consequences:** Every commit builds and passes, which the CI pipeline
will enforce. Tests do not assert validation message text, because
messages are localised to the JVM locale.

## 10. Package by feature, flat

**Context:** The `delivery` feature has eight classes.

**Decision:** Keep them in one package rather than splitting into
`controller/`, `service/` and `repository/` sub-packages.

**Reason:** At this size sub-packages add navigation without benefit, and
they would force every class to be `public`.

**When to revisit:** A second feature becomes a sibling package, and
`ApiExceptionHandler` moves to a shared package. A single feature growing
past roughly 15–20 classes may be split internally.

## 11. Health check over real HTTP in tests

**Decision:** The application test starts the full application on a random
port and calls the health, liveness and readiness endpoints over HTTP. It
also checks that `/actuator/info` returns 404.

**Reason:** These endpoints are what Docker health checks, deployment
scripts and monitoring will rely on; a configuration change that breaks
them should fail the build, not the deployment. The random port avoids
conflicts on shared build machines.

## 12. Defer loading Mockito as a Java agent

**Context:** The test build prints "Mockito is currently self-attaching…".
Since JDK 21, loading an agent at runtime produces a warning; a future JDK
will block it by default. On Java 25 it is only a warning, and these tests
use no mocks.

**Decision:** Not fixed yet.

**Fix, when needed:** Use the `maven-dependency-plugin` `properties` goal
and add `-javaagent:${org.mockito:mockito-core:jar}` to the Surefire
`argLine`.

**When to revisit:** When CI is introduced (to keep build logs clean) or
on the next Spring Boot / JDK upgrade.