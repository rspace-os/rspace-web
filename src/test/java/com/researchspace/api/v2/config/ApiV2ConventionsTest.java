package com.researchspace.api.v2.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Checks conventions against the merged document consumed by clients. */
class ApiV2ConventionsTest {
  @Test
  void mergedContractFollowsV2Conventions() throws Exception {
    JsonNode document =
        new ObjectMapper()
            .readTree(Path.of("src/main/webapp/ui/src/__tests__/mocks/apiV2OpenApi.json").toFile());
    List<String> failures = new ArrayList<>();
    document
        .path("paths")
        .fields()
        .forEachRemaining(
            path -> {
              path.getValue()
                  .fields()
                  .forEachRemaining(
                      operation -> {
                        if (!Set.of("get", "post", "put", "patch", "delete", "head", "options")
                            .contains(operation.getKey())) return;
                        String label = operation.getKey() + " " + path.getKey();
                        List<JsonNode> parameters = new ArrayList<>();
                        path.getValue().path("parameters").forEach(parameters::add);
                        operation.getValue().path("parameters").forEach(parameters::add);
                        boolean ifMatch = false;
                        for (JsonNode entry : parameters) {
                          JsonNode parameter = resolve(document, entry);
                          String name = parameter.path("name").asText();
                          // Round 3 renames only the audit route's free-text parameter at layer 05.
                          if (!(path.getKey().matches("/api/v2/[^/]+/\\{id\\}/audit")
                                  && name.equals("search"))
                              && (Set.of("search", "query").contains(name)
                                  || name.matches(".*(Start|End|From|To)$"))) {
                            failures.add(label + ": parameter " + name);
                          }
                          ifMatch |= name.equalsIgnoreCase("If-Match");
                        }
                        JsonNode responses = operation.getValue().path("responses");
                        if (ifMatch && !(responses.has("412") && responses.has("428")))
                          failures.add(label + ": If-Match requires 412 and 428");
                        responses
                            .fields()
                            .forEachRemaining(
                                response -> {
                                  JsonNode resolved = resolve(document, response.getValue());
                                  if (response.getKey().equals("403")
                                      && path.getKey().contains("booking")
                                      && resolved
                                          .path("description")
                                          .asText()
                                          .toLowerCase(java.util.Locale.ROOT)
                                          .contains("unavailable"))
                                    failures.add(label + ": unavailable must be 404");
                                  resolved
                                      .path("content")
                                      .forEach(
                                          media -> {
                                            if (resolve(document, media.path("schema"))
                                                .path("type")
                                                .asText()
                                                .equals("array"))
                                              failures.add(
                                                  label + ": array response needs docs envelope");
                                          });
                                });
                      });
            });
    assertTrue(failures.isEmpty(), String.join("\n", failures));
  }

  private static JsonNode resolve(JsonNode document, JsonNode node) {
    return node.has("$ref") ? document.at(node.path("$ref").asText().substring(1)) : node;
  }
}
