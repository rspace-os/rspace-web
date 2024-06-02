package com.researchspace.model.dtos;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import lombok.Value;

@Value
public class SwapPiCommand {
  private User currPi, newPi;
  private Group group;
}
