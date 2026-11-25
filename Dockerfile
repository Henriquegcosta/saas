# =====================================================================
# VIGENTRA - imagem Docker (build multi-stage)
#   docker build -t vigentra .
# =====================================================================

# ---------- Etapa 1: compilação com Maven ----------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Etapa 2: imagem final só com o JRE ----------
FROM eclipse-temurin:25-jre
ENV TZ=America/Sao_Paulo \
    APP_STORAGE_DIR=/data/uploads \
    MAIL_DEV_DIR=/data/emails-dev \
    VIGENTRA_LOG_FILE=/data/logs/vigentra.log \
    JAVA_OPTS="-Xms256m -Xmx512m"

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
# usuário sem privilégios (nunca rodar a aplicação como root)
RUN groupadd --system vigentra && useradd --system --gid vigentra --home /app vigentra \
    && mkdir -p /data/uploads /data/emails-dev /data/logs && chown -R vigentra:vigentra /data
WORKDIR /app
COPY --from=build /src/target/vigentra.jar app.jar
USER vigentra
VOLUME ["/data"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:8080/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
