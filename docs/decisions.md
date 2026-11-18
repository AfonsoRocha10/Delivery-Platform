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
| 13 | [Jenkins in Docker, configured as code](#13-jenkins-in-docker-configured-as-code) | Accepted |
| 14 | [Jenkins on Java 21, builds on Java 25](#14-jenkins-on-java-21-builds-on-java-25) | Accepted |
| 15 | [Jenkins reachable from this machine only](#15-jenkins-reachable-from-this-machine-only) | Accepted |
| 16 | [Docker access through the host socket](#16-docker-access-through-the-host-socket) | Accepted for local use; to be replaced |
| 17 | [Builds on the Jenkins server itself](#17-builds-on-the-jenkins-server-itself) | Accepted for local use; to be replaced |
| 18 | [Manual builds until the pipeline is finished](#18-manual-builds-until-the-pipeline-is-finished) | Accepted |

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
tools are even added. Automated secret scanning (gitleaks) is planned as
a stage of the CI pipeline in Phase 1.

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

## 13. Jenkins in Docker, configured as code

**Context:** A Jenkins server configured by clicking through its UI cannot
be reproduced, reviewed or recovered, which is the same problem this
project removes from deployments.

**Decision:** Jenkins runs in a container built from
`pipeline/jenkins/Dockerfile`. Everything is in files:

- the Jenkins version (pinned LTS image tag);
- the plugins (`plugins.txt`, pinned versions; only top-level plugins are
  listed and their dependencies are resolved automatically);
- server settings (`casc/jenkins.yml`, Configuration as Code);
- jobs (`casc/jobs.yml`, Job DSL);
- how it runs (`compose.yml`) and its secrets (`.env`, not committed).

Server settings and jobs are separate files with one purpose each; the
build steps live in the application repository's `Jenkinsfile`.

**Alternatives:** Install Jenkins on the machine and configure it in the
UI; a single configuration file for everything.

**Consequences:** `docker compose down -v` followed by `up` recreates an
identical Jenkins (verified). Changes are reviewed as Git diffs. Settings
changed in the UI are overwritten on restart. Pushing code to the
repository can change what a build does, but not who can log in to
Jenkins.

## 14. Jenkins on Java 21, builds on Java 25

**Context:** The application requires Java 25. Jenkins itself only needs
a Java version it supports.

**Decision:** The Jenkins server runs on Java 21
(`jenkins/jenkins:2.580.2-lts-jdk21`). The application is compiled and
tested inside an `eclipse-temurin:25-jdk` build container defined in the
`Jenkinsfile`.

**Consequences:** Jenkins and the build JDK can be upgraded
independently. The JDK used for builds is pinned in the pipeline, not
installed into Jenkins by hand.

## 15. Jenkins reachable from this machine only

**Decision:** The UI is published on `127.0.0.1:8090` only (port 8080 is
used by another service). The inbound agent port is disabled, self
sign-up is off and anonymous users see only the login page.

**Consequences:** Nothing is exposed to the local network. GitHub cannot
reach Jenkins, so builds cannot be triggered by webhooks (see
[18](#18-manual-builds-until-the-pipeline-is-finished)). Exposing Jenkins
later requires HTTPS through a reverse proxy.

## 16. Docker access through the host socket

**Context:** Build stages run in containers (Java 25, later gitleaks and
image builds), so Jenkins needs a Docker engine.

**Decision:** The host's Docker socket is mounted into the Jenkins
container. The `jenkins` user is added to the socket's group (`0` on
Docker Desktop, where the socket is `root:root 660`); Jenkins still runs
as `uid=1000`, not as root.

**Alternatives:** A separate Docker-in-Docker service (isolated engine,
but needs TLS certificates between the two containers); a dedicated build
agent with its own Docker.

**Risk:** Anything that can use the socket controls the host's Docker
engine, which is close to administrator access on the machine. Accepted
for a local, single-user lab.

**When to revisit:** Before Jenkins builds code from sources other than
this repository, or runs anywhere other than this machine. The change is
limited to `compose.yml`; the `Jenkinsfile` stays the same.

## 17. Builds on the Jenkins server itself

**Decision:** The Jenkins server (the "built-in node") has 2 executors
and runs the pipeline. The heavy work runs inside build containers.

**Risk:** A build running on the server can read Jenkins' own files.
Jenkins shows a warning about this. Accepted for a local, single-user
lab.

**When to revisit:** Together with
[16](#16-docker-access-through-the-host-socket): add a dedicated agent and
set `numExecutors: 0` in `casc/jenkins.yml`.

## 18. Manual builds until the pipeline is finished

**Decision:** The `delivery-platform` job has no trigger while the
`Jenkinsfile` is being written; builds are started with **Build Now**.
Once the pipeline is complete, Jenkins will poll GitHub every 2 minutes
and build only when `main` changes in code paths (`app/` or
`pipeline/Jenkinsfile`), not for documentation changes.

**Alternatives:** GitHub webhooks (instant, but GitHub cannot reach a
Jenkins bound to `127.0.0.1` without a tunnel); a multibranch job that
builds every branch.

**Consequences:** No failed builds are triggered by unfinished pipeline
work. With polling, a build starts up to about 2 minutes after a push.
Webhooks can replace polling if Jenkins becomes reachable from GitHub.
