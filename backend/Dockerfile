# syntax=docker/dockerfile:1
# =====================================================================================
# NorthCart backend image. Multi-stage: build with Maven + JDK, run on a JRE only.
#
#   docker build -t northcart-backend ./backend
#
# Tests are NOT run here: the integration tests start PostgreSQL in Docker
# (Testcontainers), which doesn't work inside a docker build. CI runs them instead
# (.github/workflows/ci.yml).
# =====================================================================================

# ---------- stage 1: build the jar ---------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# pom.xml first, on its own layer: dependencies are only re-resolved when the pom changes,
# not on every source edit. The cache mount keeps ~/.m2 between builds as well.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Then split the fat jar into layers (libraries change rarely, our classes often), so a
# code change only rebuilds and re-pushes a few hundred KB instead of ~60 MB.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && cp target/northcart-backend-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# ---------- stage 2: runtime ---------------------------------------------------------
# JRE, not JDK: no compiler in production, smaller image, smaller attack surface.
# Ubuntu-based tag: published for both amd64 and arm64 (Apple Silicon).
FROM eclipse-temurin:21-jre
WORKDIR /app

# Never run as root: a compromised process can't modify the image or bind privileged ports.
RUN useradd --system --uid 10001 --no-create-home northcart

# Least-changing layer first, so Docker can reuse the earlier ones.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# Numeric id: Kubernetes' runAsNonRoot can verify it without reading /etc/passwd.
USER 10001

# Heap sized from the CONTAINER's memory limit, not the host's; on OutOfMemoryError the
# JVM exits (and gets restarted) instead of limping along.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# No curl/wget in the image, so the health check speaks HTTP through bash's /dev/tcp.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\n\\r\\n' >&3 && grep -q UP <&3"]

# exec form: java is PID 1 and receives SIGTERM directly -> graceful shutdown.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
