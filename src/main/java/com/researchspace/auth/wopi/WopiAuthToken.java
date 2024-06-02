package com.researchspace.auth.wopi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.apache.shiro.authc.AuthenticationToken;

@Data
@AllArgsConstructor
public class WopiAuthToken implements AuthenticationToken {

  @Getter private String principal; // Username

  @Getter private String credentials; // Wopi Access token
}
