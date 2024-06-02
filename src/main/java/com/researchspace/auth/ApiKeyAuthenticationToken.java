package com.researchspace.auth;

import lombok.Value;
import org.apache.shiro.authc.AuthenticationToken;

/** Token to represent incoming API request */
@Value
public class ApiKeyAuthenticationToken implements AuthenticationToken {

  /** */
  private static final long serialVersionUID = -1053500239276332847L;

  /** Username */
  private String principal;

  /** Key */
  private String credentials;
}
