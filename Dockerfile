# Usar uma imagem base com JDK 2111
FROM openjdk:21-jdk

# Definir o diretório de trabalho
WORKDIR /app

# Copiar o arquivo JAR da aplicação para o container
COPY target/sgsm-0.0.1-SNAPSHOT.jar app.jar

# Expor a porta padrão do Spring Boot
EXPOSE 8080

# Especificar o comando para rodar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"]
