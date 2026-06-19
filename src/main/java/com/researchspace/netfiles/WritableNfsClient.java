package com.researchspace.netfiles;

import com.researchspace.api.v1.model.ApiExternalStorageOperationInfo;
import com.researchspace.api.v1.model.ApiExternalStorageOperationResult;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Sub-interface of {@link NfsClient} implemented by backends that support write operations (upload,
 * copy between buckets, delete). Code that needs to perform writes should check {@link
 * NfsClient#supportsWrite()} and cast to this type.
 */
public interface WritableNfsClient extends NfsClient {

  @Override
  default boolean supportsWrite() {
    return true;
  }

  /**
   * Returns whether this backend supports server-side object transfer. When true, {@link
   * #copyObject(String, WritableNfsClient, String)} can execute without streaming data through the
   * RSpace server. Defaults to {@code false}; backends that implement server-side copy (e.g. S3)
   * override this.
   */
  default boolean supportsServerSideTransfer() {
    return false;
  }

  /**
   * Uploads a single file to the destination directory on the remote filestore. The remote name is
   * taken from {@code source.getName()}.
   *
   * @param source the local file to upload
   * @param destDirectoryPath the destination directory on the remote filestore
   * @return the external-storage id of the uploaded object (e.g. the S3 key, or an iRODS data
   *     object id)
   */
  String uploadFile(File source, String destDirectoryPath) throws IOException;

  /**
   * Deletes a single object at the given absolute path on the remote filestore.
   *
   * @param absolutePath absolute path of the object to delete
   */
  void deleteFile(String absolutePath) throws IOException;

  /**
   * Creates a folder at the given absolute path, attaching the given audit metadata (e.g. {@code
   * rspace-created-by} / {@code rspace-created-at}). Default throws — only backends that support
   * folder creation (e.g. S3, via a zero-byte placeholder object) override this.
   *
   * @param absolutePath absolute path of the folder to create
   * @param metadata audit metadata to attach to the folder
   * @return the external-storage id of the created folder
   */
  default String createFolder(String absolutePath, Map<String, String> metadata)
      throws IOException {
    throw new UnsupportedOperationException(
        "Folder creation is not supported by this filestore backend");
  }

  /**
   * Moves a file or empty folder to another location within the same filestore, preserving its leaf
   * name (i.e. moves it <em>into</em> {@code destFolderAbsolutePath}). Only files and empty folders
   * may be moved; implementations reject non-empty folders. Default throws — only backends that
   * support within-filestore moves (e.g. S3) override this.
   *
   * @param sourceAbsolutePath absolute path of the file/empty folder to move
   * @param destFolderAbsolutePath absolute path of the destination folder
   * @return the external-storage id of the moved object at its new location
   */
  default String moveWithin(String sourceAbsolutePath, String destFolderAbsolutePath)
      throws IOException {
    throw new UnsupportedOperationException(
        "Within-filestore move is not supported by this filestore backend");
  }

  /**
   * Resolves the single object a delete of {@code absolutePath} would remove (a file, or an empty
   * folder's placeholder) into its backend key and audit metadata, so the caller can gate then
   * delete without re-resolving. Throws {@link IOException} if the target is a non-empty folder
   * (only empty folders may be deleted) or does not exist. Default throws {@link
   * UnsupportedOperationException} — only backends supporting gated delete (e.g. S3) override this.
   *
   * @param absolutePath absolute path of the file or empty folder targeted for deletion
   */
  default DeletableTarget resolveDeletableTarget(String absolutePath) throws IOException {
    throw new UnsupportedOperationException("Delete is not supported by this filestore backend");
  }

  /**
   * Deletes the object with the exact backend key obtained from {@link #resolveDeletableTarget}.
   * The caller is expected to have authorized the deletion first. Default throws — only backends
   * supporting delete (e.g. S3) override this.
   *
   * @param objectKey the exact backend key to delete
   */
  default void deleteByKey(String objectKey) throws IOException {
    throw new UnsupportedOperationException("Delete is not supported by this filestore backend");
  }

  /**
   * Server-side copies a single object from this filestore to a destination filestore. For S3 this
   * uses {@code CopyObject} (no data flows through the RSpace server). Implementations may throw
   * {@link UnsupportedOperationException} if the destination backend is incompatible (e.g. S3
   * source with non-S3 destination).
   *
   * @param sourceAbsolutePath absolute path of the source object on this filestore
   * @param destClient the destination filestore client
   * @param destAbsolutePath absolute path of the destination object on the destination filestore
   * @return the external-storage id of the copied object on the destination
   */
  String copyObject(
      String sourceAbsolutePath, WritableNfsClient destClient, String destAbsolutePath)
      throws IOException;

  /**
   * Variant of {@link #copyObject(String, WritableNfsClient, String)} that carries audit
   * attribution metadata. Default ignores metadata and delegates to the no-metadata variant — only
   * backends with native metadata support (e.g. S3) override this.
   */
  default String copyObject(
      String sourceAbsolutePath,
      WritableNfsClient destClient,
      String destAbsolutePath,
      Map<String, String> metadata)
      throws IOException {
    return copyObject(sourceAbsolutePath, destClient, destAbsolutePath);
  }

  /**
   * Batch upload a set of files to the destination directory on the remote filestore. Default
   * implementation iterates and calls {@link #uploadFile} per item, capturing per-file successes
   * and failures.
   *
   * @param destinationPath destination directory on the remote filestore
   * @param mapRecordIdToFile map of RSpace record id to the local file to upload
   * @return per-file outcomes
   */
  default ApiExternalStorageOperationResult uploadFilesToNfs(
      String destinationPath, Map<Long, File> mapRecordIdToFile) {
    ApiExternalStorageOperationResult result = new ApiExternalStorageOperationResult();
    for (Map.Entry<Long, File> entry : mapRecordIdToFile.entrySet()) {
      Long recordId = entry.getKey();
      File file = entry.getValue();
      try {
        uploadFile(file, destinationPath);
        result.add(new ApiExternalStorageOperationInfo(recordId, null, file.getName(), true));
      } catch (Exception e) {
        result.add(
            new ApiExternalStorageOperationInfo(recordId, file.getName(), false, e.getMessage()));
      }
    }
    return result;
  }

  /**
   * Variant of {@link #uploadFilesToNfs(String, Map)} that carries audit attribution (RSpace user +
   * operation), letting backends attach per-record metadata to written objects. Default
   * implementation ignores attribution and delegates to the no-metadata batch — only backends with
   * native metadata support (e.g. S3) override this.
   */
  default ApiExternalStorageOperationResult uploadFilesToNfs(
      String destinationPath, Map<Long, File> mapRecordIdToFile, WriteAttribution attribution) {
    return uploadFilesToNfs(destinationPath, mapRecordIdToFile);
  }

  /**
   * Batch delete a set of absolute paths from the remote filestore. Default implementation calls
   * {@link #deleteFile} per item; returns true if all succeeded, throws on the first failure.
   *
   * @param absolutePathFilenames absolute paths to delete
   * @return true when all deletions succeeded
   */
  default boolean deleteFilesFromNfs(Set<String> absolutePathFilenames) {
    for (String absolutePath : absolutePathFilenames) {
      try {
        deleteFile(absolutePath);
      } catch (IOException e) {
        throw new UnsupportedOperationException(
            "The file " + absolutePath + " could not be deleted", e);
      }
    }
    return true;
  }
}
