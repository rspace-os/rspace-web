package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.record.Snippet;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"id", "globalId", "name", "owner", "content", "_links"})
public class ApiSnippet extends IdentifiableNameableApiObject {
  private ApiUser owner;

  private String content;

  public ApiSnippet(Snippet snippet) {
    super(snippet.getId(), snippet.getGlobalIdentifier(), snippet.getName());
    this.owner = new ApiUser(snippet.getOwner());
    this.content = snippet.getContent();
  }
}
