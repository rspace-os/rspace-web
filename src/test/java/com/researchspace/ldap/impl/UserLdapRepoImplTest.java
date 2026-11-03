package com.researchspace.ldap.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.auth.password.NewPasswordEncodeGate;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.dtos.UserValidator;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.ISignupHandlerPolicy;
import com.researchspace.service.LicenseRequestResult;
import com.researchspace.service.LicenseService;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserExistsException;
import com.researchspace.service.UserManager;
import com.researchspace.service.UserSignupException;
import java.util.List;
import javax.naming.directory.DirContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQuery;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class UserLdapRepoImplTest {

  private static final String USER_DN = "cn=user,dc=example,dc=com";

  @Mock private LdapContextSource ldapContext;
  @Mock private LdapTemplate ldapTemplate;
  @Mock private IPropertyHolder properties;
  @Mock private DirContext dirContext;
  @Mock private UserValidator userValidator;
  @Mock private UserManager userManager;
  @Mock private LicenseService licenseService;
  @Mock private ISignupHandlerPolicy manualSignupPolicy;
  @Mock private MessageSourceUtils messages;

  @InjectMocks private UserLdapRepoImpl userLdapRepo;

  @BeforeEach
  public void setUp() {
    when(properties.getLdapEnabled()).thenReturn("true");
    when(properties.isLdapAuthenticationEnabled()).thenReturn(true);
    ReflectionTestUtils.setField(userLdapRepo, "ldapSearchQueryUidField", "uid");
    ReflectionTestUtils.setField(userLdapRepo, "fallbackDnCalculationEnabled", "false");
  }

  @Test
  public void emptyPasswordRejectedWithoutContactingDirectory() {
    assertNull(userLdapRepo.authenticate("user", null));
    assertNull(userLdapRepo.authenticate("user", ""));

    verifyNoInteractions(ldapTemplate, ldapContext);
  }

  @Test
  public void whitespaceOnlyPasswordStillReachesBind() {
    User ldapUser = new User("user");
    ldapUser.setToken(USER_DN);
    when(ldapTemplate.search(any(LdapQuery.class), ArgumentMatchers.<AttributesMapper<User>>any()))
        .thenReturn(List.of(ldapUser));
    when(ldapContext.getContext(USER_DN, "   ")).thenReturn(dirContext);

    assertNotNull(userLdapRepo.authenticate("user", "   "));

    verify(ldapContext).getContext(USER_DN, "   ");
  }

  @Test
  public void autoSignupRefusedWithoutSavingWhenNoHashingPermitIsFree() throws Exception {
    NewPasswordEncodeGate gate = gateOfOne();
    stubSignupChecks();
    when(messages.getMessage("errors.signup.rateLimited")).thenReturn("busy");
    assertTrue(gate.tryAcquire());

    UserSignupException e =
        assertThrows(UserSignupException.class, () -> userLdapRepo.signupLdapUser(newLdapUser()));
    assertEquals("busy", e.getMessage());
    verify(manualSignupPolicy, never()).saveUser(any(), any());

    gate.release();
    User saved = new User("ldapUser");
    when(manualSignupPolicy.saveUser(any(), any())).thenReturn(saved);
    assertSame(saved, userLdapRepo.signupLdapUser(newLdapUser()));
  }

  @Test
  public void hashingPermitReturnedWhenSaveFails() throws Exception {
    NewPasswordEncodeGate gate = gateOfOne();
    stubSignupChecks();
    when(manualSignupPolicy.saveUser(any(), any())).thenThrow(new UserExistsException("taken"));

    UserSignupException e =
        assertThrows(UserSignupException.class, () -> userLdapRepo.signupLdapUser(newLdapUser()));
    assertInstanceOf(UserExistsException.class, e.getCause());
    assertTrue(gate.tryAcquire());
  }

  private NewPasswordEncodeGate gateOfOne() {
    NewPasswordEncodeGate gate = new NewPasswordEncodeGate(1);
    ReflectionTestUtils.setField(userLdapRepo, "encodeGate", gate);
    return gate;
  }

  private void stubSignupChecks() {
    when(userValidator.validateUsername("ldapUser")).thenReturn(UserValidator.FIELD_OK);
    when(licenseService.requestUserLicenses(1, Role.USER_ROLE))
        .thenReturn(new LicenseRequestResult(true, 1, true));
  }

  private User newLdapUser() {
    User user = new User("ldapUser");
    user.setEmail("ldapUser@example.com");
    return user;
  }
}
