# ── Build stage ─────────────────────────────────────────────────────────────
# Uses the official Maven + JDK image so no mvnw needed inside the container.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml first and download dependencies in a separate layer.
# This layer is cached and only re-runs when pom.xml changes — not on every code edit.
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Runtime stage ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user (security best practice)
RUN groupadd -r bookstore && useradd -r -g bookstore bookstore
USER bookstore

COPY --from=build /app/target/online-bookstore-*.jar app.jar

EXPOSE 8080

# Container-level health check used by docker-compose, ECS, and Kubernetes
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
