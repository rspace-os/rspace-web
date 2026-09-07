package com.researchspace.dao.resourceaccess;

import com.researchspace.model.resourceaccess.ResourceGranteeKind;

/** Safe principal data returned by the resource-access directory query. */
public record ResourceAccessDirectoryRow(
    ResourceGranteeKind kind, Long id, String key, String name, String detail) {}
