# Gradle stack details

The app image installs JBR 17 and HotswapAgent 2.0.1. The bind-mounted wrapper
runs `jettyLaunchArgs --no-daemon` on JBR under `C.UTF-8`, then execs
`java @/var/rspace-build/app/jetty/launch.args`. Jetty is PID 1 and receives SIGTERM directly.
Its shutdown hook stops Spring and Jetty; Compose allows 120 seconds.
No Gradle process remains after startup. Set `ORG_GRADLE_PROJECT_jettyJvmArgs` in
`docker/dev/.env` for its heap or extra JVM arguments, separated by whitespace.
The entrypoint appends JDWP and HotswapAgent flags unless disabled.

Gradle state uses per-stack volumes under `/var/rspace-build`, outside the
source bind mount. The entrypoint builds from an external project view with
links to source and copies of Gradle settings/build scripts. BuildSrc uses its
default `.gradle` there. An init script sets both build directories.
Both builds use separate default `.gradle` cache volumes in the external view.
Passing `--project-cache-dir` to Gradle 9.8 also shares execution history with
buildSrc, causing colliding task names and unnecessary output replacement.
The external defaults avoid that collision. Host builds can run concurrently. `down` retains them; `nuke` removes them and legacy `app-target`.
First boot downloads Gradle and dependencies per stack. Budget 8 GB during
builds; steady usage is lower without a resident Gradle daemon. Only pnpm is
shared. The launcher notices unused `rspace-dev-m2-repo` without deleting it,
and warns to move old `MAVEN_OPTS` to `ORG_GRADLE_PROJECT_jettyJvmArgs`.

`reload` invokes the entrypoint's `--compile` path. It runs `classes` with the
same environment and resource-filter settings as startup, then writes a line
to `/tmp/jetty.in` to redeploy the webapp. A single-use daemon reuses the startup project cache; no Gradle process
is alive while Jetty runs. Watched classes and resources remain volume-backed.
A failed compile does not signal redeploy. File-watch autoHotswap stays disabled;
IDE hot code replace over JDWP changes individual classes without redeployment.

First boot and `up --fresh` select `drop-recreate-db`, which runs Gradle's
`recreateDatabase` before Jetty. Later `up` and `restart` use `keepdbintact`.
Chemistry, E2E mock and Mailpit flags reach both resource filtering and Jetty
through the same command-line system properties.

Manual redeploy invalidates sessions, as Maven did; existing sessions redirect
to login. SIGTERM during redeploy waits for Spring startup, then shuts down.
Compose permits 120 seconds for slower shared machines. Reload is not an
atomic deployment: changed classes may briefly disappear during compilation,
and changed resource inputs can replace resource outputs.
