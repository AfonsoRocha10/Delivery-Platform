# Delivery Platform

A small Spring Boot delivery-tracking API, used as the workload for building
an automated software delivery environment: CI/CD, containerization,
infrastructure as code, configuration management, monitoring and security
checks.

The scenario: a team deploys a Java application by hand. Deployments are
inconsistent, environments take too long to set up, failures are hard to
diagnose and there is little visibility into application health. This
project replaces that process, one phase at a time, with a repeatable and
observable delivery pipeline.

## Current status

**Phase 0 — application and baseline: complete.** The application, its
tests, the health endpoints and a measured baseline of the manual
process are in place.

**Phase 1 — continuous integration: complete.** A Jenkins server,
defined entirely as code, runs locally in Docker with the
`delivery-platform` job. Its pipeline scans the Git history for committed
secrets (gitleaks), compiles and tests the application in a Java 25
container, publishes the test results and archives the jar; a build takes
about 43 seconds (about 24 before the database tests were added, which
start PostgreSQL containers). Every push to
`main` that changes the application or the pipeline starts a build
automatically, through a GitHub webhook
relayed to the local Jenkins (smee.io), so Jenkins is never exposed. A
failing test or a committed secret fails the build.

**Phase 2 — containerization: in progress.** First sub-step: deliveries
are stored in PostgreSQL instead of memory. The schema is created by
Flyway migrations, concurrent updates of the same delivery are detected
(`409 Conflict`), and the tests run against a real PostgreSQL started by
Testcontainers. An optional `demo` profile fills an empty database with
300 sample deliveries. In Jenkins, the Test stage is given access to the
Docker engine so that Testcontainers can start PostgreSQL there too.
Next: the application's Docker image and a Compose setup with the
database.

