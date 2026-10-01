# PerseveREST for RESTgym. RESTgym builds this with the RESTgym folder as build context, so paths start with
# ./tools/<slug>/ (deploy-tool.sh and package-submission.sh replace perseverest with the slug, e.g. perseverest).

# Build PerseveREST from its source code (RestTestGen with PerseveREST's changes), like RestTestGen's Dockerfile
FROM gradle:8.14-jdk17 AS build
COPY ./tools/perseverest/source/ /source/
WORKDIR /source
RUN gradle build -x test --no-daemon -q && cp build/libs/*-all.jar /perseverest.jar

FROM eclipse-temurin:24-jre-alpine
COPY --from=build /perseverest.jar /app/app.jar
COPY ./tools/perseverest/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

WORKDIR /
ENTRYPOINT ["/entrypoint.sh"]
