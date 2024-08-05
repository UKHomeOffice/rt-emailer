FROM eclipse-temurin:17-jre

RUN groupadd -r app -g 1000 || true
RUN useradd -r -g app -u 1000 app -d /app || true
RUN mkdir -p /app || true
RUN chown -R app:app /app || true

COPY target/scala-2.12/rt-emailer.jar /app/rt-emailer.jar
RUN chown app:app /app/rt-emailer.jar
RUN chmod ugo+r /app/rt-emailer.jar
USER 1000
WORKDIR /app
EXPOSE 8085

ENTRYPOINT java $JAVA_OPTS -jar /app/rt-emailer.jar
