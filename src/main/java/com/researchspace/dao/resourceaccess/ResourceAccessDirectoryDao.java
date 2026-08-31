package com.researchspace.dao.resourceaccess;

import com.researchspace.model.User;
import com.researchspace.service.resourceaccess.ResourceGranteeDirectoryEntry;
import java.util.List;

/** Bounded database search for principals eligible to receive a new resource role. */
public interface ResourceAccessDirectoryDao {

  List<ResourceGranteeDirectoryEntry> search(String query, int limit, User subject);
}
