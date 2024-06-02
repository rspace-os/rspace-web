package com.researchspace.model.dtos;

import com.researchspace.model.AccessControl;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.RSForm;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;

@Data
public class FormSharingCommand implements Serializable {

  /** */
  private static final long serialVersionUID = 1L;

  private List<String> groupAvailableOptions =
      Arrays.asList(
          new String[] {
            PermissionType.NONE.toString(),
            PermissionType.READ.toString(),
            PermissionType.WRITE.toString(),
          });
  private List<String> worldAvailableOptions =
      Arrays.asList(
          new String[] {
            PermissionType.NONE.toString(), PermissionType.READ.toString(),
          });
  private List<String> groupOptions = new ArrayList<String>();
  private List<String> worldOptions = new ArrayList<String>();
  private Long formId;

  public FormSharingCommand() {
    super();
  }

  /**
   * Constructor to convert persisted access controls to the command object.
   *
   * @param form
   */
  public FormSharingCommand(RSForm form) {
    AccessControl ac = form.getAccessControl();

    setGroupOptions(Arrays.asList(new String[] {ac.getGroupPermissionType().toString()}));
    setWorldOptions(Arrays.asList(new String[] {ac.getWorldPermissionType().toString()}));
    this.formId = form.getId();
  }

  /**
   * Generates entity object from this configuration object
   *
   * @return
   */
  public AccessControl toAccessControl() {
    AccessControl ac = new AccessControl();
    if (getGroupOptions().size() > 0) {
      ac.setGroupPermissionType(PermissionType.valueOf(getGroupOptions().get(0)));
    }
    if (getWorldOptions().size() > 0) {
      ac.setWorldPermissionType(PermissionType.valueOf(getWorldOptions().get(0)));
    }
    return ac;
  }
}
