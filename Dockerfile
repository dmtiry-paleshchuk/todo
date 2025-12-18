# syntax=docker/dockerfile:1.7
# ---- STAGE 1: COMPILATION AND DEPENDENCY COLLECTION ----
FROM docker.io/eclipse-temurin:21-jdk AS build
WORKDIR /app

# Install necessary tools
RUN apt-get update -y && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*

# Copy Gradle files (for layer caching)
COPY gradlew gradle/wrapper/gradle-wrapper.properties ./
COPY settings.gradle ./
COPY build.gradle ./
COPY gradle ./gradle

# Copy source code of all modules
COPY model ./model
COPY repository ./repository
COPY web ./web
COPY buildSrc ./buildSrc

# Ensure executability
RUN chmod +x gradlew

# Perform full build (get .class files and dependencies)
# We do NOT use :web:war, but only general build
RUN ./gradlew build --no-daemon -x test

# ---- STAGE 2: RUNTIME AS EXPLODED WEBAPP (NO WAR) ----
FROM docker.io/library/tomcat:9.0-jdk17-temurin

# Deploy as exploded webapp under /usr/local/tomcat/webapps/todo
ENV CATALINA_BASE=/usr/local/tomcat \
    WEBAPP_DIR=/usr/local/tomcat/webapps/todo

RUN mkdir -p "$WEBAPP_DIR/WEB-INF/classes" "$WEBAPP_DIR/WEB-INF/lib"

# Copy JSPs, static resources, and web.xml
COPY web/src/main/webapp/ "$WEBAPP_DIR/"

# Copy compiled classes/resources into WEB-INF/classes
COPY --from=build /app/model/build/classes/java/main "$WEBAPP_DIR/WEB-INF/classes/"
COPY --from=build /app/repository/build/classes/java/main "$WEBAPP_DIR/WEB-INF/classes/"
COPY --from=build /app/web/build/classes/java/main "$WEBAPP_DIR/WEB-INF/classes/"

# Copy project jars (module outputs) and runtime dependencies into WEB-INF/lib
COPY --from=build /app/model/build/libs/*.jar "$WEBAPP_DIR/WEB-INF/lib/"
COPY --from=build /app/repository/build/libs/*.jar "$WEBAPP_DIR/WEB-INF/lib/"
COPY --from=build /app/web/build/libs/*.jar "$WEBAPP_DIR/WEB-INF/lib/"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8080/todo/ || exit 1

CMD ["catalina.sh", "run"]