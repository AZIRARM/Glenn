# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create a non-root user to run the app
RUN addgroup -S spring && adduser -S spring -G spring

# Copy the built jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the port your app runs on
EXPOSE 1080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]