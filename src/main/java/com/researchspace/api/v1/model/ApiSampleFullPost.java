package com.researchspace.api.v1.model;

import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A posted sample together with the context its cross-field rules need: who is creating it and,
 * where it is template-based, the template it must conform to. The target of {@code
 * SampleApiPostFullValidator}.
 *
 * <p>Lives here rather than nested in {@code SamplesApiController} because the same conformance
 * check runs inside the operations transaction, in {@code service.inventory}: a nested controller
 * class would make the service layer import the controller layer (parallel review, L1).
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
