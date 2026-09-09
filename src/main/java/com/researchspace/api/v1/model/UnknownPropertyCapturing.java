package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

/**
 * An API object that records the names of request properties it does not declare, instead of
 * letting Jackson drop them silently.
 *
 * <p>The API's ObjectMapper is built by {@code Jackson2ObjectMapperBuilder}, which turns {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} off, so an unrecognised key never reaches a validator: after binding,
 * a key that was dropped is indistinguishable from an optional property the caller omitted. That is
 * fine for endpoints making no strictness promise, and wrong for one whose contract is that the
 * request conforms to an operation definition, where a mistyped property means the operation
 * performed differently from what was asked with no indication why.
 *
 * <p>Capturing is therefore separated from rejecting. Every DTO in an operations request body
 * captures, which is inert: the bound values are unchanged, the list is never serialized, and it is
 * excluded from equals and toString. Only {@code InventoryOperationPostValidator} rejects what was
 * captured, so no other endpoint binding these shared DTOs changes behaviour.
 */
public interface UnknownPropertyCapturing {

  /**
   * How many unrecognised names one object records. Each captured name becomes a field error, so an
   * uncapped list would let a body of junk keys amplify into an unbounded error response; a caller
   * who has sent this many typos does not need the rest enumerated.
   */
  int MAX_CAPTURED_UNKNOWN_PROPERTIES = 10;

  /**
   * Names of properties present in the request body that match no property of this object, in the
   * order Jackson met them. Empty for a conformant request.
   */
  @JsonIgnore
  List<String> getUnknownProperties();

  /**
   * Records one unrecognised name, up to the cap. Shared so the cap is defined once rather than
   * once per implementing DTO.
   */
  static void capture(List<String> captured, String name) {
    if (captured.size() < MAX_CAPTURED_UNKNOWN_PROPERTIES) {
      captured.add(name);
    }
  }
}
