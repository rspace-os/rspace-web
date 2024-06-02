package com.researchspace.model.dtos;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import java.util.List;
import lombok.Data;

/** Holds backing data for the user-to-create command */
@Data
public class SignUpUserBySysAdminCommand {

  private User user;

  private List<Group> availableGroups;

  private Long selectedGroupId;

  public SignUpUserBySysAdminCommand(User user, List<Group> availableGroups) {
    this.user = user;
    this.availableGroups = availableGroups;
  }

  public SignUpUserBySysAdminCommand() {}
}
