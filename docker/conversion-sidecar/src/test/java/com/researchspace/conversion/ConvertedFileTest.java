package com.researchspace.conversion;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConvertedFileTest {
  @TempDir Path directory;

  @Test
  void refusesToStreamOutputThatIsASymbolicLink() throws IOException {
    Path secret = Files.writeString(directory.resolve("outside.txt"), "not for the caller");
    Path request = Files.createDirectory(directory.resolve("request"));
    Path output = Files.createSymbolicLink(request.resolve("output.html"), secret);

    try (var converted = new ConvertedFile(request, output, "text/html")) {
      assertThrows(IOException.class, () -> converted.transferTo(new ByteArrayOutputStream()));
    }
  }
}
