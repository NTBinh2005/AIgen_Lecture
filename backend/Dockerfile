# backend/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache \
    tesseract-ocr \
    tesseract-ocr-data-eng \
    tesseract-ocr-data-vie
ENV TESSDATA_PREFIX=/usr/share/tessdata
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# FIX: Grant execution permissions to the Maven wrapper
RUN chmod +x mvnw

# Now safely execute the wrapper[cite: 5]
RUN ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache \
    tesseract-ocr \
    tesseract-ocr-data-eng \
    tesseract-ocr-data-vie
ENV TESSDATA_PREFIX=/usr/share/tessdata
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
