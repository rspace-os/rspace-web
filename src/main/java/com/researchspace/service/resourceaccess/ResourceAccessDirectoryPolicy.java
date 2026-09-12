package com.researchspace.service.resourceaccess;

import com.researchspace.dao.resourceaccess.ResourceAccessDirectoryDao;
import com.researchspace.dao.resourceaccess.ResourceAccessDirectoryRow;
import com.researchspace.model.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Shared principal-directory policy used by access searches and assignment writes. */
@Component
public class ResourceAccessDirectoryPolicy {

  private final ResourceAccessDirectoryDao directory;

  public ResourceAccessDirectoryPolicy(ResourceAccessDirectoryDao directory) {
    this.directory = directory;
  }

  public List<ResourceGranteeDirectoryEntry> search(String query, int limit, User subject) {
    return directory.search(query, limit, subject).stream()
        .map(ResourceAccessDirectoryPolicy::entry)
        .toList();
  }

  /** Resolves only keys that the subject may currently assign. */
  public Map<String, ResourceGranteeDirectoryEntry> resolveAssignable(
      Set<String> keys, User subject) {
    return directory.resolveAssignable(keys, subject).entrySet().stream()
        .collect(
            java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, row -> entry(row.getValue())));
  }

  private static ResourceGranteeDirectoryEntry entry(ResourceAccessDirectoryRow row) {
    return new ResourceGranteeDirectoryEntry(
        row.kind(), row.id(), row.key(), row.name(), row.detail());
  }
}
