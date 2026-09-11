# openjdk:21-jdk foi descontinuada pelo time do OpenJDK (imagem oficial "openjdk"
# nao existe mais no Docker Hub) - eclipse-temurin e o sucessor mantido. Usamos
# a variante -jre (nao -jdk): so precisamos rodar o jar ja compilado, nao compilar.
FROM eclipse-temurin:21-jre

# Nao rodar como root (achado do Trivy no pentest)
RUN groupadd -r sgsm && useradd -r -g sgsm sgsm

WORKDIR /app

COPY target/sgsm-0.0.1-SNAPSHOT.jar app.jar

RUN chown sgsm:sgsm app.jar
USER sgsm

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:8080/v3/api-docs || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
