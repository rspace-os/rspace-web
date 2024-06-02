package com.researchspace.webapp.integrations.omero;

import lombok.Data;

@Data
public class OmeroUser {
  private String omerousername;
  private String omeropassword;
  private String webClientUserName;
  private String webClientPassword;

  public OmeroUser(String omerousername, String omeropassword) {
    this.omeropassword = omeropassword;
    this.omerousername = omerousername;
    this.webClientUserName = omerousername.replace("--omero.user=", "");
    this.webClientPassword = omeropassword.replace("--omero.pass=", "");
  }
}
