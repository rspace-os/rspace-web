package com.researchspace.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds the rootless bubblewrap prefix used for every LibreOffice process. */
@Component
final class LibreOfficeSandbox {

  private final Path executable;
  private final Path ipcDirectory;

  LibreOfficeSandbox(ConverterProperties properties) {
    executable = properties.sandboxExecutable();
    ipcDirectory = properties.ipcDirectory();
    if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
      throw new IllegalStateException("The LibreOffice sandbox launcher is unavailable");
    }
    try {
      Files.createDirectories(ipcDirectory);
      if (Files.getFileStore(ipcDirectory).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(ipcDirectory, PosixFilePermissions.fromString("rwx------"));
      }
    } catch (IOException e) {
      throw new IllegalStateException("The LibreOffice sandbox IPC directory is unavailable", e);
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
            "--dir",
            "/etc",
            "--ro-bind",
            "/etc/fonts",
            "/etc/fonts",
            "--proc",
            "/proc",
            "--dev",
            "/dev",
            "--tmpfs",
            "/tmp"));
    addDirectoryParents(command, request);
    addDirectoryParents(command, ipcDirectory);
    command.addAll(
        List.of(
            "--bind",
            request.toString(),
            request.toString(),
            "--bind",
            ipcDirectory.toString(),
            ipcDirectory.toString(),
            "--setenv",
            "HOME",
            request.resolve("home").toString(),
            "--setenv",
            "TMPDIR",
            ipcDirectory.toString(),
            "--setenv",
            "LANG",
            "C.UTF-8",
            "--setenv",
            "LC_ALL",
            "C.UTF-8",
            "--setenv",
            "SAL_USE_VCLPLUGIN",
            "svp",
            "--chdir",
            request.toString(),
            "--"));
    return command;
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
