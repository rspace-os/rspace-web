package com.researchspace.service.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourceRoleSchemeTest {

  private static final Set<ResourceGranteeKind> PEOPLE =
      Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP);

  @Test
  void validatesGenericOwnerManagerContributorReaderScheme() {
    ResourceRoleScheme scheme = validScheme();

    assertDoesNotThrow(scheme::validate);
    assertEquals(
        Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE_ASSIGNMENTS", "MANAGE_OWNERS"),
        scheme.capabilities("OWNER"));
  }

  @Test
  void rejectsHigherRoleThatOmitsLowerRoleCapability() {
    ResourceRoleScheme scheme =
        scheme(
            roles(),
            capabilities(
                Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE_ASSIGNMENTS", "MANAGE_OWNERS"),
                Set.of("READ_RESOURCE", "MANAGE_ASSIGNMENTS"),
                Set.of("READ_RESOURCE", "CONTRIBUTE"),
                Set.of("READ_RESOURCE")),
            Set.of("OWNER"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, scheme::validate);

    assertTrue(error.getMessage().contains("CONTRIBUTOR"));
    assertTrue(error.getMessage().contains("CONTRIBUTE"));
  }

  @Test
  void rejectsOwnerOrManagerInWrongPosition() {
    ResourceRoleScheme scheme =
        scheme(
            List.of(
                new ResourceRole("MANAGER", 40),
                new ResourceRole("OWNER", 30),
                new ResourceRole("CONTRIBUTOR", 20),
                new ResourceRole("READER", 10)),
            validCapabilities(),
            Set.of("OWNER"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, scheme::validate);

    assertTrue(error.getMessage().contains("OWNER"));
    assertTrue(error.getMessage().contains("highest"));
  }

  @Test
  void rejectsDuplicateRoleKeysAndRanks() {
    ResourceRoleScheme duplicateKey =
        scheme(
            List.of(
                new ResourceRole("OWNER", 40),
                new ResourceRole("MANAGER", 30),
                new ResourceRole("READER", 20),
                new ResourceRole("READER", 10)),
            validCapabilities(),
            Set.of("OWNER"));
    ResourceRoleScheme duplicateRank =
        scheme(
            List.of(
                new ResourceRole("OWNER", 40),
                new ResourceRole("MANAGER", 30),
                new ResourceRole("CONTRIBUTOR", 20),
                new ResourceRole("READER", 20)),
            validCapabilities(),
            Set.of("OWNER"));

    assertTrue(
        assertThrows(IllegalArgumentException.class, duplicateKey::validate)
            .getMessage()
            .contains("Duplicate role key"));
    assertTrue(
        assertThrows(IllegalArgumentException.class, duplicateRank::validate)
            .getMessage()
            .contains("Duplicate role rank"));
  }

  @Test
  void rejectsUnknownRequiredPersistedRole() {
    ResourceRoleScheme scheme = scheme(roles(), validCapabilities(), Set.of("OWNER", "AUDITOR"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, scheme::validate);

    assertTrue(error.getMessage().contains("AUDITOR"));
  }

  @Test
  void rejectsRoleWithoutAllowedGranteeKinds() {
    ResourceRoleScheme valid = validScheme();
    ResourceRoleScheme scheme =
        new TestScheme(valid.roles(), validCapabilities(), Set.of("OWNER")) {
          @Override
          public Set<ResourceGranteeKind> allowedGranteeKinds(String roleKey) {
            return roleKey.equals("READER") ? Set.of() : PEOPLE;
          }
        };

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, scheme::validate);

    assertTrue(error.getMessage().contains("READER"));
    assertTrue(error.getMessage().contains("grantee"));
  }

  @Test
  void rejectsOwnerOrManagerWithoutReadCapability() {
    ResourceRoleScheme scheme =
        scheme(
            roles(),
            capabilities(
                Set.of("CONTRIBUTE", "MANAGE_ASSIGNMENTS", "MANAGE_OWNERS"),
                Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE_ASSIGNMENTS"),
                Set.of("READ_RESOURCE", "CONTRIBUTE"),
                Set.of("READ_RESOURCE")),
            Set.of("OWNER"));

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, scheme::validate);

    assertTrue(error.getMessage().contains("OWNER"));
    assertTrue(error.getMessage().contains("READ_RESOURCE"));
  }

  @Test
  void registryRejectsDuplicateSchemeKeys() {
    ResourceRoleScheme first = validScheme();
    ResourceRoleScheme second = validScheme();

    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ResourceRoleSchemeRegistry(List.of(first, second)));

    assertTrue(error.getMessage().contains("test-resources"));
  }

  private static ResourceRoleScheme validScheme() {
    return scheme(roles(), validCapabilities(), Set.of("OWNER"));
  }

  private static List<ResourceRole> roles() {
    return List.of(
        new ResourceRole("OWNER", 40),
        new ResourceRole("MANAGER", 30),
        new ResourceRole("CONTRIBUTOR", 20),
        new ResourceRole("READER", 10));
  }

  private static Map<String, Set<String>> validCapabilities() {
    return capabilities(
        Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE_ASSIGNMENTS", "MANAGE_OWNERS"),
        Set.of("READ_RESOURCE", "CONTRIBUTE", "MANAGE_ASSIGNMENTS"),
        Set.of("READ_RESOURCE", "CONTRIBUTE"),
        Set.of("READ_RESOURCE"));
  }

  private static Map<String, Set<String>> capabilities(
      Set<String> owner, Set<String> manager, Set<String> contributor, Set<String> reader) {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    result.put("OWNER", owner);
    result.put("MANAGER", manager);
    result.put("CONTRIBUTOR", contributor);
    result.put("READER", reader);
    return result;
  }

  private static ResourceRoleScheme scheme(
      List<ResourceRole> roles,
      Map<String, Set<String>> capabilities,
      Set<String> requiredPersistedRoles) {
    return new TestScheme(roles, capabilities, requiredPersistedRoles);
  }

  private static class TestScheme implements ResourceRoleScheme {

    private final List<ResourceRole> roles;
    private final Map<String, Set<String>> capabilities;
    private final Set<String> requiredPersistedRoles;

    TestScheme(
        List<ResourceRole> roles,
        Map<String, Set<String>> capabilities,
        Set<String> requiredPersistedRoles) {
      this.roles = roles;
      this.capabilities = capabilities;
      this.requiredPersistedRoles = requiredPersistedRoles;
    }

    @Override
    public String key() {
      return "test-resources";
    }

    @Override
    public List<ResourceRole> roles() {
      return roles;
    }

    @Override
    public Set<String> capabilities(String roleKey) {
      return capabilities.get(roleKey);
    }

    @Override
    public Set<ResourceGranteeKind> allowedGranteeKinds(String roleKey) {
      return PEOPLE;
    }

    @Override
    public Set<String> requiredPersistedRoles() {
      return requiredPersistedRoles;
    }

    @Override
    public Optional<String> implicitRole(User subject) {
      return Optional.empty();
    }
  }
}
