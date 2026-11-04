# Gradle stack details

The app image installs JBR 17 and HotswapAgent 2.0.1. The bind-mounted wrapper
runs `jettyRun` on JBR under `C.UTF-8`; no local Java or Gradle is required.
Jetty runs in a separate JVM. Set `ORG_GRADLE_PROJECT_jettyJvmArgs` in
`docker/dev/.env` for its heap or extra JVM arguments, separated by whitespace.
The entrypoint appends JDWP and HotswapAgent flags unless disabled.

`~/.gradle` is writable in the shared external `rspace-dev-gradle-cache` volume.
Project `.gradle/` and `build/` stay on the bind mount and survive `down` and
`nuke`. Avoid simultaneous host and container builds in this worktree.

`reload` invokes the entrypoint's `--compile` path. It runs `classes` with the
same resource-filter settings as startup and `keepdbintact`, then writes a line
to `/tmp/jetty.in` to redeploy the webapp. A single-use daemon with the separate
`.gradle/dev-compile` project cache avoids the cache lock held by `jettyRun`.
A failed compile does not signal redeploy. File-watch autoHotswap stays disabled;
IDE hot code replace over JDWP changes individual classes without redeployment.

First boot and `up --fresh` select `drop-recreate-db`, which runs Gradle's
`recreateDatabase` before Jetty. Later `up` and `restart` use `keepdbintact`.
Chemistry, E2E mock and Mailpit flags reach both resource filtering and Jetty
through the same command-line system properties.
