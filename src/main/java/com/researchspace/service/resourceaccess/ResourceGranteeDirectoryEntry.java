package com.researchspace.service.resourceaccess;

import com.researchspace.model.resourceaccess.ResourceGranteeKind;

/** Safe user or group identity returned by an access-grantee directory search. */
public record ResourceGranteeDirectoryEntry(
    ResourceGranteeKind kind, Long id, String key, String name, String detail) {}
