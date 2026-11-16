# Deployment

How the application is built and deployed **today**, by hand, and the
baseline measurements that later automation will be compared against.

As the project progresses, each manual step below is replaced by an
automated one (Jenkins, Docker, Ansible, Terraform). This document records
the starting point.

## Manual build and deploy (current process)

There is no separate deployment target yet: the application is built and
run on the developer machine.

### Prerequisites

- JDK 25 on the `PATH` (or `JAVA_HOME` set).
- Git.
- A free TCP port (default `8080`).

### Steps

| # | Step | Command |
| - | ---- | ------- |
| 1 | Get the source | `git clone https://github.com/AfonsoRocha10/Delivery-Platform.git` |
| 2 | Enter the application folder | `cd Delivery-Platform/app` |
| 3 | Build and test | `./mvnw clean verify` (Windows: `mvnw.cmd clean verify`) |
| 4 | Choose a port, if 8080 is taken | `export SERVER_PORT=8081` (PowerShell: `$env:SERVER_PORT = "8081"`) |
| 5 | Start the application | `java -jar target/delivery-service-0.0.1-SNAPSHOT.jar` |
| 6 | Check it is ready | `curl http://localhost:8081/actuator/health/readiness` → `{"status":"UP"}` |
| 7 | Stop it | `Ctrl+C` in its terminal, or stop the JVM process by its PID |

The build produces a single executable JAR (about 23 MB) that contains the
application and all its dependencies. No application server is needed.

### Updating to a new version

There is no update procedure beyond repeating the steps: stop the running
process, pull the latest code, rebuild and start again. During that time
the API is unavailable, and if the new version fails to start there is no
automatic way back to the previous one.

### Configuration

| Setting | How to change it | Default |
| ------- | ---------------- | ------- |
| HTTP port | `SERVER_PORT` environment variable | `8080` |

Any Spring Boot property can be overridden the same way (environment
variable or `--property=value` argument) without rebuilding.

## Baseline

Measured on 2026-11-16, commit `134ff1b`.

**Machine:** AMD Ryzen 5 5600X (6 cores), 16 GB RAM, Windows 11 Home,
Java 25.0.5 LTS. Maven dependencies already in the local cache.

| Metric | Run 1 | Run 2 | Run 3 | Median |
| ------ | ----- | ----- | ----- | ------ |
| Clean build with tests (`./mvnw -q clean verify`, 36 tests) | 10.8 s | 10.9 s | 11.0 s | **10.9 s** |
| Spring Boot startup (as logged: "Started … in") | 1.57 s | 1.57 s | 1.56 s | **1.57 s** |
| Process launch → readiness `UP` (measured by polling) | 2.23 s | 2.21 s | 2.23 s | **2.23 s** |

| Property | Value |
| -------- | ----- |
| Manual steps from clone to verified running app | 6 (steps 1–6 above) |
| Manual steps to deploy a new version | 4 (stop, pull, rebuild, start) + health check |
| Automated tests | 36, run only when someone runs the build |
| Deployment target | Developer machine only |
| Rollback | None |
| Health monitoring | Manual `curl`; nobody is alerted when it goes down |
| Data persistence | None (in memory; lost on restart) |

**Not measured:** the first build on a clean machine, which also downloads
Maven and all dependencies and depends mostly on network speed.

### How the numbers were taken

- Build: wall-clock time of `./mvnw -q clean verify`, three consecutive
  runs.
- Startup: the JAR was started with `SERVER_PORT=8081`, and
  `/actuator/health/readiness` was polled every 100 ms until it returned
  200. The process was stopped between runs.