The container image, deployment automation and monitoring are not built
yet; see the [roadmap](#roadmap).

## Tech stack

| Area                     | Tool                                  | Status  |
| ------------------------ | ------------------------------------- | ------- |
| Application              | Java 25 (LTS), Spring Boot 4.1.2      | In use  |
| Database                 | PostgreSQL 18, Spring Data JPA, Flyway | In use  |
| Build                    | Maven 3.10.0 via the Maven Wrapper    | In use  |
| Tests                    | JUnit 5, Spring Boot Test, Testcontainers | In use  |
| CI/CD                    | Jenkins 2.580.2 LTS (Docker, Configuration as Code) | In use  |
| Packaging                | Docker                                | Planned |
| Configuration management | Ansible                               | Planned |
| Infrastructure as code   | Terraform                             | Planned |
| Monitoring               | Prometheus + Grafana                  | Planned |
| Image security           | Trivy                                 | Planned |

## Quick start

### Prerequisites

- JDK 25. Maven does **not** need to be installed: the Maven Wrapper
  (`mvnw`) downloads the pinned version on first use.
- Git.
- Docker, running: the tests start PostgreSQL in a container, and the
  application needs a PostgreSQL database.

### Build and test

```bash
git clone https://github.com/AfonsoRocha10/Delivery-Platform.git
cd Delivery-Platform/app
./mvnw verify            # Windows: mvnw.cmd verify
```

`verify` compiles the code, runs all tests and packages the executable JAR
into `app/target/`. The tests start throwaway PostgreSQL containers
(`postgres:18.7-alpine3.24`) through Testcontainers and remove them
afterwards; no database setup is needed.

### Run

The application needs a PostgreSQL database. Start one in Docker,
reachable only from this machine (choose any password; it is not stored
anywhere):

```bash
export DB_PASSWORD='choose-a-local-password'
docker run -d --name delivery-postgres \
  -e POSTGRES_DB=delivery -e POSTGRES_USER=delivery -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p 127.0.0.1:5432:5432 \
  postgres:18.7-alpine3.24
```

If port 5432 is already used (for example by a local PostgreSQL), publish
another one, such as `-p 127.0.0.1:5433:5432`, and use it in the URL
below.

Then start the application. The connection is only configured through
environment variables, so no credentials are stored in the code:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/delivery \
SPRING_DATASOURCE_USERNAME=delivery \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
java -jar target/delivery-service-0.0.1-SNAPSHOT.jar
```

On startup, Flyway creates or upgrades the schema. The API listens on
port `8080`; to use another port (for example, if 8080 is already
taken), also set `SERVER_PORT=8081`. No rebuild is needed for any of
these settings. On Windows PowerShell, set each variable with
`$env:NAME = "value"` before the command.

**Demo data:** add `SPRING_PROFILES_ACTIVE=demo` to load 300 realistic
sample deliveries (all statuses, spread over the last 30 days). They are
only loaded into an empty database, so restarting does not duplicate
them.

To stop: `Ctrl+C` for the application, then
`docker rm -f delivery-postgres` (this also deletes the data).

## API

| Method  | Path                          | Success          | Errors        |
| ------- | ----------------------------- | ---------------- | ------------- |
| `POST`  | `/api/deliveries`             | 201 + `Location` | 400           |
| `GET`   | `/api/deliveries`             | 200              | —             |
| `GET`   | `/api/deliveries/{id}`        | 200              | 400, 404      |
| `PATCH` | `/api/deliveries/{id}/status` | 200              | 400, 404, 409 |

### Delivery lifecycle

```text
CREATED ──> PICKED_UP ──> IN_TRANSIT ──> DELIVERED
   │
   └──> CANCELLED   (only before pickup)
```

`DELIVERED` and `CANCELLED` are final. Any other transition is rejected
with `409 Conflict`. A status change also returns `409 Conflict` if the
delivery was changed by another request in the meantime (optimistic
locking): reload it and try again.

### Examples

Create a delivery:

```bash
curl -i -X POST http://localhost:8080/api/deliveries \
  -H "Content-Type: application/json" \
  -d '{"recipientName": "Ana Silva", "address": "Rua das Flores 10, Porto"}'
```

```http
HTTP/1.1 201
Location: http://localhost:8080/api/deliveries/{id}

{"id":"{id}","recipientName":"Ana Silva","address":"Rua das Flores 10, Porto","status":"CREATED","createdAt":"2026-11-24T14:25:16.768546Z","updatedAt":"2026-11-24T14:25:16.768546Z"}
```

`createdAt` and `updatedAt` are UTC timestamps with microsecond
precision; `updatedAt` changes on every status change.

Advance its status:

```bash
curl -X PATCH http://localhost:8080/api/deliveries/{id}/status \
  -H "Content-Type: application/json" \
  -d '{"status": "PICKED_UP"}'
```

List all deliveries, or fetch one:

```bash
curl http://localhost:8080/api/deliveries
curl http://localhost:8080/api/deliveries/{id}
```

### Errors

Errors use [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem
details (`Content-Type: application/problem+json`). Validation errors add
an `errors` map of field → message.

An invalid transition (e.g. `CREATED` → `DELIVERED`):

```json
{
  "detail": "Delivery {id} cannot move from CREATED to DELIVERED",
  "instance": "/api/deliveries/{id}/status",
  "status": 409,
  "title": "Conflict"
}
```

A request with missing fields:

```json
{
  "detail": "Request validation failed",
  "instance": "/api/deliveries",
  "status": 400,
  "title": "Bad Request",
  "errors": {
    "recipientName": "must not be blank",
    "address": "must not be blank"
  }
}
```

Validation messages follow the JVM locale, so their wording may differ.

## Health checks

| Endpoint                     | Purpose                                        |
| ---------------------------- | ---------------------------------------------- |
| `/actuator/health`           | Overall application health                     |
| `/actuator/health/liveness`  | Is the process alive? (restart if not)         |
| `/actuator/health/readiness` | Can it accept traffic? (used for deployments). `DOWN` (503) while the database is unreachable |

Liveness deliberately ignores the database: restarting the application
does not fix a database outage. When the database comes back, readiness
returns to `UP` by itself.

Only the `health` Actuator endpoint is exposed over HTTP. The others can
reveal internal details and stay disabled.

## Repository layout

```text
Delivery-Platform/
├── app/                    Spring Boot application
│   ├── src/main/java/      Application code (package io.github.afonsorocha10.delivery)
│   ├── src/test/java/      Unit, web and database tests (Testcontainers)
│   ├── src/main/resources/ application.yaml, db/migration/ (Flyway SQL migrations)
│   ├── .mvn/, mvnw, mvnw.cmd   Maven Wrapper
│   └── pom.xml
├── pipeline/
│   ├── Jenkinsfile         CI pipeline: checkout, secret scan, compile, test (with PostgreSQL via Docker), package, archive
│   └── jenkins/            Jenkins server as code: Dockerfile, plugins, compose, casc/, smee/ webhook relay
├── .gitattributes          Forces LF line endings for mvnw and shell scripts
├── .gitignore
├── LICENSE
└── README.md
```

Planned as the phases progress: `docker/`,
`ansible/`, `terraform/`, `monitoring/`, `security/` and `scripts/`.

## Known limitations

- **No authentication** on the API.
- **Runs on the developer machine only:** no container image, deployment
  target or automatic rollback yet (Phases 2–3).
- **Jenkins builds use the host's Docker engine.** Jenkins, and the Test
  stage for Testcontainers, have access to the Docker socket, which
  amounts to full control of that engine. Acceptable for a local,
  single-developer Jenkins that only builds `main`; to be replaced by a
  build agent with its own Docker engine.
- **Slow failure during a database outage:** health checks and requests
  wait up to 30 seconds (the connection pool's default) before reporting
  the database as unavailable.
- **Unexpected server errors** (for example during a database outage)
  return Spring's default error body instead of a problem-details
  response.

## Roadmap

- [x] **Phase 0 — Application and baseline:** API, tests, health endpoint, documented baseline
- [x] **Phase 1 — Continuous integration:** Jenkins pipeline building and testing every change, with a secret scan and automatic builds on push
- [ ] **Phase 2 — Containerization:** PostgreSQL storage (done), Docker image and local Compose setup
- [ ] **Phase 3 — Automated deployment and recovery:** repeatable deploys with health checks and rollback
- [ ] **Phase 4 — Configuration management:** Ansible playbooks for the deployment host
- [ ] **Phase 5 — Infrastructure as code:** Terraform-provisioned environment
- [ ] **Phase 6 — Monitoring and observability:** Prometheus metrics, Grafana dashboards, alerts
- [ ] **Phase 7 — Security checks:** image scanning, dependency checks, secret handling
- [ ] **Phase 8 — Final integration:** end-to-end documentation and results

## Design decisions

- **PostgreSQL with Flyway migrations.** The schema lives in versioned
  SQL files; Hibernate only checks that it matches the code. Migrations
  only add, never remove or rename, so a deployment can be rolled back.
  The first version kept data in memory; the database was added at the
  start of Phase 2, before the Docker image.
- **Optimistic locking.** Each delivery has a version number; a change
  based on outdated data is rejected instead of silently overwriting
  another request's change.
- **Database settings only from the environment.** No URL, user or
  password in the code or the image.
- **Tests on a real PostgreSQL** (Testcontainers), not an in-memory
  substitute, so the migrations and the locking are tested as they run in
  production.
- **Demo data only on request.** Loaded by the application under the
  `demo` profile, never by a migration, so it cannot reach a real
  environment by accident.
- **Maven Wrapper committed.** The developer machine, Jenkins and the
  Docker build all use the same Maven version.
- **LF line endings enforced** for `mvnw` and shell scripts, which fail on
  Linux build agents if checked out with Windows (CRLF) line endings.
- **Minimal Actuator exposure.** Only `health` is reachable over HTTP.
- **Tests are written with the code they cover**, so every commit builds
  and passes.

## License

[MIT](LICENSE)