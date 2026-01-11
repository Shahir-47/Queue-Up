# 1. Build Stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy the entire project
COPY . .

# Move into Backend to start the process
WORKDIR /app/Backend

# Accept the variables from Render
ARG VITE_AWS_REGION
ARG VITE_S3_BUCKET

# Set them as environment variables for the build process
ENV VITE_AWS_REGION=$VITE_AWS_REGION
ENV VITE_S3_BUCKET=$VITE_S3_BUCKET

# Run the build (triggers npm install & build via frontend-maven-plugin)
RUN mvn clean package -DskipTests

# 2. Run Stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the final JAR file from the build stage
COPY --from=build /app/Backend/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]