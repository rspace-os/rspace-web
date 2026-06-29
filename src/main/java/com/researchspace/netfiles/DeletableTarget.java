package com.researchspace.netfiles;

/**
 * A filestore object resolved for deletion: the exact backend key to delete plus its audit metadata
 * for the delete gate. Returned by {@link WritableNfsClient#resolveDeletableTarget(String)} so the
 * target is resolved once; the caller gates {@link #audit()} then deletes {@link #objectKey()} via
 * {@link WritableNfsClient#deleteByKey(String)} without re-resolving. The key is opaque to callers.
 *
 * @param objectKey the backend key to delete (e.g. an empty folder's placeholder key ending {@code
 *     /})
 * @param audit the object's creator/creation-time metadata
 */
public record DeletableTarget(String objectKey, FilestoreAuditMetadata audit) {}
