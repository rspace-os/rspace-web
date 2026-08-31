package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
import com.researchspace.service.resourceaccess.ResourceRole;
import com.researchspace.service.resourceaccess.ResourceRoleScheme;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Resource-role scheme for Booking configurations and their dependent resources. */
@Component
public final class BookingResourceRoleScheme implements ResourceRoleScheme {

  public static final String SCHEME_KEY = "booking-configurations";

  public static final String OWNER = OWNER_ROLE;
  public static final String MANAGER = MANAGER_ROLE;
  public static final String BOOKER = "BOOKER";
  public static final String VIEWER = "VIEWER";

  public static final String READ_RESOURCE = READ_RESOURCE_CAPABILITY;
  public static final String CREATE_CALENDAR_SUBSCRIPTION = "CREATE_CALENDAR_SUBSCRIPTION";
  public static final String CREATE_BOOKING = "CREATE_BOOKING";
  public static final String MANAGE_OWN_BOOKINGS = "MANAGE_OWN_BOOKINGS";
  public static final String EDIT_CONFIGURATION = "EDIT_CONFIGURATION";
  public static final String VIEW_AUDIT = "VIEW_AUDIT";
  public static final String MANAGE_ALL_EVENTS = "MANAGE_ALL_EVENTS";
  public static final String CREATE_BLOCKOUT = "CREATE_BLOCKOUT";
  public static final String MANAGE_ASSIGNMENTS = "MANAGE_ASSIGNMENTS";
  public static final String MANAGE_OWNERS = "MANAGE_OWNERS";
  public static final String ARCHIVE_CONFIGURATION = "ARCHIVE_CONFIGURATION";

  private static final List<ResourceRole> ROLES =
      List.of(
          new ResourceRole(OWNER, 40),
          new ResourceRole(MANAGER, 30),
          new ResourceRole(BOOKER, 20),
          new ResourceRole(VIEWER, 10));

  private static final Set<String> VIEWER_CAPABILITIES =
      Set.of(READ_RESOURCE, CREATE_CALENDAR_SUBSCRIPTION);
  private static final Set<String> BOOKER_CAPABILITIES =
      Set.of(READ_RESOURCE, CREATE_CALENDAR_SUBSCRIPTION, CREATE_BOOKING, MANAGE_OWN_BOOKINGS);
  private static final Set<String> MANAGER_CAPABILITIES =
      Set.of(
          READ_RESOURCE,
          CREATE_CALENDAR_SUBSCRIPTION,
          CREATE_BOOKING,
          MANAGE_OWN_BOOKINGS,
          EDIT_CONFIGURATION,
          VIEW_AUDIT,
          MANAGE_ALL_EVENTS,
          CREATE_BLOCKOUT,
          MANAGE_ASSIGNMENTS);
  private static final Set<String> OWNER_CAPABILITIES =
      Set.of(
          READ_RESOURCE,
          CREATE_CALENDAR_SUBSCRIPTION,
          CREATE_BOOKING,
          MANAGE_OWN_BOOKINGS,
          EDIT_CONFIGURATION,
          VIEW_AUDIT,
          MANAGE_ALL_EVENTS,
          CREATE_BLOCKOUT,
          MANAGE_ASSIGNMENTS,
          MANAGE_OWNERS,
          ARCHIVE_CONFIGURATION);

  private static final Map<String, Set<String>> CAPABILITIES =
      Map.of(
          OWNER, OWNER_CAPABILITIES,
          MANAGER, MANAGER_CAPABILITIES,
          BOOKER, BOOKER_CAPABILITIES,
          VIEWER, VIEWER_CAPABILITIES);

  private static final Set<ResourceGranteeKind> ACCOUNTABLE_GRANTEES =
      Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP);
  private static final Set<ResourceGranteeKind> SHARE_GRANTEES =
      Set.of(ResourceGranteeKind.USER, ResourceGranteeKind.GROUP, ResourceGranteeKind.AUDIENCE);

  @Override
  public String key() {
    return SCHEME_KEY;
  }

  @Override
  public List<ResourceRole> roles() {
    return ROLES;
  }

  @Override
  public Set<String> capabilities(String roleKey) {
    Set<String> capabilities = CAPABILITIES.get(roleKey);
    if (capabilities == null) {
      throw new IllegalArgumentException("Unknown Booking role: " + roleKey);
    }
    return capabilities;
  }

  @Override
  public Set<ResourceGranteeKind> allowedGranteeKinds(String roleKey) {
    if (OWNER.equals(roleKey) || MANAGER.equals(roleKey)) {
      return ACCOUNTABLE_GRANTEES;
    }
    if (BOOKER.equals(roleKey) || VIEWER.equals(roleKey)) {
      return SHARE_GRANTEES;
    }
    throw new IllegalArgumentException("Unknown Booking role: " + roleKey);
  }

  @Override
  public Optional<String> implicitRole(User subject) {
    return subject != null && subject.hasSysadminRole() ? Optional.of(OWNER) : Optional.empty();
  }
}
