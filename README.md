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
tests, the health endpoints and a measured
[baseline](docs/deployment.md#baseline) of the manual process are in
place. Next is Phase 1 (continuous integration). The CI pipeline,
container image, deployment automation and monitoring are not built yet;
see the [roadmap](#roadmap).

## Tech stack

| Area                     | Tool                                  | Status  |
| ------------------------ | ------------------------------------- | ------- |
| Application              | Java 25 (LTS), Spring Boot 4.1.2      | In use  |
| Build                    | Maven 3.10.0 via the Maven Wrapper    | In use  |
| Tests                    | JUnit 5, Spring Boot Test             | In use  |
| CI/CD                    | Jenkins                               | Planned |
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

### Build and test

```bash
git clone https://github.com/AfonsoRocha10/Delivery-Platform.git
cd Delivery-Platform/app
./mvnw verify            # Windows: mvnw.cmd verify
```

`verify` compiles the code, runs all tests and packages the executable JAR
into `app/target/`.

### Run

```bash
./mvnw spring-boot:run   # Windows: mvnw.cmd spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar target/delivery-service-0.0.1-SNAPSHOT.jar
```

The API listens on port `8080`. To use another port (for example, if 8080
is already taken), set `SERVER_PORT`; no rebuild is needed:

```bash
SERVER_PORT=8081 java -jar target/delivery-service-0.0.1-SNAPSHOT.jar
```

On Windows PowerShell: `$env:SERVER_PORT = "8081"` before the command.

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
with `409 Conflict`.

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

{"id":"{id}","recipientName":"Ana Silva","address":"Rua das Flores 10, Porto","status":"CREATED"}
```

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
| `/actuator/health/readiness` | Can it accept traffic? (used for deployments)  |

Only the `health` Actuator endpoint is exposed over HTTP. The others can
reveal internal details and stay disabled.

## Repository layout

```text
Delivery-Platform/
├── app/                    Spring Boot application
│   ├── src/main/java/      Application code (package io.github.afonsorocha10.delivery)
│   ├── src/test/java/      Unit and web-layer tests
│   ├── src/main/resources/ application.yaml
│   ├── .mvn/, mvnw, mvnw.cmd   Maven Wrapper
│   └── pom.xml
├── docs/                   Deployment, decisions, troubleshooting, architecture
├── .gitattributes          Forces LF line endings for mvnw and shell scripts
├── .gitignore
├── LICENSE
└── README.md
```

Planned as the phases progress: `pipeline/` (Jenkinsfile), `docker/`,
`ansible/`, `terraform/`, `monitoring/`, `security/` and `scripts/`.

## Documentation

| Document | Contents |
| -------- | -------- |
| [Deployment](docs/deployment.md) | Current manual build and deploy process, and baseline measurements |
| [Architecture](docs/architecture.md) | Components today and the planned delivery environment |
| [Decisions](docs/decisions.md) | Technical decisions, alternatives considered and reasons |
| [Troubleshooting](docs/troubleshooting.md) | Known problems with their causes and fixes |

## Roadmap

- [x] **Phase 0 — Application and baseline:** API, tests, health endpoint, documented baseline
- [ ] **Phase 1 — Continuous integration:** Jenkins pipeline building and testing every change
- [ ] **Phase 2 — Containerization:** Docker image and local Compose setup
- [ ] **Phase 3 — Automated deployment and recovery:** repeatable deploys with health checks and rollback
- [ ] **Phase 4 — Configuration management:** Ansible playbooks for the deployment host
- [ ] **Phase 5 — Infrastructure as code:** Terraform-provisioned environment
- [ ] **Phase 6 — Monitoring and observability:** Prometheus metrics, Grafana dashboards, alerts
- [ ] **Phase 7 — Security checks:** image scanning, dependency checks, secret handling
- [ ] **Phase 8 — Final integration:** end-to-end documentation and results

## Design decisions

- **In-memory storage.** Keeps the first version small; data is lost on
  restart. A database will be considered when the deployment setup needs
  persistent state.
- **Maven Wrapper committed.** The developer machine, Jenkins and the
  Docker build all use the same Maven version.
- **LF line endings enforced** for `mvnw` and shell scripts, which fail on
  Linux build agents if checked out with Windows (CRLF) line endings.
- **Minimal Actuator exposure.** Only `health` is reachable over HTTP.
- **Tests are written with the code they cover**, so every commit builds
  and passes.

The full reasoning, alternatives and open items are in
[docs/decisions.md](docs/decisions.md).

## License

[MIT](LICENSE)