package com.researchspace.extmessages.msteams;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** A text element of an Adaptive Card. Supports a markdown subset, including links. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
class TextBlock implements CardElement {
  private String type = "TextBlock";
  private String text;
  private String weight;
  private String size;
  private boolean wrap = true;

  /** A bold, large heading line. */
  static TextBlock heading(String text) {
    TextBlock block = new TextBlock();
    block.setText(text);
    block.setWeight("Bolder");
    block.setSize("Large");
    return block;
  }

  /** A standard body line. */
  static TextBlock body(String text) {
    TextBlock block = new TextBlock();
    block.setText(text);
    return block;
  }
}
