package com.researchspace.testutils;

import com.researchspace.testutils.InventoryTestSample.InventorySamplePost;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

public abstract class InventoryTestSample {

  @Data
  public static class InventorySamplePost {
    private Long id;
    private String name, description, tags;
    private QuantityPost quantity;
    private Long templateId;
    private int newSampleSubSamplesCount = 1;
    private List<? extends EmptyFieldPost> fields = new ArrayList<EmptyFieldPost>();
  }

  /** Use for empty fields where data is not being specified. */
  public static class EmptyFieldPost {}

  @Getter
  @AllArgsConstructor
  public static class QuantityPost {
    private Integer unitId = 3;
    private Integer numericValue;

    public QuantityPost(Integer numericValue) {
      super();
      this.numericValue = numericValue;
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = false)
  public static class InventoryFieldPost extends EmptyFieldPost {
    private String name, content, type;
  }

  @AllArgsConstructor
  @Value
  public static class FormRef {
    private Long id;
  }

  protected abstract InventorySamplePost toInventorySamplePost(Long formId);
}
