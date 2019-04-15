FROM openjdk:8-jre

RUN groupadd -r app -g 1000 && \
    useradd -r -g app -u 1000 app -d /app && \
    mkdir -p /app && \
    chown -R app:app /app

USER 1000

WORKDIR /app

COPY target/scala-2.11/rt-emailer*.jar /app/rt-emailer.jar

ENV JAVA_OPTS="-Xmx2g -Dkube=true"

ENV TZ=Europe/London

EXPOSE 9200

ENTRYPOINT java ${JAVA_OPTS} -jar /app/rt-emailer.jar
