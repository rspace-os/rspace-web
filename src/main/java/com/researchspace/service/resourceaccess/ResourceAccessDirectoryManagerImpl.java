package com.researchspace.service.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default grantee-directory policy and transaction boundary. */
@Service
@Transactional(readOnly = true)
public class ResourceAccessDirectoryManagerImpl implements ResourceAccessDirectoryManager {

  private final ResourceAccessDirectoryPolicy directory;
  private final ResourceAccessManager accessManager;

  public ResourceAccessDirectoryManagerImpl(
      ResourceAccessDirectoryPolicy directory, ResourceAccessManager accessManager) {
    this.directory = directory;
    this.accessManager = accessManager;
  }

  @Override
  public <T, ID> List<ResourceGranteeDirectoryEntry> searchForResource(
      ProtectedResourceAccess<T, ID> resource, ID id, String query, int limit, User subject) {
    if (!resource.featureEnabled(subject)) {
      throw new ResourceAccessException(ResourceAccessException.Reason.NOT_FOUND);
    }
    T protectedEntity =
        resource
            .find(id)
            .orElseThrow(
                () -> new ResourceAccessException(ResourceAccessException.Reason.NOT_FOUND));
    ResourceAccess aggregate = resource.access(protectedEntity);
    ResolvedResourceAccess access = accessManager.resolve(aggregate, subject);
    if (!access.hasCapability(ResourceRoleScheme.READ_RESOURCE_CAPABILITY)) {
      throw new ResourceAccessException(ResourceAccessException.Reason.NOT_FOUND);
    }
    if (!access.hasCapability(resource.manageAssignmentsCapability())) {
      throw new ResourceAccessException(ResourceAccessException.Reason.FORBIDDEN);
    }
    return directory.search(query, limit, subject);
  }

  @Override
  public List<ResourceGranteeDirectoryEntry> searchForSettings(
      String query, int limit, User subject) {
    if (subject == null || !subject.isEnabled() || !subject.hasSysadminRole()) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    return directory.search(query, limit, subject);
  }
}
