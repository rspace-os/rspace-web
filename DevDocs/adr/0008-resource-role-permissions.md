---
status: accepted
---

# Use resource role schemes for new permissions

Booking needs resource-scoped Owner, Manager, Booker, and Viewer roles for user,
group, and All users grantees. These roles deliberately authorize Booking
without granting access to the target Inventory record. The legacy record ACL
cannot express that model cleanly, and making Inventory permissions a second
authority would let the two systems disagree.

Create a reusable, versioned resource-access aggregate and generic role
assignments. A protected entity references its aggregate by foreign key. Each
resource type registers a role scheme that defines its ordered roles,
capabilities, assignment rules, required persisted roles, implicit roles, and
self-removal behavior. Every scheme includes Owner and Manager, while other
roles remain specific to the resource type. Owner is always highest and may
manage Owner assignments; Manager is next and may not change Owners. Every
protected resource retains at least one persisted Owner. Each higher role must
include every capability of the lower roles because the shared module resolves
direct, active-group, dynamic-audience, and implicit sources to one highest
effective role and its capabilities. Each role declares which grantee kinds it
accepts.

Expose assignments through resource-scoped REST API v2 routes such as
`/api/v2/booking-configurations/{id}/access`. Replace an access document
atomically with optimistic concurrency so invariants such as at least one
persisted Owner never depend on request ordering. Keep grantee discovery behind
a resource-scoped REST API v2 endpoint, and require assignment-management
capability for that resource before searching, reading, or changing its
assignments. Booking settings use a separate sysadmin-only directory for
creation defaults.

The resource-access aggregate has its own generalized `version`, separate from
the protected entity's version. Access reads return that version and an ETag;
replacements require `If-Match` and reject stale writes without changing any
assignment. Resource queries apply the effective-role predicate before count
and pagination, and direct reads conceal inaccessible resources as not found.
The transactional manager reloads and locks the aggregate and every live
identity or membership fact used to authorize the represented subject, then
re-resolves capability before mutation. A controller's earlier check is not the
write authority.

Assignment availability comes from live User and Group state. Disabled users
and groups with no enabled members do not grant effective access. A hard-deleted
group leaves its assignment through `ON DELETE SET NULL`; the retained key and
snapshot keep the row visible and auditable. No availability flag is copied to
assignments and identity lifecycle changes do not fan out across protected
resources. Such an unavailable Owner row still satisfies the structural Owner
invariant. Booking exposes a batched Owner-health projection so an implicit
sysadmin Owner can find and repair configurations with no effective Owner.

The shared module emits resource-scoped audit deltas for assignment, audience,
self-removal, and coordinated ownership changes. It records the actor and
represented subject but emits nothing for a no-op replacement. A generic
frontend access editor consumes the same access document, capabilities,
grantee search, and concurrency behavior. Resource adapters supply translated
role labels and resource-specific notices rather than duplicating the editor.

Booking is the first production consumer. Ordinary users may create a Booking
configuration only for an Instrument they own; system administrators may create
one for any eligible Instrument. Inventory permissions are not copied or kept
in sync after creation. An Inventory ownership transfer may coordinate an
explicit Booking ownership transfer when the actor has both permissions, but
that operation remains an opt-in command rather than synchronization.

After any voluntary or involuntary loss of a Booking role, the requester may
still read only their own booking rows through the booking requester relation.
There is no departure marker, and this fallback grants no configuration,
calendar, audit, access, or subscription permission. A coordinated Inventory
transfer adds or changes the incoming owner's direct Booking role to Owner and
removes the outgoing owner's direct assignment only when it is Owner; a lower
outgoing role and all other assignments remain intact.

The current Shiro solution remains the request identity boundary. Controllers
adapt its represented subject and original actor into explicit service inputs.
The generic resource-access module does not import Shiro, use ambient security
state, or depend on REST API types.

Booking-specific tables were rejected because the next adopter would require a
schema migration. A global grant table keyed by arbitrary resource type and ID
was rejected because the database could not enforce ownership of the protected
resource. Continuous Inventory synchronization and read-time intersection were
rejected because they would make an apparently valid Booking role ineffective
or mutable from another permission system.

This iteration migrates only Booking. Existing Inventory, workspace, Gallery,
and form permissions keep their current behavior until separate migrations can
preserve their propagation and ownership rules. A second test-only role scheme
must prove that the shared module contains no Booking role names or conditions.
