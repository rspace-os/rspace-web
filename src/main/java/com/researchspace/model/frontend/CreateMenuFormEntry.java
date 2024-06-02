package com.researchspace.model.frontend;

import com.researchspace.model.record.RSForm;
import lombok.Data;
import lombok.NonNull;

/**
 * Data about recent / popular forms that should show up in the Workspace 'Create' menu. Converted
 * to JSON.
 */
@Data
public class CreateMenuFormEntry {
  private static final String ICON_URL_BASE = "/image/getIconImage/";

  @NonNull private String name;
  @NonNull private Long id;
  @NonNull private String iconURL;

  public static CreateMenuFormEntry fromRSForm(RSForm form) {
    return new CreateMenuFormEntry(form.getName(), form.getId(), ICON_URL_BASE + form.getIconId());
  }
}
