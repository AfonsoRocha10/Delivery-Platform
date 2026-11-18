# Continuous integration

Jenkins builds and tests the application. The Jenkins server itself is
defined as code in [`pipeline/jenkins/`](../pipeline/jenkins): it can be
deleted and recreated identically from these files, with nothing
configured by hand.

**Status:** the Jenkins server, the `delivery-platform` job and the
pipeline (`pipeline/Jenkinsfile`) are in place: a build checks out
`main`, compiles, runs the tests, publishes their results and archives
the jar. Builds are still started manually with **Build Now**; automatic
builds on push are not enabled yet.

## How it fits together

```text
compose.yml ──starts──> Jenkins (image built from Dockerfile + plugins.txt)
                          │ reads at startup
                          ├── casc/jenkins.yml  server: login, security, executors, URL
                          └── casc/jobs.yml     job "delivery-platform": repository + Jenkinsfile path
                                                  │ fetched from GitHub on every build
                                                  └── pipeline/Jenkinsfile  build stages
```

| File | Purpose | Change takes effect |
| ---- | ------- | ------------------- |
| `pipeline/jenkins/Dockerfile` | Jenkins image: pinned Jenkins LTS, Docker CLI, plugins, configuration | `docker compose up -d --build` |
| `pipeline/jenkins/plugins.txt` | Plugins with pinned versions (dependencies resolved automatically) | `docker compose up -d --build` |
| `pipeline/jenkins/compose.yml` | How Jenkins runs: port, data volume, Docker access, environment | `docker compose up -d` |
| `pipeline/jenkins/casc/jenkins.yml` | Server settings (Configuration as Code) | `docker compose up -d --build` |
| `pipeline/jenkins/casc/jobs.yml` | Job definitions (Job DSL) | `docker compose up -d --build` |
| `pipeline/jenkins/.env.example` | Template listing the required variables (committed) | — |
| `pipeline/jenkins/.env` | Real values, e.g. the admin password (**never committed**) | `docker compose up -d` |
| `pipeline/Jenkinsfile` | What a build does (see [The pipeline](#the-pipeline)) | Next build after it is pushed, no restart |

Settings defined in `casc/` are re-applied on every start, so changes
made in the Jenkins UI to those settings are lost on restart. Change the
files instead.

## The pipeline

Jenkins reads `pipeline/Jenkinsfile` from GitHub at the start of every
build, so a change to it must be pushed before it takes effect.

```text
Jenkins server     Checkout
                      │
Build container    Compile ──> Test ──> Package     (eclipse-temurin:25-jdk)
                                           │
Jenkins server                          Archive     (jar + fingerprint)

All stages use the same workspace folder.
```

| Stage | Runs on | What it does |
| ----- | ------- | ------------ |
| Checkout | Jenkins server | Clones the latest commit of `main` and names the build after it, e.g. `#2 4bc7556` |
| Compile | Build container | Prints the Java and Maven versions, then `./mvnw compile` |
| Test | Build container | `./mvnw test`; the results are published to Jenkins even when tests fail |
| Package | Build container | `./mvnw package -DskipTests` (tests already ran in Test) |
| Archive | Jenkins server | Stores `delivery-service-*.jar` with the build and records its fingerprint |

Details:

- **Java and Maven versions** are fixed by the build image tag
  (`eclipse-temurin:25-jdk`) and the Maven Wrapper (Maven 3.10.0), not by
  tools installed in Jenkins.
- **Build container:** Checkout runs on the Jenkins server because the
  Java image has no `git`. The Maven stages start the container with
  `reuseNode true`, so it sees the same workspace.
- **Maven cache:** Maven and the project dependencies are kept in
  `.m2/` inside the workspace (`MAVEN_USER_HOME` and `MAVEN_ARGS` in the
  `Jenkinsfile`), so they are downloaded only once.
- **A failing step fails the build:** any command that exits with an error
  (for example a failing test) marks its stage red and skips the
  remaining stages.
- **Limits:** a build is stopped after 15 minutes, only one build runs at
  a time and the last 20 builds are kept.

### Reading a build

On <http://localhost:8090/job/delivery-platform/>, open a build:

| Where | What it shows |
| ----- | ------------- |
| Build name | `#<build number> <short commit>`: which commit was built |
| **Console Output** | The full log, with a timestamp on every line |
| **Tests** / Test Result | Passed and failed tests, with the error for each failure |
| Build Artifacts | The jar, ready to download |
| **See Fingerprints** | The jar's checksum, to identify which build produced a copy |

### Build times

Measured on the same machine as the [baseline](deployment.md#baseline)
(Ryzen 5 5600X, 16 GB), pipeline at commit `4bc7556`:

| Build | Total | Notes |
| ----- | ----- | ----- |
| #1 (first run) | 45.6 s | Includes pulling the Java image and downloading Maven and all dependencies |
| #2 (normal run) | 21.8 s | Nothing downloaded |

In build #2, Maven itself took 8.7 s (compile 1.3 s, test 5.5 s,
package 1.9 s). The other ~13 s is pipeline overhead, mostly starting a
build container for each of the three Maven stages. The local
`./mvnw clean verify` baseline is 10.9 s.

## Running Jenkins

### Prerequisites

- Docker Desktop (or another Docker engine) running.
- Port `8090` free on `127.0.0.1`.

### First-time setup

```bash
cd pipeline/jenkins
cp .env.example .env        # then set JENKINS_ADMIN_PASSWORD in .env
docker compose up -d --build
docker compose ps           # wait for "healthy"
```

Open <http://localhost:8090> and log in as `admin` with the password
from `.env`. There is no setup wizard.

### Everyday commands

Run from `pipeline/jenkins/`:

| Task | Command |
| ---- | ------- |
| Start / apply changes | `docker compose up -d --build` |
| Stop (keeps data) | `docker compose stop` |
| Follow logs | `docker compose logs -f jenkins` |
| Status and health | `docker compose ps` |
| Delete Jenkins **and all its data** | `docker compose down -v` |

`docker compose down -v` removes build history and archived artifacts.
The configuration is not lost: it comes back from the files on the next
`up`.

### Upgrading Jenkins or plugins

1. Change the image tag in `Dockerfile` (and the `image:` tag in
   `compose.yml` to match).
2. Resolve plugin versions compatible with the new Jenkins version and
   update `plugins.txt`:

   ```bash
   docker run --rm jenkins/jenkins:<version>-lts-jdk21 \
     jenkins-plugin-cli --no-download --list --plugins <plugin ids>
   ```

3. Check the pinned versions for known vulnerabilities:

   ```bash
   jenkins-plugin-cli --no-download --view-security-warnings --plugin-file plugins.txt
   ```

4. `docker compose up -d --build`. An incompatible plugin version fails
   the image build, before anything runs.

## What is configured

| Area | Setting |
| ---- | ------- |
| Jenkins | 2.580.2 LTS on Java 21 (the application itself is built with Java 25 inside build containers) |
| Access | `http://localhost:8090`, reachable from this machine only |
| Login | Local user `admin`; self sign-up disabled; anonymous users see only the login page |
| Build capacity | 2 executors on the Jenkins server (no separate agents) |
| Agent port | Disabled (`-1`) |
| Docker | Jenkins uses the host Docker engine to run build containers |
| Job | `delivery-platform`: `main` branch of this repository, script `pipeline/Jenkinsfile` |
| Triggers | None yet: builds start with **Build Now** |

## Security notes

These are accepted for a local lab and listed with the change that
removes each one. Reasons are in [decisions.md](decisions.md).

| Risk | Why it exists | Planned fix |
| ---- | ------------- | ----------- |
| Jenkins can control the host Docker engine | The Docker socket is mounted so builds can run in containers | Docker-in-Docker service or a dedicated build agent |
| Builds run on the Jenkins server ("built-in node") | There are no separate agents | Dedicated agent, then `numExecutors: 0` |
| No HTTPS | Only reachable from `127.0.0.1` | Reverse proxy with TLS if Jenkins is ever exposed |

## Verification

How the setup was checked (all passed):

- The image builds; the container reports `healthy`.
- The Configuration as Code and Job DSL logs show no errors.
- Anonymous requests get `403` (except the login page); a wrong password
  gets `401`; `admin` logs in.
- The job points to this repository, `*/main` and `pipeline/Jenkinsfile`,
  with no triggers.
- Jenkins runs as `uid=1000(jenkins)`, not root, and can start a container
  through Docker.
- After `docker compose down -v` and `up`, everything above is identical.

Pipeline (builds #1 and #2, both `SUCCESS`):

- The build is named after the commit it built (`#1 4bc7556`).
- The log shows `openjdk version "25.0.5"` and `Apache Maven 3.10.0`, and
  Maven is installed under the workspace `.m2/`.
- The build container runs as `1000:1000`, not root.
- Test Result shows 36 tests, all passed.
- `delivery-service-0.0.1-SNAPSHOT.jar` is archived with a fingerprint.
- Build #2 downloads nothing (cache works).

Known noise in the log: `WARNING: A Java agent has been loaded
dynamically` during the tests comes from Mockito (decision 12) and is
harmless; it is planned to be removed in Phase 1.