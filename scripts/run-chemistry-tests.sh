#!/usr/bin/env bash

set -euo pipefail

: "${CHEMISTRY_URL:?CHEMISTRY_URL must point to the OSS chemistry service}"
: "${JDBC_DB:?JDBC_DB must name the test database}"
: "${JDBC_URL:?JDBC_URL must point to the test database server}"
: "${RS_FILE_BASE:?RS_FILE_BASE must point to the test filestore}"

chemistry_ready=false
for _ in $(seq 1 30); do
  if curl --silent --output /dev/null "${CHEMISTRY_URL}"; then
    chemistry_ready=true
    break
  fi
  sleep 2
done

if [[ "${chemistry_ready}" != "true" ]]; then
  echo "Chemistry service did not become ready at ${CHEMISTRY_URL}" >&2
  exit 1
fi

./mvnw clean integration-test -DskipUnitTests=true -Denvironment=drop-recreate-db \
  -DincludedTestGroups=chemistry -DexcludedTestGroups= \
  -Dchemistry.provider=indigo -Dchemistry.service.url="${CHEMISTRY_URL}" \
  -Djava-version="${MAVEN_TOOLCHAIN_JAVA_VERSION:-17}" \
  -Djava-vendor="${MAVEN_TOOLCHAIN_JAVA_VENDOR:-openjdk}" \
  -Djavax.xml.accessExternalDTD=all \
  -Dlog4j2.configurationFile=log4j2-dev.xml \
  -Djdbc.db.maven="${JDBC_DB}" -Djdbc.url="${JDBC_URL}" \
  -Dmaven.test.failure.ignore=false -DRS.devlogLevel=INFO \
  -DRS_FILE_BASE="${RS_FILE_BASE}"
