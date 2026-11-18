# Continuous integration

Jenkins builds and tests the application. The Jenkins server itself is
defined as code in [`pipeline/jenkins/`](../pipeline/jenkins): it can be
deleted and recreated identically from these files, with nothing
configured by hand.

**Status:** the Jenkins server and the `delivery-platform` job are in
place. The pipeline (`pipeline/Jenkinsfile`) is being written, and builds
are started manually until it is finished. Automatic builds on push are
not enabled yet.

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
| `pipeline/Jenkinsfile` | What a build does *(in progress)* | Next build, no restart |

Settings defined in `casc/` are re-applied on every start, so changes
made in the Jenkins UI to those settings are lost on restart. Change the
files instead.

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