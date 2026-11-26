package com.researchspace.service;

/**
 * An annotated image in a text field whose Gallery image has no active link to the field, as
 * reported by {@link DevOpsManager#reportAnnotatedImagesInField(Long)} or repaired by {@link
 * DevOpsManager#relinkAnnotatedImagesInField(Long)}.
 *
 * @param documentGlobalId global id of the document containing the field
 * @param ownerUsername username of the document owner
 * @param fieldId id of the text field containing the annotated image
 * @param imageGlobalId global id of the Gallery image
 * @param linkState what was wrong with the link
 * @param outcome what was, or would be, done about it
 * @param skipReason why the link is not restored, when <code>outcome</code> is {@link
 *     Outcome#SKIPPED}; otherwise <code>null</code>
 */
public record AnnotatedImageRelink(
    String documentGlobalId,
    String ownerUsername,
    Long fieldId,
    String imageGlobalId,
    LinkState linkState,
    Outcome outcome,
    String skipReason) {

  /** State of the field/image link before any repair. */
  public enum LinkState {
    /** No FieldAttachment row exists for the field and image. */
    MISSING_ROW,
    /** FieldAttachment rows exist for the field and image, but all are flagged deleted. */
    DELETED_ROW
  }

  /** What happened to the link. */
  public enum Outcome {
    /** Report-only run: the link would be restored. */
    TO_REPAIR,
    /** The link was restored. */
    REPAIRED,
    /** The link is not restored; see the skip reason. */
    SKIPPED
  }
}
