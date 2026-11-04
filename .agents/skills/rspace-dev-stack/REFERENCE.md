# Gradle stack details

The app image installs JBR 17 and HotswapAgent 2.0.1. The bind-mounted wrapper
runs `jettyLaunchArgs --no-daemon` on JBR under `C.UTF-8`, then execs
`java @build/jetty/launch.args`. Jetty is PID 1 and receives SIGTERM directly.
Its shutdown hook stops Spring and Jetty; Compose allows 60 seconds.
No Gradle process remains after startup. Set `ORG_GRADLE_PROJECT_jettyJvmArgs` in
`docker/dev/.env` for its heap or extra JVM arguments, separated by whitespace.
The entrypoint appends JDWP and HotswapAgent flags unless disabled.

Gradle home and `/rspace/build`, `/rspace/.gradle`, `/rspace/buildSrc/build`
and `/rspace/buildSrc/.gradle` use per-stack volumes. Host builds can run
concurrently. `down` retains them; `nuke` removes them and legacy `app-target`.
First boot downloads Gradle and dependencies per stack. Budget 8 GB during
builds; steady usage is lower without a resident Gradle daemon. Only pnpm is
shared. The launcher notices unused `rspace-dev-m2-repo` without deleting it,
and warns to move old `MAVEN_OPTS` to `ORG_GRADLE_PROJECT_jettyJvmArgs`.

`reload` invokes the entrypoint's `--compile` path. It runs `classes` with the
same resource-filter settings as startup and `keepdbintact`, then writes a line
to `/tmp/jetty.in` to redeploy the webapp. A single-use daemon reuses the startup project cache; no Gradle process
is alive while Jetty runs. Watched classes and resources remain volume-backed.
A failed compile does not signal redeploy. File-watch autoHotswap stays disabled;
IDE hot code replace over JDWP changes individual classes without redeployment.

First boot and `up --fresh` select `drop-recreate-db`, which runs Gradle's
`recreateDatabase` before Jetty. Later `up` and `restart` use `keepdbintact`.
Chemistry, E2E mock and Mailpit flags reach both resource filtering and Jetty
through the same command-line system properties.
