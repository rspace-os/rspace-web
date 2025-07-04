package com.researchspace.webapp.integrations.ascenscia;

import lombok.Data;

@Data
public class RefreshDTO {
  private final String refreshToken;
  private final String username;
  private final String organization;
}
