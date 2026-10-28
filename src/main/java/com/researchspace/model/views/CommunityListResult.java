package com.researchspace.model.views;

import java.io.Serializable;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Value;

/** Simple POJO for communities listing */
@Value
@EqualsAndHashCode(of = {"id"})
public class CommunityListResult implements Serializable {

  private Long id;
  private String displayName;

  /** */
  private static final long serialVersionUID = 8845841673385216492L;

  /**
   * Parses "name &lt;id&gt;, name2 &lt;id2&gt;" values from the autocomplete box. Ids may be
   * negative, as in the "All Groups &lt;-1&gt;" entry.
   */
  public static Set<Long> getCommunityIdsfromMultiGroupAutocomplete(String input) {
    return AutocompleteIdList.parseIds(input, true);
  }

  public static boolean validateMultiCommunityAutocompleteInput(String input) {
    return AutocompleteIdList.isValid(input, true);
  }
}
