package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.office.OfficeUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class LibreOfficeSandboxTest {

  @TempDir Path directory;

  @Test
  void terminationKillsRestartsUntilManagerStopCompletes() throws Exception {
    var current = new AtomicReference<ProcessHandle>();
    var scope =
        new LibreOfficeSandbox.RequestProcesses(
            directory, () -> Optional.ofNullable(current.get()).stream().toList());
    scope.start(() -> {}).get(1, TimeUnit.SECONDS);
    var stopEntered = new CountDownLatch(1);
    var allowStop = new CountDownLatch(1);
    var stopped =
        scope.stop(
            () -> {
              stopEntered.countDown();
              try {
                allowStop.await();
              } catch (InterruptedException e) {
                LoggerFactory.getLogger(getClass()).warn("Test stop interrupted", e);
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
              }
            });
    try {
      assertTrue(stopEntered.await(1, TimeUnit.SECONDS));
      var first = requestProcess();
      current.set(first);
      var terminated = scope.terminate();
      verify(first, timeout(1000).atLeastOnce()).destroyForcibly();
      current.set(null);
      assertThrows(TimeoutException.class, () -> terminated.get(100, TimeUnit.MILLISECONDS));
      var restarted = requestProcess();
      current.set(restarted);
      verify(restarted, timeout(1000).atLeastOnce()).destroyForcibly();
      assertFalse(terminated.isDone());
      allowStop.countDown();
      stopped.get(1, TimeUnit.SECONDS);
      terminated.get(1, TimeUnit.SECONDS);
    } finally {
      allowStop.countDown();
    }
  }

  private ProcessHandle requestProcess() {
    var process = mock(ProcessHandle.class);
    var info = mock(ProcessHandle.Info.class);
    when(process.info()).thenReturn(info);
    when(info.arguments()).thenReturn(Optional.of(new String[] {directory.toString()}));
    when(process.descendants()).thenAnswer(invocation -> Stream.empty());
    when(process.onExit()).thenReturn(CompletableFuture.completedFuture(process));
    return process;
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void cancellationWaitsForLateStartupAndKillsItsProcessBeforeRelease() throws Exception {
    var scope = new LibreOfficeSandbox.RequestProcesses(directory);
    var allowStartup = new CountDownLatch(1);
    var launched = new AtomicReference<Process>();
    var startup =
        scope.start(
            () -> {
              try {
                allowStartup.await();
                launched.set(
                    new ProcessBuilder("sh", "-c", "sleep 30 & wait", directory.toString())
                        .start());
              } catch (Exception e) {
                LoggerFactory.getLogger(getClass()).warn("Test startup failed", e);
                throw new CompletionException(e);
              }
            });
    var stopped = scope.stop(() -> assertNotNull(launched.get()));
    var terminated = scope.terminate();
    var released = terminated.thenRun(() -> assertFalse(launched.get().isAlive()));
    try {
      assertFalse(terminated.isDone());
      assertFalse(released.isDone());
      assertFalse(stopped.isDone());
      allowStartup.countDown();
      startup.get(2, TimeUnit.SECONDS);
      terminated.get(2, TimeUnit.SECONDS);
      assertFalse(launched.get().isAlive());
      released.get(2, TimeUnit.SECONDS);
    } finally {
      allowStartup.countDown();
      if (launched.get() != null) {
        launched.get().destroyForcibly();
      }
    }
  }

  @Test
  void failingManagerStopStillCompletesBeforeDeadline() throws Exception {
    var scope = new LibreOfficeSandbox.RequestProcesses(directory);
    var manager = mock(OfficeManager.class);
    doThrow(new IllegalStateException("stop failed")).when(manager).stop();
    try (var deadline = new ConversionDeadline(Duration.ofSeconds(5))) {
      var stopped = scope.stop(() -> OfficeUtils.stopQuietly(manager));
      assertDoesNotThrow(() -> stopped.get(1, TimeUnit.SECONDS));
      assertDoesNotThrow(deadline::check);
      verify(manager).stop();
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void cancellationKillsRelaunchedProcessesAndRetainsPermitUntilStopCompletes() throws Exception {
    var scope = new LibreOfficeSandbox.RequestProcesses(directory);
    var allowStop = new CountDownLatch(1);
    var stopEntered = new CountDownLatch(1);
    var released = new AtomicBoolean();
    Process first = new ProcessBuilder("sh", "-c", "read line", directory.toString()).start();
    Process replacement = null;
    try (var deadline = new ConversionDeadline(Duration.ofSeconds(10))) {
      scope.start(() -> {}).get(1, TimeUnit.SECONDS);
      scope.stop(
          () -> {
            stopEntered.countDown();
            try {
              allowStop.await();
            } catch (InterruptedException e) {
              LoggerFactory.getLogger(getClass()).warn("Test stop interrupted", e);
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(stopEntered.await(1, TimeUnit.SECONDS));
      var terminated = scope.terminate();
      deadline.cleanupBeforeRelease(terminated);
      var permitReleased = new CountDownLatch(1);
      deadline.releaseAfterCleanup(
          () -> {
            released.set(true);
            permitReleased.countDown();
          });
      first.onExit().get(2, TimeUnit.SECONDS);
      assertFalse(terminated.isDone());
      assertFalse(released.get());

      replacement = new ProcessBuilder("sh", "-c", "read line", directory.toString()).start();
      replacement.onExit().get(2, TimeUnit.SECONDS);
      assertFalse(released.get(), "The permit must remain held until the manager stops");
      allowStop.countDown();
      terminated.get(2, TimeUnit.SECONDS);
      assertFalse(replacement.isAlive());
      assertTrue(permitReleased.await(1, TimeUnit.SECONDS));
      assertTrue(released.get());
    } finally {
      allowStop.countDown();
      first.destroyForcibly();
      if (replacement != null) {
        replacement.destroyForcibly();
      }
    }
  }

  @Test
  void isolatesNetworkAndBindsOnlyRequestState() throws Exception {
    Path executable = directory.resolve("bwrap");
    Files.createFile(executable).toFile().setExecutable(true);
    Path request = directory.resolve("request");
    Files.createDirectories(request);
    var properties =
        new ConverterProperties(
            Path.of("/office"),
            directory,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            2,
            1024,
            executable);

    List<String> command = new LibreOfficeSandbox(properties).commandPrefix(request);

    assertTrue(command.contains("--unshare-all"));
    assertTrue(command.contains("--clearenv"));
    assertEquals(2, command.stream().filter("--bind"::equals).count());
    assertTrue(command.contains("/tmp"));
    assertTrue(command.contains(request.resolve("ipc").toAbsolutePath().toString()));
    assertEquals(
        request.resolve("ipc").toAbsolutePath().toString(),
        command.get(command.indexOf("/tmp") - 1));
    assertTrue(command.contains("/proc"));
    assertTrue(command.indexOf("/tmp") < command.indexOf(request.toAbsolutePath().toString()));
    assertTrue(command.contains(request.toAbsolutePath().toString()));
    assertTrue(command.contains(request.resolve("tmp").toAbsolutePath().toString()));
    assertTrue(command.contains("/lib64"));
    assertTrue(command.contains("/usr/lib/libreoffice/program"));
  }
}
