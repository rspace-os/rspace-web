package com.researchspace.model.collection;

import static com.researchspace.model.collection.ApiV2ResourceField.WriteAccess.NEVER;

import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.List;

/**
 * The publicly listable shape of a user: an allowlist, not a projection of the entity.
 *
 * <p>{@code User} carries password hashes, verification tokens, signup source, and role wiring.
 * Naming the five safe fields here means a field added to the entity is invisible to the API until
 * someone adds it deliberately, which is the opposite default to reflecting over the entity.
 *
 * <p>{@code email} is deliberately <b>not</b> restricted to system administrators. Row scoping is
 * what protects it: {@link #OWN_ROW_UNLESS_SYSADMIN} limits an ordinary caller to their own row,
 * and their own address is already in {@code /api/v2/users/me}. Gating the field as well would make
 * {@code /api/v2/users/{myId}} return strictly less than {@code /me} for no gain, because {@code
 * AccessFunction} is evaluated with the request's access context.
 */
// TODO: Expose updatedAt, createdBy, and updatedBy after the shared User entity persists those
// properties; until then the audit-field builder deliberately omits the unavailable selectors.
@ApiV2ResourceDefinition(name = "users", entity = User.class, id = "id")
public record ApiV2UserResource(
    @ApiV2ResourceField(description = "Stable user identifier.", example = "42") Long id,
    @ApiV2ResourceField(
            createAccess = NEVER,
            updateAccess = NEVER,
            description = "RSpace login name.",
            example = "ada")
        String username,
    @ApiV2ResourceField(
            createAccess = NEVER,
            updateAccess = NEVER,
            description = "User's given name.")
        String firstName,
    @ApiV2ResourceField(
            createAccess = NEVER,
            updateAccess = NEVER,
            description = "User's family name.")
        String lastName,
    @ApiV2ResourceField(
            createAccess = NEVER,
            updateAccess = NEVER,
            description = "User's email address.",
            format = "email",
            example = "ada@example.org")
        String email) {

  public static final AccessFunction OWN_ROW_UNLESS_SYSADMIN = AccessFunction.sysadminOrSelf("id");

  public static final CollectionDescription<User> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2UserResource.class,
          List.of(),
          List.of(new Sort("username", true), new Sort("id", true)),
          AccessPolicy.readOnly(OWN_ROW_UNLESS_SYSADMIN));
}
