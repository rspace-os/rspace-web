package com.researchspace.api.v1.model;

import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The target of {@code SampleApiPostFullValidator}.
 *
 * <p>Lives here rather than nested in {@code SamplesApiController} because the same conformance
 * check runs inside the operations transaction, in {@code service.inventory}: a nested controller
 * class would make the service layer import the controller layer.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiSampleFullPost {
  ApiSampleWithFullSubSamples apiSample;
  User user;
  // may be null
  SampleTemplate template;
}
