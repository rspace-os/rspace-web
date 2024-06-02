package com.researchspace.netfiles;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.User;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@AllArgsConstructor
@EqualsAndHashCode(of = "user")
@Builder
@JsonPropertyOrder(value = {"username", "password"})
public class ApiNfsCredentials implements Serializable {

  @JsonIgnore private User user;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("username")
  private String username;

  @JsonInclude(Include.NON_NULL)
  @JsonProperty("password")
  private String password;

  public ApiNfsCredentials() {}
}
