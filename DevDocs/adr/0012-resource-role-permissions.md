---
status: accepted
---

# Use resource role schemes for new permissions

New resource-scoped collaboration needs roles and capabilities that the existing record ACL cannot
express cleanly. A reusable resource-access aggregate lets the next adopter declare its own scheme
without adding another resource-specific permission table or Shiro wildcard dialect.

The protected entity references its versioned access aggregate by foreign key. A global grant table
keyed by arbitrary resource type and ID was rejected because the database could not enforce the
protected entity's ownership of its access data. Resource-specific tables were rejected because
every new adopter would need another schema migration.

The scheme resolves all applicable sources to one highest effective role. This requires monotonic
capabilities: each higher role includes every lower role's capabilities. Owner is highest and
Manager immediately below it. A persisted Owner is a structural invariant independent of implicit
administrator access or the current availability of that Owner's identity.

Identity availability remains live data. Copying availability onto assignments or updating every
protected resource when users or groups change was rejected because it creates a second identity
authority and resource-level audit changes for an identity operation. Retained identity snapshots
provide audit history without granting access. The transactional service locks and re-resolves the
subject before changing assignments; an earlier controller check cannot authorize that mutation.

Shiro remains the request identity boundary. Services take explicit subject and actor inputs; the
generic module does not consult ambient security state or depend on REST types. Existing Inventory,
workspace, Gallery, and form permissions keep their behavior until separate migrations preserve
their propagation and ownership rules. A new production adopter needs an explicit migration plan;
a second test-only scheme guards against resource-specific branches in shared code.

See [Security and permissions](../DeveloperNotes/SecurityAndPermissions.md#resource-role-permissions)
for the maintained contracts.
