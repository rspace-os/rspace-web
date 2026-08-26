package com.researchspace.webapp.integrations.omero;

import lombok.Data;
import lombok.ToString;

@Data
public class OmeroUser {
  private String omerousername;
  @ToString.Exclude private String omeropassword;
  private String webClientUserName;

  // holds a real third-party password; never let it reach a log via toString()
  @ToString.Exclude private String webClientPassword;

  public OmeroUser(String omerousername, String omeropassword) {
    this.omeropassword = omeropassword;
    this.omerousername = omerousername;
    this.webClientUserName = stripPrefix(omerousername, "--omero.user=");
    this.webClientPassword = stripPrefix(omeropassword, "--omero.pass=");
  }

  // a form submission can omit either field, so neither is guaranteed non-null here
  private static String stripPrefix(String value, String prefix) {
    return value == null ? null : value.replace(prefix, "");
  }
}
