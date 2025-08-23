# Etapa 1: Construir la aplicación con Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /home/app

# Copiar el pom.xml y descargar dependencias
COPY pom.xml .
RUN mvn dependency:go-offline

# Copiar el resto del código fuente y construir el .jar
COPY src ./src
RUN mvn package -DskipTests

# Etapa 2: Crear la imagen final y ligera para producción
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiar solo el .jar construido desde la etapa anterior
COPY --from=build /home/app/target/*.jar /app/application.jar

# Exponer el puerto en el que corre Spring Boot (usualmente 8080)
EXPOSE 8080

# Comando para ejecutar la aplicación
ENTRYPOINT ["java", "-jar", "/app/application.jar"]