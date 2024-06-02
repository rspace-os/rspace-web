package com.researchspace.model.dtos;

import lombok.Data;

/** Encapsulates request parameters for a RunAs command. */
@Data
public class RunAsUserCommand {

  private String sysadminPassword;

  private String runAsUsername;

  private boolean incognito = false; // default = not icognito
}
