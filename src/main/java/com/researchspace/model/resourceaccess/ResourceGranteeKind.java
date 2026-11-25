package com.researchspace.model.resourceaccess;

import io.swagger.v3.oas.annotations.media.Schema;

/** The supported kinds of principals that can receive a resource role. */
@Schema(enumAsRef = true)
public enum ResourceGranteeKind {
  USER,
  GROUP,
  AUDIENCE
}
