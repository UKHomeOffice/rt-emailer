FROM eclipse-temurin:17-jre

RUN groupadd -r app -g 1000 && \
    useradd -r -g app -u 1000 app -d /app && \
    mkdir -p /app && \
    chown -R app:app /app

USER 1000

WORKDIR /app

COPY target/scala-2.12/rt-emailer.jar /app/rt-emailer.jar

EXPOSE 8085

ENTRYPOINT java $JAVA_OPTS -jar /app/rt-emailer.jar
