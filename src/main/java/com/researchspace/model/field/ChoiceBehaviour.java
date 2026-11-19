package com.researchspace.model.field;

import com.researchspace.model.field.rule.ElnChoiceValues;
import java.util.List;
import java.util.function.Supplier;

public class ChoiceBehaviour {

  public ChoiceBehaviour() {}

  /** Selected choices stored as {@code name=a&name=b}; see {@link ElnChoiceValues#parse}. */
  public List<String> getChoiceOptionSelectedAsList(Supplier<String> fieldDataSupplier) {
    return ElnChoiceValues.parse(fieldDataSupplier.get());
  }

  /**
   * Gets comma-separated list of values from string in a=b&a=c&a=d format, would return b, c, d
   *
   * @param fieldDataSupplier
   * @return
   */
  public String getChoiceOptionSelectedAsString(Supplier<String> fieldDataSupplier) {
    return String.join(", ", ElnChoiceValues.parse(fieldDataSupplier.get()));
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
