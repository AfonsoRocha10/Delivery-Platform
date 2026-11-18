# Troubleshooting

Problems encountered while building and running the project, with their
cause and fix.

## Application fails to start: port 8080 already in use

**Symptom:** Startup stops with
`Web server failed to start. Port 8080 was already in use.`

**Cause:** Another program (on the development machine, an Apache httpd
service) is listening on port 8080.

**Fix:** Run the application on another port. No rebuild is needed:

```bash
SERVER_PORT=8081 java -jar target/delivery-service-0.0.1-SNAPSHOT.jar
```

To find what is using the port:

```bash
netstat -ano | grep ':8080 '     # Windows (Git Bash); last column is the PID
ss -ltnp | grep ':8080 '         # Linux
```

## Build fails because the JAR is locked (Windows)

**Symptom:** `./mvnw clean` or `package` fails with an error that
`target/delivery-service-0.0.1-SNAPSHOT.jar` cannot be deleted.

**Cause:** A previous run of the application is still running. On Windows,
`java` on the `PATH` is often a launcher (`javapath`) that starts the real
JVM as a separate process. Stopping the launcher's process ID does not
stop the JVM, which keeps the JAR open.

**Fix:** Stop the process that is actually listening on the port:

```bash
netstat -ano | grep ':8081 ' | grep LISTENING   # last column is the PID
taskkill //PID <pid> //F                        # Git Bash
```

Starting the application with the full JDK path (`"$JAVA_HOME/bin/java"`)
avoids the launcher, so the PID you see is the JVM's.

## `curl` from PowerShell returns 400 for a valid request

**Symptom:** A `POST` that works from Bash returns `400 Bad Request` with a
JSON parse error when run from Windows PowerShell 5.1.

**Cause:** Windows PowerShell 5.1 strips the inner double quotes when
passing arguments to native programs such as `curl.exe`, so the API
receives invalid JSON such as `{recipientName: Ana}`.

**Fix:** Run `curl` from Git Bash or WSL, or use PowerShell's own client:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/deliveries `
  -ContentType "application/json" `
  -Body '{"recipientName": "Ana Silva", "address": "Rua das Flores 10, Porto"}'
```

## Validation messages are not in English

**Symptom:** A `400` response contains messages such as
`"não deve estar em branco"` instead of `"must not be blank"`.

**Cause:** Bean Validation messages follow the JVM's default locale, which
comes from the operating system.

**Fix:** This is expected behaviour. To force English, start the JVM with
`-Duser.language=en`. Tests check *which* fields failed, never the message
text, so they pass on any locale.

## `mvnw` fails on Linux with `bad interpreter`

**Symptom:** `./mvnw: /bin/sh^M: bad interpreter: No such file or
directory`.

**Cause:** The script was checked out with Windows (CRLF) line endings.

**Fix:** The repository's `.gitattributes` forces LF for `mvnw` and `*.sh`,
so a fresh clone is correct. For an older clone, delete the file and let
Git check it out again with the correct line endings:

```bash
rm mvnw && git checkout -- mvnw
```

## `./mvnw: Permission denied` on Linux or macOS

**Cause:** The execute bit is missing (for example, after copying the files
instead of cloning).

**Fix:** `chmod +x mvnw`

## Jenkins: `required variable JENKINS_ADMIN_PASSWORD is missing a value`

**Symptom:** `docker compose up` (or `config`) in `pipeline/jenkins/` stops
with this message before starting anything.

**Cause:** `pipeline/jenkins/.env` does not exist, is empty, or has no
`JENKINS_ADMIN_PASSWORD=` line. `compose.yml` uses
`${JENKINS_ADMIN_PASSWORD:?…}`, which deliberately refuses to start
Jenkins with an empty admin password.

**Fix:** `cp .env.example .env` and set the password. Format: no spaces
around `=`; write `$` as `$$`.

## Jenkins does not start, or settings are missing

**Symptom:** The container restarts repeatedly or never becomes
`healthy`, or a setting or job from `casc/` is missing.

**Cause:** Configuration as Code rejects the whole configuration if any
file in `casc/` is invalid, for example:

- a tab instead of spaces in the YAML;
- a misspelled or removed setting (`unknown attribute`);
- a `#` comment inside a Job DSL `script:` block (Groovy needs `//`);
- a literal `${...}` inside a Job DSL script that should be `^${...}`.

**Fix:** Read the error, which names the file and setting:

```bash
docker compose logs jenkins | grep -iE "configuration.as.code|SEVERE|exception" -A 5
```

## Jenkins: `permission denied` on `/var/run/docker.sock`

**Symptom:** `docker exec jenkins docker version` shows the client but
fails for the server with `permission denied while trying to connect to
the Docker daemon socket`.

**Cause:** Jenkins runs as the non-root `jenkins` user, which is not in
the group that owns the socket.

**Fix:** Find the socket's group ID and put it in `group_add` in
`compose.yml` (do **not** run Jenkins as root):

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v /var/run/docker.sock:/s alpine stat -c '%g %U:%G %a' /s
```

On Docker Desktop this prints `0 root:root 660`, so the value is `"0"`.

## Git Bash changes Linux paths in `docker` commands

**Symptom:** A `docker run -v /var/run/docker.sock:...` or
`docker exec ... /some/path` command run from Git Bash fails with a path
like `C:/Program Files/Git/var/run/docker.sock`.

**Cause:** Git Bash (MSYS) converts arguments that look like Unix paths
into Windows paths before passing them to Windows programs such as
`docker.exe`.

**Fix:** Prefix the command with `MSYS_NO_PATHCONV=1`, for example
`MSYS_NO_PATHCONV=1 docker run --rm -v /var/run/docker.sock:/s alpine ...`.
