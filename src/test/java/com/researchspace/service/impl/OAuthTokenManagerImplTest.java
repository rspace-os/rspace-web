package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.views.ServiceOperationResult;
import org.junit.jupiter.api.Test;

public class OAuthTokenManagerImplTest {

  private final OAuthTokenManagerImpl manager = new OAuthTokenManagerImpl();

  @Test
  public void jwtShapedTokenIsCheckedSectionBySection() {
    ServiceOperationResult<Void> ok = manager.validateToken("aGVhZGVy.cGF5bG9hZA.c2lnbmF0dXJl");
    assertTrue(ok.isSucceeded());

    ServiceOperationResult<Void> bad = manager.validateToken("aGVhZGVy.not base64!.c2lnbmF0dXJl");
    assertFalse(bad.isSucceeded());
    assertEquals("not base64! not in base64 format", bad.getMessage());
  }

  @Test
  public void tokenWithEmptySectionIsNotTreatedAsJwt() {
    // "a..b" has an empty middle section, so it falls through to the opaque-token length check
    ServiceOperationResult<Void> result = manager.validateToken("a..b");
    assertFalse(result.isSucceeded());
    assertEquals("token length incorrect", result.getMessage());
  }

  @Test
  public void opaqueTokenMustBeExactLengthBase64() {
    assertTrue(manager.validateToken("A".repeat(32)).isSucceeded());
    assertEquals("token length incorrect", manager.validateToken("A".repeat(31)).getMessage());
    assertEquals("token not in base64 format", manager.validateToken("!".repeat(32)).getMessage());
  }
}
