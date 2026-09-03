package com.researchspace.ldap.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
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
}
