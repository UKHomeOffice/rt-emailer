FROM eclipse-temurin:24-jre

RUN groupadd -r app -g 1000 || true
RUN useradd -r -g 1000 -u 1000 -d /app || true
RUN mkdir -p /app || true
RUN chown -R 1000:1000 /app || true

COPY target/scala-3.7.1/rt-emailer.jar /app/rt-emailer.jar
RUN chown 1000:1000 /app/rt-emailer.jar
RUN chmod ugo+r /app/rt-emailer.jar
USER 1000
WORKDIR /app
EXPOSE 8085

ENTRYPOINT java $JAVA_OPTS -jar /app/rt-emailer.jar
