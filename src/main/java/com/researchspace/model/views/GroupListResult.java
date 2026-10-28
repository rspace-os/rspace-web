package com.researchspace.model.views;

import java.io.Serializable;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** A simple POJO to hold some information about Groups for display in the UI */
@Getter
@Setter
@EqualsAndHashCode(of = {"displayName", "id"})
@ToString(of = {"displayName", "id"})
public class GroupListResult implements Serializable {

  /** */
  private static final long serialVersionUID = -8208692397022688326L;

  private Long id;
  private String displayName;
  private String piFullname;
  private String piAffiliation;

  /** Member count in group */
  private Integer groupSize = 0;

  /** Parses "name &lt;id&gt;, name2 &lt;id2&gt;" values from the autocomplete box. */
  public static Set<Long> getGroupIdsfromMultiGroupAutocomplete(String input) {
    return AutocompleteIdList.parseIds(input, false);
  }

  public static boolean validateMultiGroupAutocompleteInput(String input) {
    return AutocompleteIdList.isValid(input, false);
  }

  public GroupListResult(Long id, String displayName) {
    super();
    this.id = id;
    this.displayName = displayName;
  }
}
