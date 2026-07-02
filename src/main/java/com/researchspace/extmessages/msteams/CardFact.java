package com.researchspace.extmessages.msteams;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single fact (title/value pair) within a {@link FactSet}. */
@Data
@AllArgsConstructor
@NoArgsConstructor
class CardFact {
  private String title;
  private String value;
}
