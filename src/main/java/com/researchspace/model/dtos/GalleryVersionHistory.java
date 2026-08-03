package com.researchspace.model.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The version history of a Gallery item: every audit revision of it, newest revision id last.
 *
 * <p>Deliberately shaped like the inventory revisions response ({@code
 * ApiInventoryRecordRevisionList}) so that the frontend's shared version-grouping helper consumes
 * both unchanged. See {@code DevDocs/adr/0003-gallery-version-history-endpoint.md}.
 *
 * <p>Callers must group by {@link Item#version()} rather than treat one revision as one version:
 * several revisions can share a version, because not every recorded change bumps the user-facing
 * counter.
 */
public record GalleryVersionHistory(List<Revision> revisions, int revisionsCount) {

  /** One audit revision of a Gallery item. */
  public record Revision(
      long revisionId,
      String revisionType,
      /* named "record" on the wire to match the inventory response; "record" is a restricted
       * identifier in Java, so the component itself is named differently */
      @JsonProperty("record") Item item) {}

  /**
   * The state of the Gallery item at one revision.
   *
   * <p>{@code name} and {@code description} are per-revision, not per-item: uploading a new version
   * can replace the file with a differently named one, and either can be edited at any time. Only
   * the item's identity is shared across versions.
   */
  public record Item(
      Long version,
      String lastModified,
      String modifiedByFullName,
      long size,
      String name,
      String description) {}
}
