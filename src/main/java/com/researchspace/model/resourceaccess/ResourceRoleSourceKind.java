package com.researchspace.model.resourceaccess;

import io.swagger.v3.oas.annotations.media.Schema;

/** How a subject receives one applicable resource role. */
@Schema(enumAsRef = true)
public enum ResourceRoleSourceKind {
  DIRECT,
  GROUP,
  AUDIENCE,
  IMPLICIT
}
