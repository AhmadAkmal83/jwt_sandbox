### Build Stage
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app

# Copy Gradle scripts, wrapper and build files
COPY gradlew ./
COPY gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties gradle/wrapper/
COPY build.gradle.kts settings.gradle.kts ./

# Ensure Gradle wrapper is executable
RUN chmod +x gradlew

# Download dependencies
RUN ./gradlew dependencies --no-daemon

COPY src ./src

# Build the application JAR
RUN ./gradlew bootJar --no-daemon


### Runtime Container Stage
FROM eclipse-temurin:17-jre AS runtime

WORKDIR /app

# Ensure root user is active
USER root

# Update package lists and install curl for actuator
RUN apt-get update && \
    apt-get install -y curl --no-install-recommends && \
    rm -rf /var/lib/apt/lists/*

# Create a non-root app user
RUN useradd -ms /bin/bash spring

# Ensure app directory is writable by app user
RUN mkdir -p /app && chown -R spring:spring /app

# Switch to the app user for the build and runtime
USER spring

# Copy the JAR from the build stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Instruct JVM to respect container's cpu and ram
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
