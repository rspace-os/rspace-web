package com.researchspace.api.v1.model;

import com.researchspace.core.testutilJU5.JakartaValidatorTestJU5;
import org.junit.jupiter.api.Test;

public class ApiUserPostTest extends JakartaValidatorTestJU5 {

  @Test
  public void testValidation() {
    // start off with valid user
    ApiUserPost userToCreate =
        ApiUserPost.builder()
            .email("x@y.com")
            .username("abcdefg")
            .firstName("bob")
            .lastName("smith")
            .password("12312312")
            .role("ROLE_USER")
            .build();
    assertValid(userToCreate);
    userToCreate.setRole("ROLE_PI");
    assertValid(userToCreate);

    userToCreate.setFirstName("   ");
    assertNErrors(userToCreate, 1);
    userToCreate.setLastName("  ");
    assertNErrors(userToCreate, 2);

    userToCreate.setEmail("xxxxx"); // invalid
    assertNErrors(userToCreate, 3);

    userToCreate.setPassword("222"); // too short
    assertNErrors(userToCreate, 5);

    userToCreate.setPassword("222     "); // whitespaces are fine
    assertNErrors(userToCreate, 3);
  }
}
