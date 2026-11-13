package com.researchspace.api.v2.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.NullNode;
import java.io.IOException;

/** A JSON tree request body with a per-type mapper configuration. */
@JsonDeserialize(using = ApiV2JsonBody.Deserializer.class)
public record ApiV2JsonBody(JsonNode node) {
  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public ApiV2JsonBody {}

  @JsonValue
  public JsonNode node() {
    return node;
  }

  /** Keeps an explicit JSON null distinct from a missing HTTP body. */
  public static final class Deserializer extends JsonDeserializer<ApiV2JsonBody> {
    @Override
    public ApiV2JsonBody deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      return new ApiV2JsonBody(parser.readValueAsTree());
    }

    @Override
    public ApiV2JsonBody getNullValue(DeserializationContext context) {
      return new ApiV2JsonBody(NullNode.getInstance());
    }
  }
}
