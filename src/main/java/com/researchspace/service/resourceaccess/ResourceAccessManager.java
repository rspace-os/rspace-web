package com.researchspace.service.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import java.util.Collection;
import java.util.Map;

/** Generic role resolution and transactional assignment management for protected resources. */
public interface ResourceAccessManager {

  /** Resolves one aggregate for the represented subject. */
  ResolvedResourceAccess resolve(ResourceAccess access, User subject);

  /** Resolves a bounded collection without ambient-session identity. */
  Map<Long, ResolvedResourceAccess> resolveAll(Collection<ResourceAccess> accesses, User subject);

  /** Reads an access document when the subject has the adapter's view capability. */
  <T, ID> ResourceAccessDocument get(ProtectedResourceAccess<T, ID> resource, ID id, User subject);

  /** Re-authorizes and replaces all persisted assignments under the resource lock. */
  <T, ID> ResourceAccessDocument replace(
      ProtectedResourceAccess<T, ID> resource,
      ReplaceResourceAccess<ID> command,
      User subject,
      User actor);

  /**
   * Atomically promotes the incoming user to direct Owner and removes the outgoing user's direct
   * Owner assignment. A lower outgoing direct role and every unrelated assignment are preserved.
   */
  <T, ID> void transferDirectOwnership(
      ProtectedResourceAccess<T, ID> resource,
      ID id,
      User outgoingOwner,
      User incomingOwner,
      User subject,
      User actor);

  /** Re-authorizes and removes the subject's direct assignment through the explicit leave path. */
  <T, ID> void removeSelf(
      ProtectedResourceAccess<T, ID> resource,
      RemoveSelfResourceAccess<ID> command,
      User subject,
      User actor);
}
