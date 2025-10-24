package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.service.SnippetService;
import com.researchspace.auth.PermissionUtils;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Snippet;
import com.researchspace.service.RecordManager;
import org.springframework.stereotype.Service;

@Service
public class SnippetServiceImpl implements SnippetService {

  private final RecordManager recordManager;
  private final PermissionUtils permissionUtils;

  public SnippetServiceImpl(RecordManager recordManager, PermissionUtils permissionUtils) {
    this.recordManager = recordManager;
    this.permissionUtils = permissionUtils;
  }

  @Override
  public Snippet getSnippet(long id, User user) {
    Snippet snippet = recordManager.getAsSubclass(id, Snippet.class);
    permissionUtils.assertIsPermitted(snippet, PermissionType.READ, user, "read snippet");
    return snippet;
  }
}
