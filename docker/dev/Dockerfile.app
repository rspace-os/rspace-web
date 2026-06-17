# Backend dev image: Maven + JetBrains Runtime (JBR) 17 + HotswapAgent.
#
# The image contains NO application source: the worktree is bind-mounted at
# /rspace at runtime. The base image provides Maven; on top of it we install the
# JetBrains Runtime as the JVM the app actually runs on, because JBR ships the
# DCEVM-derived "enhanced class redefinition" that, together with HotswapAgent,
# allows hot-reloading far more than plain HotSpot (new methods/fields/classes,
# many Spring bean changes) without restarting the JVM. See README "Debugging".
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
FROM ${MAVEN_IMAGE}

# Pinned versions (update together; both are vetted third-party artifacts).
ARG JBR_VERSION=17.0.14
ARG JBR_BUILD=b1367.22
ARG HOTSWAP_AGENT_VERSION=2.0.1
# Provided automatically by BuildKit (amd64 / arm64).
ARG TARGETARCH

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl ca-certificates; \
    rm -rf /var/lib/apt/lists/*; \
    case "${TARGETARCH}" in \
      amd64) jbr_arch=x64 ;; \
      arm64) jbr_arch=aarch64 ;; \
      *) echo "unsupported TARGETARCH: ${TARGETARCH}" >&2; exit 1 ;; \
    esac; \
    mkdir -p /opt/jbr /opt/hotswap; \
    curl -fsSL "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-${JBR_VERSION}-linux-${jbr_arch}-${JBR_BUILD}.tar.gz" \
      | tar -xz -C /opt/jbr --strip-components=1; \
    curl -fsSL -o /opt/hotswap/hotswap-agent.jar \
      "https://github.com/HotswapProjects/HotswapAgent/releases/download/RELEASE-${HOTSWAP_AGENT_VERSION}/hotswap-agent-${HOTSWAP_AGENT_VERSION}.jar"; \
    # Fail the build early if this JBR does not provide the enhanced-redefinition
    # flag or cannot run.
    /opt/jbr/bin/java -XX:+AllowEnhancedClassRedefinition -version

# Make JBR the JVM that Maven (and therefore jetty:run) runs on.
ENV JAVA_HOME=/opt/jbr
ENV PATH="/opt/jbr/bin:${PATH}"

# The maven-toolchains-plugin needs a JDK-17 toolchain; point it at JBR. (JBR is
# OpenJDK-based, so vendor=openjdk is correct.)
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
      '      <jdkHome>/opt/jbr</jdkHome>' \
      '    </configuration>' \
      '  </toolchain>' \
      '</toolchains>' \
      > /root/.m2/toolchains.xml

COPY entrypoint-app.sh /usr/local/bin/entrypoint-app.sh
RUN chmod +x /usr/local/bin/entrypoint-app.sh

WORKDIR /rspace
EXPOSE 8080 5005
ENTRYPOINT ["/usr/local/bin/entrypoint-app.sh"]
