package com.researchspace.api.v1.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.researchspace.model.field.rule.FieldDisplayRule;
import java.io.IOException;

/**
 * Reads and writes a display rule in the API exactly as it is stored, so the API accepts the same
 * rules as the rule core and rejects the same malformed ones (unknown properties or operators,
 * values that do not suit the operator, non-string values).
 */
final class FieldDisplayRuleJson {

  private FieldDisplayRuleJson() {}

  static final class Serializer extends JsonSerializer<FieldDisplayRule> {
    @Override
    public void serialize(FieldDisplayRule rule, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeRawValue(rule.toJson());
    }
  }

  static final class Deserializer extends JsonDeserializer<FieldDisplayRule> {
    @Override
    public FieldDisplayRule deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      JsonNode node = parser.readValueAsTree();
      try {
        return FieldDisplayRule.fromJson(node);
      } catch (IllegalArgumentException e) {
        throw JsonMappingException.from(parser, e.getMessage(), e);
      }
    }
  }
}
