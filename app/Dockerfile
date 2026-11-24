# Image of the delivery-service application.
# Build from the app/ folder:  docker build -t delivery-service:local .
# Configuration comes only from the environment at runtime (SPRING_DATASOURCE_*, SERVER_PORT,
# SPRING_PROFILES_ACTIVE); nothing environment-specific or secret is baked into the image.

# ---- Stage 1: build the jar ------------------------------------------------------------------
FROM eclipse-temurin:25.0.5_8-jdk-alpine-3.24 AS build
WORKDIR /build

# Maven Wrapper and POM first: this layer, and the downloaded dependencies below, are reused
# from the build cache until pom.xml or the wrapper changes, so a code change does not
# download everything again.
# "sh ./mvnw" works even if the executable bit was lost on the way (checkout, copy).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh ./mvnw -B -ntp dependency:go-offline

# Then the application source only. Tests are neither compiled nor run here
# (-Dmaven.test.skip=true): they need Docker (Testcontainers) and already ran in CI before an
# image is built. -o (offline) proves the layer above holds every dependency.
COPY src/main/ src/main/
RUN sh ./mvnw -B -ntp -o package -Dmaven.test.skip=true

# Split the jar into layers (dependencies change rarely, application code often), so a code
# change only replaces the small application layer of the final image.
RUN java -Djarmode=tools -jar target/delivery-service-*.jar extract --layers --launcher --destination extracted

# ---- Stage 2: runtime ------------------------------------------------------------------------
FROM eclipse-temurin:25.0.5_8-jre-alpine-3.24

# Dedicated user without a login shell or home: the application never runs as root.
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app -s /sbin/nologin app
WORKDIR /app

# One COPY per layer, least to most frequently changed.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# Commit and time the image was built from, passed by the build (docker build --build-arg ...).
# Declared last: a changed build argument re-runs every RUN step after it, so here it changes
# only the image metadata and every layer above is reused from the cache.
ARG REVISION=unknown
ARG CREATED=unknown
LABEL org.opencontainers.image.title="delivery-service" \
      org.opencontainers.image.source="https://github.com/AfonsoRocha10/Delivery-Platform" \
      org.opencontainers.image.revision="${REVISION}" \
      org.opencontainers.image.created="${CREATED}"

USER 10001:10001
EXPOSE 8080

# Exec form: Java is process 1 and receives the stop signal directly (clean shutdown).
# MaxRAMPercentage: heap sized from the container's memory limit, not the host's.
# ExitOnOutOfMemoryError: exit instead of running on in a broken state, so whatever runs the
# container (Docker restart policy, Compose, an orchestrator) restarts it.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
