package com.researchspace.extmessages.msteams;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** A set of name/value facts rendered as a two-column list in an Adaptive Card. */
@Data
class FactSet implements CardElement {
  private String type = "FactSet";
  private List<CardFact> facts = new ArrayList<>();

  void add(String title, String value) {
    facts.add(new CardFact(title, value));
  }
}
