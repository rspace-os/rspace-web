package com.researchspace.conversion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds the rootless bubblewrap prefix used for every LibreOffice process. */
@Component
final class LibreOfficeSandbox {

  private final Path executable;

  LibreOfficeSandbox(ConverterProperties properties) {
    executable = properties.sandboxExecutable();
    if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
      throw new IllegalStateException("The LibreOffice sandbox launcher is unavailable");
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
            // JODConverter and LibreOffice must share this hard-coded UNO socket location.
            "--bind",
            "/tmp",
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
