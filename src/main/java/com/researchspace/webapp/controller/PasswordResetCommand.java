package com.researchspace.webapp.controller;

import lombok.Data;

@Data
public class PasswordResetCommand {
  private String token;
  private String password;
  private String confirmPassword;
}
