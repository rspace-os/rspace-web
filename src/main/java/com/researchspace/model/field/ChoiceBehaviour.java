package com.researchspace.model.field;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

public class ChoiceBehaviour {

  public ChoiceBehaviour() {}

  public List<String> getChoiceOptionSelectedAsList(Supplier<String> fieldDataSupplier) {
    List<String> result = new ArrayList<>();
    String data = fieldDataSupplier.get();
    if (StringUtils.isBlank(data)) {
      return result;
    }
    if (fieldDataSupplier.get().isEmpty()) {
      result.add("");
    } else {
      String[] options = fieldDataSupplier.get().split("&");

      for (String v : options) {
        result.add(v.split("=")[1]);
      }
    }
    return result;
  }

  /**
   * Gets comma-separated list of values from string in a=b&a=c&a=d format, would return b,c,d
   *
   * @param fieldDataSupplier
   * @return
   */
  public String getChoiceOptionSelectedAsString(Supplier<String> fieldDataSupplier) {
    String result = "";
    boolean first = true;

    if (!fieldDataSupplier.get().isEmpty()) {
      String[] options = fieldDataSupplier.get().split("&");
      for (String v : options) {
        if (first) {
          result = v.split("=")[1];
          first = false;
        } else {
          result += ", " + v.split("=")[1];
        }
      }
    }
    return result;
  }

  /**
   * Whether a Choice field's options show as checkboxes rather than radio buttons. A
   * single-selection field that already holds several values keeps checkboxes, so no value is lost.
   *
   * @param multipleChoice whether the field allows several values
   * @param selectedCount how many values the field holds (stored values, or defaults on a form)
   */
  static boolean showsCheckboxes(boolean multipleChoice, int selectedCount) {
    return multipleChoice || selectedCount > 1;
  }

  public static ChoiceFieldForm realOrProxy(IFieldForm ft) {
    if (ft instanceof ChoiceFieldForm) {
      return (ChoiceFieldForm) ft;
    } else if (AbstractField.isAuditingProxy(ft)) {
      return (new ChoiceFieldForm());
    } else {
      throw new IllegalStateException();
    }
  }
}
