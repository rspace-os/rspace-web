package com.researchspace.model.field.rule;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;

/**
 * Generates field keys, the identifiers display rules use to name their source field.
 *
 * <p>New keys are UUID version 7 (RFC 9562): a millisecond timestamp followed by random bits, so
 * they need no database host's node ID and sort roughly by creation time. Validation does not
 * depend on the version: {@link FieldDisplayRuleValidator} accepts any lowercase UUID, so keys made
 * by older versions of RSpace or by API clients keep working.
 */
public final class FieldKeys {

  /**
   * Thread-safe. Keys are time-ordered at millisecond resolution: within one millisecond the
   * generator increments the previous key's random bits rather than drawing new ones. Ordering is
   * not strictly guaranteed, because a system clock that steps back yields lower keys.
   */
  private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

  private FieldKeys() {}

  /** A new field key: a lowercase UUID version 7 string. */
  public static String newFieldKey() {
    return GENERATOR.generate().toString();
  }
}
