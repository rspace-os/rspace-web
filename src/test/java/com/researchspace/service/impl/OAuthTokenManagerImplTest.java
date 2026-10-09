package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.OAuthTokenDao;
import com.researchspace.model.User;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.OAuthAppManager;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

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

  @Nested
  @ExtendWith(MockitoExtension.class)
  class UiTokenRetryTests {

    @Mock private OAuthTokenDao tokenDao;
    @Mock private OAuthAppManager appManager;
    @Mock private IPropertyHolder properties;

    private final CountingTransactionManager transactions = new CountingTransactionManager();
    private OAuthTokenManagerImpl tokenManager;

    @BeforeEach
    void setUp() {
      tokenManager = new OAuthTokenManagerImpl();
      tokenManager.tokenDao = tokenDao;
      tokenManager.appManager = appManager;
      tokenManager.properties = properties;
      tokenManager.setTransactionManager(transactions);

      when(appManager.getOAuthTokenExpiryTimeInSeconds()).thenReturn(Duration.ofHours(1));
      when(properties.getServerUrl()).thenReturn("https://rspace.example.org");
      when(properties.getJwtKey()).thenReturn(Keys.secretKeyFor(SignatureAlgorithm.HS256));
    }

    @Test
    void retriesTokenCreationInACommittedNewTransaction() {
      when(appManager.isClientSecretCorrect(any(), any())).thenReturn(true);
      User user = mock(User.class);
      when(user.getId()).thenReturn(42L);
      when(tokenDao.getToken("rsInventoryWebClient", 42L, OAuthTokenType.UI_TOKEN))
          .thenReturn(Optional.empty());
      AtomicInteger saves = new AtomicInteger();
      when(tokenDao.save(any(OAuthToken.class)))
          .thenAnswer(
              invocation -> {
                if (saves.getAndIncrement() == 0) {
                  throw new DataIntegrityViolationException("concurrent insert");
                }
                return invocation.getArgument(0);
              });

      assertNotNull(tokenManager.createUiToken(user));

      assertEquals(2, saves.get());
      assertEquals(2, transactions.started);
      assertEquals(1, transactions.rolledBack);
      assertEquals(1, transactions.committed);
      assertEquals(
          TransactionDefinition.PROPAGATION_REQUIRES_NEW, transactions.propagationBehaviors.get(0));
      assertEquals(
          TransactionDefinition.PROPAGATION_REQUIRES_NEW, transactions.propagationBehaviors.get(1));
    }

    @Test
    void retriesSessionBoundTokenCreationWhenCommitFails() {
      User subject = mock(User.class);
      User actor = mock(User.class);
      when(subject.getId()).thenReturn(42L);
      when(actor.getId()).thenReturn(43L);
      when(tokenDao.getToken("rsInventoryWebClient", 42L, OAuthTokenType.UI_TOKEN))
          .thenReturn(Optional.empty());
      when(tokenDao.save(any(OAuthToken.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      transactions.failNextCommit();

      assertNotNull(tokenManager.createUiToken(subject, actor, "session-context"));

      assertEquals(2, transactions.started);
      assertEquals(2, transactions.committed);
      assertEquals(1, transactions.rolledBack);
    }
  }

  private static class CountingTransactionManager implements PlatformTransactionManager {
    private final List<Integer> propagationBehaviors = new ArrayList<>();
    private int started;
    private int rolledBack;
    private int committed;
    private boolean failNextCommit;

    private void failNextCommit() {
      failNextCommit = true;
    }

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
      started++;
      propagationBehaviors.add(definition.getPropagationBehavior());
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      committed++;
      if (failNextCommit) {
        failNextCommit = false;
        rolledBack++;
        throw new DataIntegrityViolationException("concurrent commit");
      }
    }

    @Override
    public void rollback(TransactionStatus status) {
      rolledBack++;
    }
  }
}
