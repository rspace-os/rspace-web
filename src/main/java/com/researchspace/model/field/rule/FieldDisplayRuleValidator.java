package com.researchspace.model.field.rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checks the display rules of a whole form or template at once, so that rules referencing fields
 * created, changed or deleted in the same save are judged on the result.
 *
 * <p>The TypeScript validator in {@code modules/common/field-rules} returns the same codes; both
 * run the shared vectors in {@code src/test/resources/fieldrules}.
 */
public final class FieldDisplayRuleValidator {

  public static final String KEY_INVALID = "validation.fieldDisplayRule.keyInvalid";
  public static final String KEY_DUPLICATE = "validation.fieldDisplayRule.keyDuplicate";
  public static final String SOURCE_MISSING = "validation.fieldDisplayRule.sourceMissing";
  public static final String SOURCE_SELF = "validation.fieldDisplayRule.sourceSelf";
  public static final String SOURCE_TYPE = "validation.fieldDisplayRule.sourceType";
  public static final String VALUE_UNKNOWN = "validation.fieldDisplayRule.valueUnknown";
  public static final String VALUE_DUPLICATE = "validation.fieldDisplayRule.valueDuplicate";
  public static final String VALUE_TOO_MANY = "validation.fieldDisplayRule.valueTooMany";
  public static final String RULE_TOO_LONG = "validation.fieldDisplayRule.ruleTooLong";
  public static final String CYCLE = "validation.fieldDisplayRule.cycle";
  public static final String OPERATOR_NOT_FOR_SOURCE =
      "validation.fieldDisplayRule.operatorNotForSource";
  public static final String VALUE_COUNT = "validation.fieldDisplayRule.valueCount";
  public static final String VALUE_BLANK = "validation.fieldDisplayRule.valueBlank";
  public static final String VALUE_NOT_A_NUMBER = "validation.fieldDisplayRule.valueNotANumber";

  /**
   * The most values one rule may list. Far more than any real radio or choice field offers, and it
   * keeps the per-save work small.
   */
  public static final int MAX_VALUES = 1000;

  /**
   * The longest a rule may be as stored, in characters of {@link FieldDisplayRule#toJson()}. That
   * output is plain ASCII, so this is also its size in bytes. Inventory stores the rule in a
   * MariaDB TEXT column, which holds 65,535 bytes, so half of that leaves ample headroom there. ELN
   * stores it in a LONGTEXT column, which has no practical limit; the same bound applies so that a
   * rule valid in one is valid in the other.
   */
  public static final int MAX_JSON_LENGTH = 32_768;

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
   * keys must be lowercase UUIDs and unique. Each rule must list at most {@link #MAX_VALUES} values
   * and fit in {@link #MAX_JSON_LENGTH} characters as stored. Each rule's source must exist, differ
   * from the field itself and be able to be a source ({@link SourceKind#NONE} cannot), and the
   * operator must suit the source's kind. An option rule must list each value once and only options
   * the source offers. A text or number comparison must have exactly one value that is not blank,
   * and against a number source that value must be a plain decimal number. Rules must not form a
   * cycle.
   *
   * @return the problems found, in field order; empty when the set is valid
   */
  public static List<RuleError> validate(List<FieldSnapshot> fields) {
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
      FieldSnapshot field = fields.get(i);
      String key = field.key();
      if (key != null && !isCanonicalUuid(key)) {
        errors.add(new RuleError(i, KEY_INVALID));
      } else if (key != null && duplicateKeys.contains(key)) {
        errors.add(new RuleError(i, KEY_DUPLICATE));
      }
      FieldDisplayRule rule = field.rule();
      if (rule == null) {
        continue;
      }
      if (rule.values().size() > MAX_VALUES) {
        errors.add(new RuleError(i, VALUE_TOO_MANY));
      } else if (valuesLongerThanBudget(rule) || rule.toJson().length() > MAX_JSON_LENGTH) {
        errors.add(new RuleError(i, RULE_TOO_LONG));
      } else if (rule.operator().requiresValues()
          && new HashSet<>(rule.values()).size() < rule.values().size()) {
        errors.add(new RuleError(i, VALUE_DUPLICATE));
      }
      Integer sourceIndex = indexByKey.get(rule.sourceFieldKey());
      if (sourceIndex == null) {
        errors.add(new RuleError(i, SOURCE_MISSING));
      } else if (sourceIndex == i) {
        errors.add(new RuleError(i, SOURCE_SELF));
      } else {
        FieldSnapshot source = fields.get(sourceIndex);
        String problem = RuleValues.problem(rule, source.sourceKind());
        if (problem != null) {
          errors.add(new RuleError(i, problem));
        } else if (rule.operator().requiresValues()
            && !new HashSet<>(source.allowedOptions()).containsAll(rule.values())) {
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
  private static boolean[] onCycle(List<FieldSnapshot> fields, Map<String, Integer> indexByKey) {
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

  /**
   * Whether the values together are longer than {@link #MAX_JSON_LENGTH}, so the rule is too long
   * without serialising it (which escapes each non-ASCII unit to six characters). Each UTF-16 unit
   * is written as at least one character, so their total is a lower bound on the stored length.
   */
  private static boolean valuesLongerThanBudget(FieldDisplayRule rule) {
    return rule.values().stream().mapToLong(String::length).sum() > MAX_JSON_LENGTH;
  }

  /**
   * A key is valid only in canonical form: what {@link UUID#toString()} writes (lower case). Any
   * UUID version is accepted: RSpace generates version 7 ({@link FieldKeys}), but older databases
   * and API clients hold other versions.
   */
  private static boolean isCanonicalUuid(String key) {
    try {
      return UUID.fromString(key).toString().equals(key);
    } catch (IllegalArgumentException e) {
      // Not an error: this is how UUID reports "not a UUID", and the caller returns keyInvalid.
      return false;
    }
  }
}
