# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-25@sha256:1471c0503b1f746e38aa42a220f1cff49ef94351e48b71a2006f08d0e621c02d AS build
WORKDIR /repo/docker/conversion-sidecar
COPY docker/conversion-sidecar/pom.xml .
COPY docker/conversion-sidecar/src src
COPY src/main/java/com/researchspace/documentconversion/validation /repo/src/main/java/com/researchspace/documentconversion/validation
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

FROM ubuntu:26.04@sha256:b7f48194d4d8b763a478a621cdc81c27be222ba2206ca3ca6bc42b49685f3d9e
ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=UTC \
    SAL_USE_VCLPLUGIN=svp \
    HOME=/work/rspace-conversion \
    LD_LIBRARY_PATH=/usr/lib/libreoffice/program
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
      openjdk-25-jre-headless \
      bubblewrap \
      curl \
      tini \
      libreoffice-core-nogui \
      libreoffice-writer-nogui \
      libreoffice-calc-nogui \
      libreoffice-impress-nogui \
      libreoffice-common \
      libreoffice-java-common \
      fontconfig \
      fonts-opensymbol \
      fonts-crosextra-carlito \
      fonts-crosextra-caladea \
      fonts-liberation2 \
      fonts-dejavu-core \
      fonts-noto-core \
      fonts-noto-cjk \
      fonts-noto-color-emoji \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 converter \
    && install -d -o converter -g converter -m 0700 /work /work/rspace-conversion
COPY --from=build /repo/docker/conversion-sidecar/target/conversion-sidecar-1.0.0-SNAPSHOT.jar /opt/conversion-sidecar.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["/usr/bin/tini", "-s", "--"]
CMD ["java", "-jar", "/opt/conversion-sidecar.jar"]
