package com.researchspace.webapp.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditTrailCSVConverterInput {
  public String time;
  public String user;
  public String action;
  public String type;
  public String resource;
  public String name;
  public String description;
}
