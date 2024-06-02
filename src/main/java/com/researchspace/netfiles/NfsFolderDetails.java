package com.researchspace.netfiles;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NfsFolderDetails extends NfsResourceDetails {

  private List<NfsResourceDetails> content = new ArrayList<>();

  public NfsFolderDetails() {
    setType(TYPE_FOLDER);
  }

  public NfsFolderDetails(String name) {
    this();
    setName(name);
  }
}
