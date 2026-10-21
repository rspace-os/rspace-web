package com.researchspace.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Builds the rootless bubblewrap prefix used for every LibreOffice process. */
@Component
class LibreOfficeSandbox {

  private static final Logger LOG = LoggerFactory.getLogger(LibreOfficeSandbox.class);

  private final Path executable;
  private final ConverterProperties properties;

  LibreOfficeSandbox(ConverterProperties properties) {
    this.properties = properties;
    executable = properties.sandboxExecutable();
    if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
      throw new IllegalStateException("The LibreOffice sandbox launcher is unavailable");
    }
  }

  boolean isReady() {
    try {
      Files.createDirectories(properties.workingDirectory());
      boolean filesReady =
          Files.isRegularFile(executable)
              && Files.isExecutable(executable)
              && Files.isDirectory(properties.officeHome())
              && Files.isWritable(properties.workingDirectory());
      if (!filesReady) {
        return false;
      }
      Process probe =
          new ProcessBuilder(
                  executable.toString(),
                  "--die-with-parent",
                  "--unshare-all",
                  "--ro-bind",
                  "/usr",
                  "/usr",
                  "--symlink",
                  "usr/bin",
                  "/bin",
                  "--symlink",
                  "usr/lib",
                  "/lib",
                  "--symlink",
                  "usr/lib64",
                  "/lib64",
                  "--ro-bind",
                  "/proc",
                  "/proc",
                  "--dev",
                  "/dev",
                  "--",
                  "/usr/bin/true")
              .redirectErrorStream(true)
              .start();
      boolean completed = probe.waitFor(2, TimeUnit.SECONDS);
      if (!completed) {
        probe.destroyForcibly();
      }
      return completed && probe.exitValue() == 0;
    } catch (RuntimeException | IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOG.warn("LibreOffice sandbox readiness check failed", e);
      return false;
    }
  }

  List<String> commandPrefix(Path requestDirectory) {
    Path request = requestDirectory.toAbsolutePath().normalize();
    List<String> command = new ArrayList<>();
    command.add(executable.toString());
    command.addAll(
        List.of(
            "--die-with-parent",
            "--new-session",
            "--unshare-all",
            "--clearenv",
            "--ro-bind",
            "/usr",
            "/usr",
            "--symlink",
            "usr/bin",
            "/bin",
            "--symlink",
            "usr/lib",
            "/lib",
            "--symlink",
            "usr/lib64",
            "/lib64",
            "--dir",
            "/etc",
            "--ro-bind",
            "/etc/fonts",
            "/etc/fonts",
            "--ro-bind",
            "/etc/libreoffice",
            "/etc/libreoffice",
            "--ro-bind",
            "/proc",
            "/proc",
            "--dev",
            "/dev",
            // Expose only this request's UNO socket directory inside the sandbox.
            "--bind",
            request.resolve("ipc").toString(),
            "/tmp"));
    addDirectoryParents(command, request);
    command.addAll(
        List.of(
            "--bind",
            request.toString(),
            request.toString(),
            "--setenv",
            "HOME",
            request.resolve("home").toString(),
            "--setenv",
            "TMPDIR",
            request.resolve("tmp").toString(),
            "--setenv",
            "LANG",
            "C.UTF-8",
            "--setenv",
            "LC_ALL",
            "C.UTF-8",
            "--setenv",
            "SAL_USE_VCLPLUGIN",
            "svp",
            "--setenv",
            "LD_LIBRARY_PATH",
            "/usr/lib/libreoffice/program",
            "--chdir",
            request.toString(),
            "--"));
    return command;
  }

  static final class RequestProcesses {
    private final Path request;
    private final Supplier<List<ProcessHandle>> descendants;
    private final Set<ProcessHandle> processes = new HashSet<>();

    private final CompletableFuture<Void> startupFinished = new CompletableFuture<>();
    private final CompletableFuture<Void> stopFinished = new CompletableFuture<>();
    private boolean startupSubmitted;
    private boolean stopSubmitted;
    private CompletableFuture<Void> termination;

    RequestProcesses(Path request) {
      this(request, () -> ProcessHandle.current().descendants().toList());
    }

    RequestProcesses(Path request, Supplier<List<ProcessHandle>> descendants) {
      this.request = request.toAbsolutePath().normalize();
      this.descendants = descendants;
    }

    synchronized CompletableFuture<Void> start(Runnable action) {
      startupSubmitted = true;
      return CompletableFuture.runAsync(
          () -> {
            try {
              action.run();
            } finally {
              startupFinished.complete(null);
            }
          },
          Thread::startVirtualThread);
    }

    synchronized CompletableFuture<Void> stop(Runnable action) {
      stopSubmitted = true;
      if (!startupSubmitted) {
        startupFinished.complete(null);
      }
      startupFinished.whenCompleteAsync(
          (ignored, failure) -> {
            try {
              action.run();
            } catch (RuntimeException e) {
              LOG.warn("Could not stop the LibreOffice manager", e);
            } finally {
              stopFinished.complete(null);
            }
          },
          Thread::startVirtualThread);
      return stopFinished;
    }

    synchronized void noMoreStarts() {
      if (!startupSubmitted) {
        startupFinished.complete(null);
      }
      if (!stopSubmitted) {
        stopFinished.complete(null);
      }
    }

    synchronized CompletableFuture<Void> terminate() {
      if (termination == null) {
        termination = new CompletableFuture<>();
        Thread.startVirtualThread(
            () -> {
              try {
                // JOD can restart after a lost connection until its manager has stopped.
                while (!startupFinished.isDone() || !stopFinished.isDone()) {
                  killProcesses();
                  Thread.sleep(10);
                }
                killProcesses()
                    .whenComplete(
                        (ignored, failure) -> {
                          if (failure == null) {
                            termination.complete(null);
                          } else {
                            LOG.error("Could not confirm request process termination", failure);
                            termination.completeExceptionally(failure);
                          }
                        });
              } catch (Exception e) {
                LOG.error("Could not terminate request processes", e);
                termination.completeExceptionally(e);
              }
            });
      }
      return termination;
    }

    private synchronized CompletableFuture<Void> killProcesses() {
      // Match a whole workspace argument or a path below it, never another request's prefix.
      var roots =
          descendants.get().stream()
              .filter(
                  process ->
                      Arrays.stream(process.info().arguments().orElse(new String[0]))
                          .anyMatch(
                              argument ->
                                  argument.equals(request.toString())
                                      || argument.contains(request + "/")))
              .toList();
      for (ProcessHandle root : roots) {
        var descendants = root.descendants().toList();
        processes.addAll(descendants);
        processes.add(root);
        descendants.reversed().forEach(ProcessHandle::destroyForcibly);
      }
      roots.forEach(ProcessHandle::destroyForcibly);
      processes.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
      return CompletableFuture.allOf(
          processes.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new));
    }
  }

  private static void addDirectoryParents(List<String> command, Path path) {
    Path current = path.toAbsolutePath().normalize().getRoot();
    for (Path part : path.toAbsolutePath().normalize()) {
      current = current.resolve(part);
      if (!current.equals(path)) {
        command.add("--dir");
        command.add(current.toString());
      }
    }
  }
}
