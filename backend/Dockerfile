# Build stage
FROM maven:3.9.6-eclipse-temurin-21-jammy AS build
WORKDIR /app
# Copy pom.xml and download dependencies to cache them
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Copy source code and build
COPY src ./src
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Create a non-root user and group
RUN addgroup --system spring && adduser --system --ingroup spring spring
# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar
# Set ownership
RUN chown spring:spring app.jar && mkdir -p logs uploads && chown spring:spring logs uploads
# Run as non-root user
USER spring:spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]