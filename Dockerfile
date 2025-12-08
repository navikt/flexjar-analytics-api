FROM gcr.io/distroless/java21-debian12:nonroot

WORKDIR /app

COPY build/libs/app.jar app.jar

ENV TZ="Europe/Oslo"
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

CMD ["app.jar"]
