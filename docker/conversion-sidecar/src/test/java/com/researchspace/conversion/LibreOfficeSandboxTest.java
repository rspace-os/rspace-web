package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibreOfficeSandboxTest {

  @TempDir Path directory;

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
