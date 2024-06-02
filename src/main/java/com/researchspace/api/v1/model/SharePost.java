package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ValidSharePost
public class SharePost {

  @Size(min = 1, max = 255, message = "There must be at least 1 document or notebook to share")
  @Singular("itemToShare")
  private List<Long> itemsToShare = new ArrayList<>();

  @Valid
  @Singular
  @JsonProperty("groups")
  private List<GroupSharePostItem> groupSharePostItems = new ArrayList<>();

  @Valid
  @Singular
  @JsonProperty("users")
  private List<UserSharePostItem> userSharePostItems = new ArrayList<>();
}
