package com.researchspace.b2inst.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The community-submission request object, returned by the review and submit calls.
 *
 * <p>Creating the review ({@code PUT /api/records/{id}/draft/review}) returns it in
 * {@code "created"} status carrying the {@code submit} action link the connector
 * extracts from {@code links.actions.submit}. Posting that submit link
 * returns the same object in {@code "submitted"} status with the
 * curator actions ({@code accept}, {@code decline}, {@code cancel}) instead.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class B2instRequestResponse {

  /** Request id (a UUID). */
  @JsonProperty("id")
  private String id;

  /** Creation timestamp (ISO-8601). */
  @JsonProperty("created")
  private String created;

  /** Last-update timestamp (ISO-8601). */
  @JsonProperty("updated")
  private String updated;

  /** Who created the request. */
  @JsonProperty("created_by")
  private B2instCreatedBy createdBy;

  /** Who receives the request (the target community). */
  @JsonProperty("receiver")
  private B2instRequestReceiver receiver;

  /** What the request is about (the draft record). */
  @JsonProperty("topic")
  private B2instRequestTopic topic;

  /** Optimistic-concurrency revision counter. */
  @JsonProperty("revision_id")
  private Integer revisionId;

  /** Request type, here {@code "community-submission"}. */
  @JsonProperty("type")
  private String type;

  /** Human-readable title (often empty for community submissions). */
  @JsonProperty("title")
  private String title;

  /** Per-receiver sequential request number. */
  @JsonProperty("number")
  private String number;

  /** Lifecycle status, for example {@code "created"} or {@code "submitted"}. */
  @JsonProperty("status")
  private String status;

  /** Whether the request is closed. */
  @JsonProperty("is_closed")
  private Boolean isClosed;

  /** Whether the request is open (active). */
  @JsonProperty("is_open")
  private Boolean isOpen;

  /** Whether the request has expired. */
  @JsonProperty("is_expired")
  private Boolean isExpired;

  /** Expiry timestamp, or {@code null}. */
  @JsonProperty("expires_at")
  private String expiresAt;

  /** Links for the request, including the available state-transition actions. */
  @JsonProperty("links")
  private B2instRequestLinks links;
}
