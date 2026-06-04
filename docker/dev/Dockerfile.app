# Backend dev image: Maven + Temurin JDK 17.
#
# The image deliberately contains NO application source: the worktree is
# bind-mounted at /rspace at runtime so edits on the host are live. The image
# only provides the toolchain (Maven, JDK 17) plus a Maven toolchains.xml and
# the entrypoint that launches `mvn jetty:run`.
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
FROM ${MAVEN_IMAGE}

# RSpace is Java 11 source level but the build toolchain requires JDK 17 (and
# Spotless does not support the Maven toolchains plugin, so Maven itself must
# run on 17 — which it does in this image). The maven-toolchains-plugin still
# needs a matching <jdk> toolchain, so point it at this image's JDK.
RUN mkdir -p /root/.m2 && \
    printf '%s\n' \
      '<?xml version="1.0" encoding="UTF-8"?>' \
      '<toolchains>' \
      '  <toolchain>' \
      '    <type>jdk</type>' \
      '    <provides>' \
      '      <version>17</version>' \
      '      <vendor>openjdk</vendor>' \
      '    </provides>' \
      '    <configuration>' \
      "      <jdkHome>${JAVA_HOME}</jdkHome>" \
      '    </configuration>' \
      '  </toolchain>' \
      '</toolchains>' \
      > /root/.m2/toolchains.xml

COPY entrypoint-app.sh /usr/local/bin/entrypoint-app.sh
RUN chmod +x /usr/local/bin/entrypoint-app.sh

WORKDIR /rspace
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/entrypoint-app.sh"]
