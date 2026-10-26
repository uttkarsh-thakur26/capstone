# syntax=docker/dockerfile:1

# Build: package the application with the Maven Wrapper and split the jar into layers.
FROM eclipse-temurin:25.0.4.1_1-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# Tests run in CI and with `./mvnw verify`; the image build only packages.
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -ntp -Dmaven.test.skip=true package \
    && cp target/knowledge-platform-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# Runtime: JRE only, non-root user, one layer per rate of change.
FROM eclipse-temurin:25.0.4.1_1-jre
RUN useradd --system --no-create-home --uid 10001 app
# The files volume is mounted here. A new named volume takes this directory's owner, so the
# non-root user can write uploads to it (D8, ADR-029).
RUN mkdir -p /var/lib/knowledge-platform/files && chown app /var/lib/knowledge-platform/files
WORKDIR /application
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
