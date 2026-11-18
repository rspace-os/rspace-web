package com.researchspace.model.field.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks the display rules of a whole form or template at once, so that rules referencing fields
 * created, changed or deleted in the same save are judged on the result.
 *
 * <p>The TypeScript validator in {@code modules/common/field-rules} returns the same codes; both
 * run the shared vectors in {@code src/test/resources/fieldrules}.
 */
public final class FieldDisplayRuleValidator {

  /** Message code prefix for display rule errors. */
  public static final String CODE_PREFIX = "validation.fieldDisplayRule.";

  public static final String KEY_INVALID = CODE_PREFIX + "keyInvalid";
  public static final String KEY_DUPLICATE = CODE_PREFIX + "keyDuplicate";
  public static final String SOURCE_MISSING = CODE_PREFIX + "sourceMissing";
  public static final String SOURCE_SELF = CODE_PREFIX + "sourceSelf";
  public static final String SOURCE_TYPE = CODE_PREFIX + "sourceType";
  public static final String VALUE_UNKNOWN = CODE_PREFIX + "valueUnknown";
  public static final String CYCLE = CODE_PREFIX + "cycle";

  private static final Pattern UUID =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  /**
   * One problem with one field.
   *
   * @param fieldIndex the field's position in the validated list
   * @param code a message code, one of the constants on {@link FieldDisplayRuleValidator}
   */
  public record RuleError(int fieldIndex, String code) {}

  private FieldDisplayRuleValidator() {}

  /**
   * Validates every key and rule in the field set. Keys may be null (legacy fields), but non-null
   * keys must be lowercase UUIDs and unique. Each rule's source must exist, differ from the field
   * itself, be a radio or choice field and offer every value the rule names, and rules must not
   * form a cycle.
   *
   * @return the problems found, in field order; empty when the set is valid
   */
  public static List<RuleError> validate(List<RuleField> fields) {
    List<RuleError> errors = new ArrayList<>();
    Map<String, Integer> indexByKey = new HashMap<>();
    Set<String> duplicateKeys = new HashSet<>();
    for (int i = 0; i < fields.size(); i++) {
      String key = fields.get(i).key();
      if (key != null && indexByKey.putIfAbsent(key, i) != null) {
        duplicateKeys.add(key);
      }
    }
    boolean[] onCycle = onCycle(fields, indexByKey);

    for (int i = 0; i < fields.size(); i++) {
      RuleField field = fields.get(i);
      String key = field.key();
      if (key != null && !UUID.matcher(key).matches()) {
        errors.add(new RuleError(i, KEY_INVALID));
      } else if (key != null && duplicateKeys.contains(key)) {
        errors.add(new RuleError(i, KEY_DUPLICATE));
      }
      FieldDisplayRule rule = field.rule();
      if (rule == null) {
        continue;
      }
      Integer sourceIndex = indexByKey.get(rule.sourceFieldKey());
      if (sourceIndex == null) {
        errors.add(new RuleError(i, SOURCE_MISSING));
      } else if (sourceIndex == i) {
        errors.add(new RuleError(i, SOURCE_SELF));
      } else {
        RuleField source = fields.get(sourceIndex);
        if (!source.canBeSource()) {
          errors.add(new RuleError(i, SOURCE_TYPE));
        } else if (!source.allowedOptions().containsAll(rule.values())) {
          errors.add(new RuleError(i, VALUE_UNKNOWN));
        }
        if (onCycle[i]) {
          errors.add(new RuleError(i, CYCLE));
        }
      }
    }
    return errors;
  }

  /**
   * Which fields lie on a cycle of rule sources. Each rule has one source, so following sources
   * from a field either ends or loops; every field is walked once, so this runs in linear time.
   */
  private static boolean[] onCycle(List<RuleField> fields, Map<String, Integer> indexByKey) {
    final int unvisited = 0;
    final int onWalk = 1;
    final int done = 2;
    int[] state = new int[fields.size()];
    boolean[] onCycle = new boolean[fields.size()];
    for (int start = 0; start < fields.size(); start++) {
      if (state[start] != unvisited) {
        continue;
      }
      List<Integer> walk = new ArrayList<>();
      int current = start;
      while (true) {
        state[current] = onWalk;
        walk.add(current);
        FieldDisplayRule rule = fields.get(current).rule();
        Integer next = rule == null ? null : indexByKey.get(rule.sourceFieldKey());
        if (next == null || state[next] == done) {
          break;
        }
        if (state[next] == onWalk) {
          for (int i = walk.indexOf(next); i < walk.size(); i++) {
            onCycle[walk.get(i)] = true;
          }
          break;
        }
        current = next;
      }
      for (int index : walk) {
        state[index] = done;
      }
    }
    return onCycle;
  }
}
