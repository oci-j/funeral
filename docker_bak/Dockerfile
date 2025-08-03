# Backend Dockerfile
FROM quarkus/ubi-quarkus-graalvm:22.3-java17 AS build
USER quarkus
WORKDIR /code
COPY --chown=quarkus:quarkus mvnw /code/mvnw
COPY --chown=quarkus:quarkus .mvn /code/.mvn
COPY --chown=quarkus:quarkus pom.xml /code/
COPY --chown=quarkus:quarkus src /code/src
RUN ./mvnw clean package -Pnative

FROM quarkus/ubi-quarkus-micro:latest
WORKDIR /work/
COPY --from=build --chown=185 /code/target/*-runner /work/application

EXPOSE 8080
USER 185

ENTRYPOINT ["./application", "-Dquarkus.http.host=0.0.0.0"]

## Alternative JVM Dockerfile (faster build, larger image)
#FROM registry.access.redhat.com/ubi8/openjdk-17:1.15
#
#ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'
#
#COPY --chown=185 target/quarkus-app/lib/ /deployments/lib/
#COPY --chown=185 target/quarkus-app/*.jar /deployments/
#COPY --chown=185 target/quarkus-app/app/ /deployments/app/
#COPY --chown=185 target/quarkus-app/quarkus/ /deployments/quarkus/
#
#EXPOSE 8080
#USER 185
#ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
#ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
#
#ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
#
## MongoDB initialization script (optional)
## mongo-init.js
#db = db.getSiblingDB('oci_registry');
