FROM eclipse-temurin:21-jre-alpine

WORKDIR /scheduler

COPY build/libs/scheduler-1.0-SNAPSHOT-all.jar scheduler.jar

RUN mkdir -p /touchstore

ENTRYPOINT ["java", "-jar", "scheduler.jar"]
