package com.researchspace.model.dtos;

import lombok.Data;

@Data
public class UserRoleChangeCmnd {

  private Long userId;

  private String sysadminPassword;
}
