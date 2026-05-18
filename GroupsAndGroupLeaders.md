# Groups and Group Leaders in RSpace

Reference notes derived from the RSpace public documentation and verified against
the source code in this repository. Where the docs and code disagree, the code is
treated as authoritative and the divergence is called out.

> Reminder: when acting on any of this, follow the AI and Third Party code policy
> found in Drata.

---

## 1. Group types

The exhaustive list of group types is defined in `GroupType` (`src/main/java/com/researchspace/model/GroupType.java:3-17`):

| Constant | Display name | Notes |
|---|---|---|
| `LAB_GROUP` | Lab Group | The standard research group. |
| `COLLABORATION_GROUP` | Collaboration Group | Cross-LabGroup; multiple PIs may participate. |
| `PROJECT_GROUP` | Project Group | No PI; uses a distinct `GROUP_OWNER` role. |

**Self-Service** is **not** a separate group type. It is a boolean flag
(`isSelfService`) on a `LAB_GROUP` — `Group.java:81, 909-914`.

---

## 2. Leadership and ownership by group type

| Group type | PI required? | Owner required? | Notes |
|---|---|---|---|
| `LAB_GROUP` (incl. Self-Service variant) | Yes — `RoleInGroup.PI` (exactly one primary PI) | Yes — `owner` field on `Group`; usually the same user as the PI | The standard case. |
| `COLLABORATION_GROUP` | Yes — at least one PI; multiple PIs allowed | Yes — `owner` is the initiating PI | PI-to-PI sharing; non-PI members do not see each other's work by default. |
| `PROJECT_GROUP` | No | Yes — `owner` field plus a distinct `RoleInGroup.GROUP_OWNER` role | `GROUP_OWNER` is only valid in Project Groups — enforced at `Group.java:289-290`: <br>`if (RoleInGroup.GROUP_OWNER.equals(role) && !isProjectGroup()) { throw new IllegalArgumentException(...); }` |

---

## 3. Members who cannot be deleted without first disbanding (or transferring roles)

User deletion is gated in `UserDeletionManagerImpl.validateGroupMembershipCriteria()`
at `src/main/java/com/researchspace/service/impl/UserDeletionManagerImpl.java:220-231`.
For every group the user belongs to, deletion is blocked if any of the following hold:

1. `group.isOnlyGroupPi(toDelete.getUsername())` → error key
   `group.edit.mustbe1.admin.error.msg`. Applies wherever the group needs a PI
   (LabGroup, Collaboration Group). The user is the *only* PI in that group.
2. `group.getOwner().equals(toDelete)` → error key
   `group.edit.nogroupownerdelete.error.msg`. Applies to **every** group type —
   the owner is unconditionally protected.
3. The data-preservation policy refuses the delete (e.g., recent logins) via
   `isAllGroupCapableOfDeletion(policy, group)`.

### Per-group-type summary

| Group type | Blocking member(s) |
|---|---|
| `LAB_GROUP` | The **PI** if they are the only PI; **AND** the **owner** (always). In practice these are usually the same person. |
| `COLLABORATION_GROUP` | Same as LAB_GROUP — only-PI and owner. |
| `PROJECT_GROUP` | The **owner** (always). No PI requirement applies. |

### Practical "unblock" paths before user deletion

- **LabGroup / Collaboration Group**: promote another member to `PI_ROLE`, run
  change-PI (`GroupManagerImpl.setNewPi()` → `SwapPiValidator` →
  `doChangePi()`). After the swap the original PI is no longer the "only PI"
  and no longer the owner — user deletion can proceed.
- **Project Group**: transfer ownership, **or** disband the group entirely. The
  user-deletion code only checks `Group.getOwner().equals(user)`, so any owner
  is blocked even if a transfer mechanism would otherwise be available.
- **Any group**: disband the group (see Section 5) — this clears the
  group-membership constraint and the user can then be deleted (subject to the
  other guards in `UserDeletionManagerImpl`).

### Notable code observation

`Group.isOnlyGroupOwner()` exists at `Group.java:803-806` and is used elsewhere
(e.g., in member-removal flows in `GroupController`), but
`UserDeletionManagerImpl` does **not** call it. The user-delete code blocks
*any* owner, not just the last owner. This is a slight inconsistency worth
knowing if you're reasoning about edge cases.

---

## 4. Public REST API access for group disbanding

**There is no public REST API endpoint that can disband a group.** All
deletion paths are interactive MVC controllers requiring a web session (CSRF
token plus cookies), not API-key authentication.

