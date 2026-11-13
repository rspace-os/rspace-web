package com.researchspace.service.resourceaccess;

import com.researchspace.model.User;
import java.util.List;

/** Transactional resource-scoped grantee directory. */
public interface ResourceAccessDirectoryManager {

  /** Searches principals after rechecking assignment-management access to the resource. */
  <T, ID> List<ResourceGranteeDirectoryEntry> searchForResource(
      ProtectedResourceAccess<T, ID> resource, ID id, String query, int limit, User subject);
}
