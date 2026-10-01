package com.researchspace.dao.resourceaccess;

import com.researchspace.model.User;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded database search for principals eligible to receive a new resource role. */
public interface ResourceAccessDirectoryDao {

  List<ResourceAccessDirectoryRow> search(String query, int limit, User subject);

  /** Resolves the requested keys only when they fall inside the subject's assignment directory. */
  Map<String, ResourceAccessDirectoryRow> resolveAssignable(Set<String> keys, User subject);
}