### Group endpoints that exist in the public API (`/api/v1/...`)

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/groups` (`GroupApiController`) | List the caller's groups. |
| `GET /api/v1/groups/search` | Search groups. |
| `GET /api/v1/groups/{id}` | Get group details. |
| `POST /api/v1/sysadmin/groups` (`SysadminApiController`) | Create a group; sysadmin-only, IP-whitelisted via `@Sysadmin`. |

No `@DeleteMapping` exists on `GroupApiController` or `SysadminApiController`.

### Implications

- **Sysadmins cannot script group disbanding** via the public API — they must
  use the web UI.
- **Owners of project / self-service groups** are also UI-only.
- For automated cleanup of stale groups there is currently no documented
  public endpoint. Either drive the UI form-post endpoints, or add a new API
  endpoint — the latter is the cleaner path if such automation is needed.

---

## 5. How to disband each group type (web UI only)

All paths invoke `GroupManagerImpl.removeGroup()`
(`src/main/java/com/researchspace/service/impl/GroupManagerImpl.java:788-836`),
which **does not block** based on group state — there are no checks for active
publications, signed/locked records, or in-flight shares.

| Group type | Endpoint | Caller(s) allowed |
|---|---|---|
| `LAB_GROUP`, `COLLABORATION_GROUP` | `POST /admin/removeGroup/{grpid}` (`GroupController.java:537`) | Sysadmin (`SYSTEM_ROLE`) **or** Community Admin (`ADMIN_ROLE`). A TODO comment at line 536 questions whether community admin should retain this. |
| `PROJECT_GROUP` | `POST /groups/projectGroup/deleteGroup/{grpid}` (`ProjectGroupController.java:138`) | The group **owner** only — guard: `targetGroup.isProjectGroup() && targetGroup.getOwner().equals(subject)`. Sysadmin **cannot** disband via this endpoint. |
| `LAB_GROUP` with `isSelfService=true` | `POST /groups/selfServiceLabGroup/deleteGroup/{grpid}` (`SelfServiceLabGroupController.java:118`) | The group **owner** only — guard: `targetGroup.isSelfService() && targetGroup.getOwner().equals(subject)`. Sysadmin **cannot** disband via this endpoint. |

### What `removeGroup()` actually does

`GroupManagerImpl.removeGroup()` (`GroupManagerImpl.java:788-836`):

1. Removes all users from the group.
2. Removes record-sharing entries (`RecordGroupSharingDao`).
3. Removes the group from its community.
4. Removes join requests and group-related events.
5. Removes RAiD entries (Project Groups).
6. Calls `groupDao.remove(group.getId())` — the group row is deleted.

Member documents are **not** physically deleted. Their previously group-shared
records revert to being accessible only to the owning user. The deletion of the
group row itself is permanent.

### What is **not** preserved by disbanding

- The shared folder of the group.
- All group-share aliases that members saw under their Shared folder.
- Any public publication URLs that depended on group membership (note:
  publication-URL impact depends on who originally published — see
  `Publication of Documents` docs; URLs do not auto-regenerate).

---

## 6. Doc claims that diverge from code

These are points where the public help docs describe a stricter or different
rule than the implementation actually enforces.

1. **"To change the PI of a group, the group must first have at least 2 members
   with the PI role."** **Not enforced as stated.** `SwapPiValidator`
   (`SwapPiValidator.java:26-29`) only requires the candidate to (a) already be
   a non-PI member of the group and (b) hold the system-level `PI_ROLE`. The
   new PI is added before the old one is removed (`GroupManagerImpl.java:1160-1188`).
2. **"LabGroups cannot be created or exist without an allocated PI."**
   Enforced on removal/demotion (`UserDeletionManagerImpl.java:223`;
   `GroupManagerImpl.java:896-898`) but no explicit guard was found at group
   *creation* time at the manager level. Likely enforced indirectly via the
   creation UI flow.
3. **Deployment property name.** Docs call the flag `deleteUser.enabled`. The
   actual property in `defaultDeployment.properties:121` is
   `sysadmin.delete.user` (default `false`).
4. **"Sysadmin can disband a LabGroup at any time."** True for `LAB_GROUP` and
   `COLLABORATION_GROUP`. **False** for `PROJECT_GROUP` and self-service
   LabGroups via their dedicated endpoints — those check
   `getOwner().equals(subject)` and reject sysadmin callers.
5. **User-delete soft vs hard.** RSpace uses soft deletes for most entities,
   but **user deletion is a true hard delete** —
   `UserDeletionDaoHibernate.doForceDeleteUser()` (`UserDeletionDaoHibernate.java:100-123`)
   cascades physical deletes through records, inventory, groups, forms, and
   filestore contents on disk. This is the documented intent.

---

## 7. Quick decision matrix

| You want to … | Possible? | How |
|---|---|---|
| Delete a user who is the only PI of a LabGroup | No — must transfer PI first (or disband group) | Promote another member to `PI_ROLE`, run change-PI, then delete |
| Delete a user who is the owner of a LabGroup | No — owner is blocked | Transfer ownership or disband |
| Delete the owner of a Project Group | No — owner is blocked | Disband the project group (owner-only UI endpoint) or transfer ownership |
| Disband a LabGroup as a sysadmin | Yes | `POST /admin/removeGroup/{id}` |
| Disband a Project Group as a sysadmin | Not via the dedicated UI endpoint | Only the owner can; sysadmin would need a different path |
| Disband any group via REST API | No | No `@DeleteMapping` exists in the public API |
| Block disbanding because of published / signed content | Cannot — no such guard | `removeGroup()` proceeds unconditionally once auth passes |
