package com.researchspace.service.inventory.csvimport;

import com.researchspace.model.inventory.field.InventoryDateField;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.inventory.field.InventoryTimeField;
import com.researchspace.model.inventory.field.InventoryUriField;
import com.researchspace.model.inventory.field.SampleField;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

/** Recognise potential sample field type from provided values. */
@Component
public class InventoryImportSampleFieldCreator {

  /** Maximum length of a value for which field type other than 'text' will be suggested */
  public static final int MAX_NON_TEXT_LENGTH = 100;

  /** Maximum number of distinct values for which field type 'radio' will be suggested */
  public static final int MAX_RADIO_OPTIONS = 20;

  /** Ratio of distinct values to all values below which the radio type is suggested. */
  public static final double RADIO_REPEATING_OPTIONS_RATIO = 0.76;

  public SampleField getSuggestedSampleFieldForNameAndValues(String name, List<String> values) {

    List<String> nonEmptyValues =
        values.stream().filter(opt -> StringUtils.isNotBlank(opt)).collect(Collectors.toList());
    Set<String> valueSet = new HashSet<>(nonEmptyValues);

    // default field type (string) if only empty/blank values
    if (valueSet.isEmpty()) {
      return new InventoryStringField(name);
    }

    // text
    String longestValue = Collections.max(valueSet, Comparator.comparing(String::length));
    if (longestValue.length() > MAX_NON_TEXT_LENGTH) {
      return new InventoryTextField(name);
    }
    // number
    if (isSuggestedFieldForValues(valueSet, new InventoryNumberField())) {
      return new InventoryNumberField(name);
    }
    // date
    if (isSuggestedFieldForValues(valueSet, new InventoryDateField())) {
      return new InventoryDateField(name);
    }
    // uri
    if (isSuggestedFieldForValues(valueSet, new InventoryUriField())) {
      return new InventoryUriField(name);
    }
    // time
    if (isSuggestedFieldForValues(valueSet, new InventoryTimeField())) {
      return new InventoryTimeField(name);
    }
    // radio
    if (shouldRadioTypeBeSuggested(name, nonEmptyValues, valueSet)) {
      InventoryRadioFieldDef radioDef = new InventoryRadioFieldDef();

      radioDef.setRadioOptionsList(calculateRadioOptions(valueSet));
      InventoryRadioField field = new InventoryRadioField(radioDef, name);
      return field;
    }

    // default (string)
    return new InventoryStringField(name);
  }

  private boolean isSuggestedFieldForValues(Set<String> values, SampleField field) {
    return values.stream().allMatch(v -> field.isSuggestedFieldForData(v));
  }

  private boolean shouldRadioTypeBeSuggested(
      String name, List<String> nonEmptyValues, Set<String> valueSet) {
    return (valueSet.size() > 1)
        && (valueSet.size() < MAX_RADIO_OPTIONS)
        && isEnoughRepeatingValuesForSuggestingRadioType(nonEmptyValues, valueSet)
        && noProblematicRadioValues(name, valueSet);
  }

  private boolean isEnoughRepeatingValuesForSuggestingRadioType(
      List<String> nonEmptyValues, Set<String> valueSet) {
    return (((double) valueSet.size()) / nonEmptyValues.size()) < RADIO_REPEATING_OPTIONS_RATIO;
  }

  private boolean noProblematicRadioValues(String name, Set<String> valueSet) {
    // '&' and '=' are used as separators when storing radio field content
    return Stream.concat(valueSet.stream(), Stream.of(name))
        .allMatch(v -> !v.contains("=") && !v.contains("&"));
  }

  /**
   * @return sorted list of unique values, for possible radio options
   */
  public List<String> calculateRadioOptions(Set<String> fieldValues) {
    List<String> uniqueValuesList = new ArrayList<>(fieldValues);
    List<String> radioOptionList =
        uniqueValuesList.stream()
            .filter(opt -> !StringUtils.isBlank(opt))
            .sorted()
            .collect(Collectors.toList());
    return radioOptionList;
  }

  /**
   * Finds an RSUnitDef that is compatible with all the quantity string values passed in a list.
   *
   * @return quantity unit compatible for a list of quantity strings, or null if common value cannot
   *     be determined
   */
  public RSUnitDef getCommonQuantityUnit(List<String> fieldValues) {
    RSUnitDef result = null;
    for (String value : fieldValues) {
      if (!StringUtils.isBlank(value)) {
        try {
          QuantityInfo parsedQuantity = QuantityInfo.of(value);
          RSUnitDef parsedUnit = RSUnitDef.getUnitById(parsedQuantity.getUnitId());
          if (result == null) {
            result = parsedUnit;
          } else if (!result.isComparable(parsedUnit)) {
            return null; // uncomparable units across values
          }
        } catch (IllegalArgumentException e) {
          return null; // at least one non-quantity value
        }
      }
    }
    return result;
  }
}
