package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.collection.AccessDocumentation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiV2OpenApiAccessPoliciesTest {

  private static Map<String, Object> uses(AccessDocumentation... policies) {
    Map<String, Object> node = new LinkedHashMap<>();
    for (int i = 0; i < policies.length; i++) {
      node.put(
          "use" + i,
          new LinkedHashMap<>(
              Map.of("x-rspace-access", ApiV2OpenApiAccessPolicies.extension(policies[i]))));
    }
    return node;
  }

  @Test
  void referencesEachPolicyByItsDeclaredName() {
    AccessDocumentation read = new AccessDocumentation("ItemRead", "Readers.", Set.of("b", "a"));
    Map<String, Object> node = uses(read, read);
    ApiV2OpenApiComponents components = new ApiV2OpenApiComponents();

    ApiV2OpenApiAccessPolicies.share(node, components);

    Map<String, Object> document = new LinkedHashMap<>();
    components.addTo(document);
    assertEquals(
        Map.of(
            "ItemRead",
            Map.of(
                "description",
                "Readers.",
                "authentication",
                "AUTHENTICATED",
                "denialReasonCodes",
                List.of("a", "b"))),
        document.get(ApiV2OpenApiAccessPolicies.SECTION));
    assertEquals(
        Map.of("x-rspace-access", Map.of("$ref", "#/components/x-rspace-access-policies/ItemRead")),
        node.get("use1"));
  }

  @Test
  void rejectsTwoPoliciesDeclaringOneName() {
    Map<String, Object> node =
        uses(
            new AccessDocumentation("ItemRead", "Readers.", Set.of()),
            new AccessDocumentation("ItemRead", "Other readers.", Set.of()));
    ApiV2OpenApiComponents components = new ApiV2OpenApiComponents();

    assertThrows(
        IllegalArgumentException.class, () -> ApiV2OpenApiAccessPolicies.share(node, components));
  }
}
