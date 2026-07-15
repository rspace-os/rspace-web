package com.researchspace.service.metadata;

import lombok.Value;

/** The composed sidecar: its target filename and the serialized content. */
@Value
public class GeneratedSidecar {
  String filename;
  String content;
}
