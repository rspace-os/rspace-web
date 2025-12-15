package com.researchspace.webapp.integrations.ascenscia.dto;

import lombok.Data;

@Data
public class AuthResponseDTO {
  private String accessToken;

  private String refreshToken;
}
