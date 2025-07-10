package com.researchspace.webapp.integrations.ascenscia.dto;

import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@Data
public class ConnectDTO {
  @NotBlank(message = "Username cannot be blank")
  private String username;

  @NotBlank(message = "Password cannot be blank")
  private String password;

  @NotBlank(message = "Org cannot be blank")
  private String organization;
}
