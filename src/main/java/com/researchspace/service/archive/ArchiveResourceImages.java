package com.researchspace.service.archive;

import com.researchspace.core.util.MediaUtils;
import com.researchspace.model.EcatImage;
import com.researchspace.service.MediaFileContentValidator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Finds the images an XML archive stores in its {@code resources} folder, such as legacy emoticons
 * that the exporter rewrote to {@code ../resources/<filename>}, and remembers which of them one
 * import has already saved to the Gallery. Create one instance per import; it is not thread-safe.
 */
class ArchiveResourceImages {

  static final String RESOURCES_SRC_PREFIX = "../" + ExportImport.RESOURCES + "/";

  // matched literally and never percent-decoded: one bare filename, no separators, query or
  // fragment
  private static final Pattern RESOURCE_SRC =
      Pattern.compile(Pattern.quote(RESOURCES_SRC_PREFIX) + "([^/\\\\?#]+)");

  private final Path extractionRoot;
  private final Map<Path, EcatImage> importedByRealPath = new HashMap<>();

  ArchiveResourceImages(Path extractionRoot) {
    this.extractionRoot = extractionRoot.toAbsolutePath().normalize();
  }

  /**
   * Resolves an image {@code src} to a regular file in the {@code resources} folder beside the
   * record folder, inside the extracted archive.
   *
   * @return the file's real path, or empty if the reference or the file is not acceptable
   */
  Optional<Path> resolve(Path recordFolder, String src) {
    Matcher matcher = RESOURCE_SRC.matcher(src);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    String fileName = matcher.group(1);
    if (fileName.equals(".") || fileName.equals("..")) {
      return Optional.empty();
    }
    Path recordParent = recordFolder.toAbsolutePath().normalize().getParent();
    if (recordParent == null) {
      return Optional.empty();
    }
    Path resourcesDir = recordParent.resolve(ExportImport.RESOURCES);
    Path file = resourcesDir.resolve(fileName);
    if (!file.startsWith(extractionRoot) || hasSymbolicLinkBelowRoot(file)) {
      return Optional.empty();
    }
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      return Optional.empty();
    }
    try {
      Path realFile = file.toRealPath();
      if (realFile.startsWith(resourcesDir.toRealPath())
          && realFile.startsWith(extractionRoot.toRealPath())) {
        return Optional.of(realFile);
      }
    } catch (IOException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private boolean hasSymbolicLinkBelowRoot(Path file) {
    Path current = extractionRoot;
    for (Path part : extractionRoot.relativize(file)) {
      current = current.resolve(part);
      if (Files.isSymbolicLink(current)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether the file has an image extension the Gallery stores as an image, content matching that
   * extension, and decodes completely. Any failure, including a decoder runtime exception, counts
   * as not an image.
   */
  static boolean isDecodableImage(Path file) {
    String fileName = file.getFileName().toString();
    if (!MediaUtils.IMAGES_MEDIA_FLDER_NAME.equals(MediaUtils.extractFileTypeFromPath(fileName))) {
      return false;
    }
    try (InputStream in = Files.newInputStream(file)) {
      InputStream checked = MediaFileContentValidator.verifyContentMatchesExtension(in, fileName);
      return ImageIO.read(checked) != null;
    } catch (IOException | RuntimeException e) {
      return false;
    }
  }

  Optional<EcatImage> getImported(Path realPath) {
    return Optional.ofNullable(importedByRealPath.get(realPath));
  }

  void putImported(Path realPath, EcatImage image) {
    importedByRealPath.put(realPath, image);
  }
}
