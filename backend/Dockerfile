# ============================================================================
# TICKET-ADV146 — Spring Boot Dockerfile (multi-stage Maven -> JRE 25 slim)
#
# Stage 1: build the JAR with Temurin JDK 25 + the Maven wrapper.
# Stage 2: copy ONLY the JAR onto a slim JRE image, run as a non-root user.
#
# BUILD CONTEXT IS THE REPO ROOT, not backend/:
#     docker build -f backend/Dockerfile -t reconx-backend .
# backend/pom.xml declares a compile dependency on the sibling module
# com.dbtraining.reconx:recon-audit-starter:1.0.0, which is built from source
# in this repository and published to no registry. A backend/-scoped context
# cannot see it, so the build dies at dependency resolution. Building the root
# reactor (recon-audit-starter -> backend) keeps the image self-contained and
# reproducible on a machine with an empty ~/.m2.
# ============================================================================

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# POMs and the wrapper first: this layer only changes when the build definition
# changes, so the dependency download below stays cached across source edits.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY recon-audit-starter/pom.xml recon-audit-starter/
COPY backend/pom.xml backend/
RUN chmod +x mvnw && \
    ./mvnw -q -B dependency:go-offline -DskipTests || true

# Sources last — editing them invalidates only the layers below this point.
COPY recon-audit-starter/src recon-audit-starter/src
COPY backend/src backend/src
RUN ./mvnw -q -B -DskipTests package && \
    mv backend/target/reconx-*.jar app.jar

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S reconx && adduser -S reconx -G reconx
USER reconx
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
