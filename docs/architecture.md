# Architecture

## Current state

A single Spring Boot service, built into one executable JAR and run
directly with `java -jar` on the developer machine.

```text
          HTTP client (curl, browser, future pipeline checks)
                              │
              ┌───────────────┴────────────────┐
              ▼                                ▼
   /api/deliveries/**                 /actuator/health[/liveness|/readiness]
              │                                │
┌─────────────┼────────────────────────────────┼──────────────┐
│ delivery-service (Spring Boot 4.1, Java 25)  │              │
│             ▼                                ▼              │
│   DeliveryController ──errors──> ApiExceptionHandler        │
│             │                    (RFC 9457 problem details) │
│             ▼                                               │
│   DeliveryService  ── checks transitions with DeliveryStatus│
│             │                                               │
│             ▼                                               │
│   DeliveryRepository (ConcurrentHashMap, in memory)         │
└─────────────────────────────────────────────────────────────┘
```

### Components

| Component | Responsibility |
| --------- | -------------- |
| `DeliveryController` | HTTP API: request validation, mapping to and from JSON, `201 Created` with `Location` header. |
| `DeliveryService` | Business operations: create, find, change status. |
| `DeliveryStatus` | The lifecycle rules: which status changes are allowed. |
| `DeliveryRepository` | Stores deliveries in memory. Status updates are atomic per delivery. |
| `ApiExceptionHandler` | Turns exceptions into consistent `application/problem+json` responses (400, 404, 409). |
| Spring Boot Actuator | Health, liveness and readiness endpoints. Nothing else is exposed. |

### Delivery lifecycle

```text
CREATED ──> PICKED_UP ──> IN_TRANSIT ──> DELIVERED
   │
   └──> CANCELLED   (only before pickup)
```

### Current limitations

- Data lives in memory: it is lost on restart, and only one instance can
  run at a time.
- No authentication.
- Built, run and checked by hand; see [deployment.md](deployment.md).

## Target state (planned)

Each element below is added in its own phase; none of it exists yet.

```text
Developer ──push──> GitHub
                      │
                      ▼
                   Jenkins ── build & test (Maven)
                      │     ── dependency and image scans (Trivy)
                      │     ── build Docker image
                      ▼
            Deployment target (Linux VM first, cloud later)
              ├─ provisioned by Terraform
              ├─ configured by Ansible
              └─ runs the application container
                      │
                      ▼
            Prometheus + Grafana ── metrics, dashboards, alerts
```

The reasons behind the current design are recorded in
[decisions.md](decisions.md).