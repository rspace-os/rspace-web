package com.researchspace.service;

import com.researchspace.core.util.MediaUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;

/**
 * Checks that an uploaded file's bytes match the type claimed by its file extension, so that
 * arbitrary content cannot be stored and later served under a false content type.
 *
 * <p>Detection reads the leading bytes only; the client-supplied name and content type are never
 * trusted. The check is enforced for image extensions, which all map to formats with reliable magic
 * numbers. Other extension types (e.g. plain-text formats, chemistry files) have no reliable
 * signature and are not checked.
 *
 * <p>Because detection only inspects a prefix, a valid image carrying appended trailing content
 * still passes. Closing that gap needs the image to be re-encoded rather than inspected.
 */
public final class MediaFileContentValidator {

  private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();

  private MediaFileContentValidator() {}

  /**
   * Verifies the stream's leading bytes against the type implied by the extension of {@code
   * fileName}.
   *
   * @return the stream to continue reading the content from; the input may have been wrapped to
   *     make it rewindable, so callers must use the returned stream and not the original
   * @throws MediaContentMismatchException if the extension claims an image but the content is a
   *     different type
   */
  public static InputStream verifyContentMatchesExtension(InputStream inputStream, String fileName)
      throws IOException {
    String extension = FilenameUtils.getExtension(fileName);
    if (!MediaUtils.isImageFile(extension)) {
      return inputStream;
    }
    InputStream markable =
        inputStream.markSupported() ? inputStream : new BufferedInputStream(inputStream);
    MediaType detected = MIME_TYPES.detect(markable, new Metadata());
    MediaType expected = typeImpliedByExtension(extension);
    if (!expected.equals(detected)) {
      throw new MediaContentMismatchException(
          "errors.upload.imageContentMismatch", fileName, expected.toString(), detected.toString());
    }
    return markable;
  }

  /**
   * Resolves the extension through Tika's own name-based detection, which canonicalises equivalent
   * extensions (jpg and jpeg, tif and tiff) onto one type so those spellings are interchangeable.
   */
  private static MediaType typeImpliedByExtension(String extension) throws IOException {
    Metadata metadata = new Metadata();
    metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "upload." + extension);
    return MIME_TYPES.detect(null, metadata);
  }
}
