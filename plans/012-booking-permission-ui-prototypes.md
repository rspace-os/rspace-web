# Plan 012: Prototype the Booking permission experience and select one editor

> **Executor instructions**: Read this plan fully before changing code. This is
> a disposable UI prototype, not production implementation. Follow the steps in
> order, run every verification command, and confirm the expected result before
> continuing. Preserve unrelated and user-authored work. If a STOP condition
> occurs, satisfy it automatically when the remedy is safe and within scope;
> otherwise stop and report it rather than inventing a product decision.
> When the prototype review is complete, record the verdict in the ignored
> review note, capture the prototype on the throwaway branch described below,
> return to the recorded delivery branch, remove every resource-access
> prototype artifact there, and only then fill in the "Prototype verdict" and
> update this plan's row in `plans/README.md` unless a reviewer says they
> maintain the index.
>
> **Drift check, run first**:
>
> ```bash
> plan_scope=(
>   src/main/webapp/ui/src/modules/booking/prototypes
>   src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.tsx
>   src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.story.tsx
>   src/main/webapp/ui/src/modules/booking/pages/all-bookable-items/AllBookableItemsPage.tsx
>   src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookingSettingsPage.tsx
>   src/main/webapp/ui/src/modules/booking/pages/my-bookings/MyBookingsPage.tsx
>   src/main/webapp/ui/src/Inventory/components/ContextMenu/TransferAction.tsx
>   plans/012-booking-permission-ui-prototypes.md
>   plans/README.md
> )
> git diff --stat 5f230b8da3..HEAD -- "${plan_scope[@]}"
> git diff --stat -- "${plan_scope[@]}"
> git diff --cached --stat -- "${plan_scope[@]}"
> git ls-files --others --exclude-standard -- "${plan_scope[@]}"
> ```
>
> The worktree was already dirty when this plan was written. A non-empty diff is
> expected. The four commands separately expose committed, unstaged, staged,
> and untracked drift; do not treat a clean base-to-HEAD diff as a clean
> worktree. Compare the live files with "Current state" below. Stop if the
> merged bookable-item page, Storybook discovery pattern, or shared prototype
> story no longer has the described shape.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: direction
- **Planned at**: commit `5f230b8da3`, 2026-08-30, with the current dirty
  Booking worktree present
- **Status**: DONE

## Why this matters

Booking needs a permission editor that remains understandable when access is
direct, inherited through several groups, supplied by the dynamic All users
audience, or assumed by a system administrator. The surrounding product also
needs credible states for creation defaults, configuration management,
leaving, ownership transfer, and read-only access to one's own bookings after
role loss. Choosing an editor layout in production code would make the most
uncertain design decision costly to reverse.

This plan validates the selected principal-first editor in Storybook against
the complete permission state model and records the evidence Plan 013 can
implement. Earlier role-first and guided alternatives were discarded by user
direction before acceptance. The prototype deliberately uses in-memory
fixtures, hard-coded prototype copy, and no network or production state.

## Fixed product decisions

Do not reopen these decisions in the prototype. The review is about interaction
and information hierarchy, not the permission model.

### Roles and inheritance

- The role name is **Booker**, never "User" or "booking user".
- The ordered roles are Owner, Manager, Booker, Viewer. Higher roles include
  every lower role's capabilities. The prototype must fail visibly if a scheme
  violates that monotonic rule because the production resolver selects one
  highest effective role.
- Owner edits everything, manages all role assignments, and archives the
  configuration.
- Manager edits configuration and all calendar events and may assign Manager,
  Booker, and Viewer, but cannot add, remove, or change Owners and cannot
  archive.
- Booker creates bookings and edits or cancels only their own bookings.
- Viewer reads the configuration, schedule, and full authorized event details.
- All roles may create a personal calendar subscription.
- A user or group has at most one direct role assignment for a configuration.
  A user can still inherit several roles from groups and All users; the highest
  applicable role is effective. A weaker direct role never reduces inherited
  access.
- System administrators assume Owner without a persisted row. In run-as mode,
  the represented user's permissions apply; the administrator is only the
  audit actor.

### Assignment and leave behavior

- Grantees can be users, Lab/Collaboration/Project groups, or the dynamic All
  users audience. Communities are never supported.
- All users may be Booker or Viewer only. It cannot be Manager or Owner and
  never satisfies Owner health; the role control must make those invalid
  combinations unavailable without relying on a server error.
- Every configuration retains at least one persisted Owner assignment row. A
  disabled user, a hard-deleted group retained by snapshot, or a group with no
  enabled members still satisfies this structural invariant. None supplies an
  effective Owner. Implicit sysadmin Owner access also does not satisfy the row
  invariant.
- Disabled users and deleted groups remain visible as unavailable assignments.
  Groups with no enabled members remain valid grantees but are labelled as
  providing no effective Owner access. The sysadmin administration scene must
  flag configurations with no effective Owner and offer Repair access as the
  repair
  action.
- Owner can edit every row. Manager controls only Manager/Booker/Viewer rows.
- The editor uses an explicit draft followed by **Save changes** or **Cancel**.
  There is no save-on-select behavior.
- "Leave configuration" removes only the caller's direct assignment. It does
  not exclude access inherited through a group or All users.
- After any role loss, the requester retains a read-only view of their own past
  and future booking rows in My Bookings. This is a booking-row rule, not a
  retained configuration permission. The user gets no configuration, calendar,
  audit, subscription, edit, or cancel access without a current role.

### Other Booking surfaces

- An ordinary user can create a configuration only for an Instrument they own;
  a system administrator can use any eligible Instrument. The creator is a
  persisted Owner.
- Instance-wide **Default shared with** starts as **All users** and gives new
  configurations initial Booker access. Its choices are:
  1. All users, a dynamic audience including future accounts.
  2. Selected users and groups, exactly the grantees selected below.
  3. Only me, which creates only the creator Owner assignment.
- All Items shows every configuration visible to the caller. Owner/Manager get
  settings and sharing actions; Booker gets Book and View; Viewer gets View
  only. It also
  exposes Add item when the caller owns an eligible unconfigured Instrument,
  or for any eligible Instrument when the caller is a sysadmin.
- The admin **All bookable items** page stays sysadmin-only and keeps settings,
  enabled, archive, and existing bulk actions. Bulk actions never modify
  access.
- Inventory ownership transfer offers an optional "Also transfer Booking
  configuration ownership" choice when the represented user may change Booking
  Owners.
  It adds the incoming Instrument owner as Booking Owner, removes only the
  outgoing Instrument owner's direct Owner assignment, and preserves all other
  assignments.

## Prototype question and evaluation criteria

The question is:

> Does the principal-first editor let an Owner or Manager safely understand
> inherited access, make several assignment changes as one draft, and recover
> from conflicts on desktop and mobile without teaching the whole role model
> inside the Access tab?

Evaluate the selected design against these criteria:

1. Can a first-time Owner find a person/group and assign the intended role?
2. Can an Owner see why a user has an effective role higher than their direct
   role?
3. Can a Manager tell immediately that Owner rows are visible but immutable?
4. Can the user predict the result before pressing Save changes?
5. Are last-owner, unavailable-holder, self-leave, and stale-version states
   noticeable at the point of action?
6. Does the editor remain usable at 320 CSS pixels without horizontal page
   scrolling or a second mobile-only design?
7. Is the Access tab keyboard-operable with correct arrow-key navigation, and
   do Save, Cancel, and status focus remain predictable?
8. Can the same component render a second role scheme without Booking-specific
   branches?

## Selected design: principal-first Access tab

- One responsive table/list ordered by grantee.
- Columns on wide screens: grantee, direct role, effective access/sources,
  status, action. Collapse each row into a labelled card on narrow screens.
- Search/add sits above the assignment list. Selecting a grantee opens an
  inline role control and stages the row.
- This design optimizes for answering "what can Ada or Imaging group do?" and
  for scanning duplicate or unavailable principals.

## Required scenarios

Provide a visible scenario selector. A scenario may open the editor or show a
surrounding flow, but uses the same selected editor and state semantics.

| Scenario | Required visible state and interaction |
|---|---|
| Owner, mixed sources | Caller is direct Owner. Ada has direct Viewer plus Manager through Imaging group, so effective Manager is explicit. All users supplies Booker. Multiple group sources can be expanded. |
| Manager | Owner rows are visible and disabled with an explanation; Manager can add/change/remove only Manager, Booker, Viewer. Archive is absent. |
| System administrator | "Owner (system administrator)" is shown as assumed access without a fake assignment row. An optional explicit assignment, if present in a separate fixture, is distinguishable. |
| Add and change | Search returns users and valid groups with type/status. Add a new Booker, promote a Viewer, act as Owner to demote a Manager, and show the staged review. All users offers only Booker and Viewer. |
| Remove and leave | Remove another grantee; leave directly while inherited access remains; leave when final access is lost and explain that only the caller's own booking rows remain read-only. |
| Last Owner | Attempting to remove or demote the final persisted Owner is disabled and explained before submission. Group Owner with zero active members still counts and is labelled. |
| Unavailable holder | Disabled user and deleted-group snapshot remain in the list, labelled unavailable, selectable for removal only when permitted, and included in the structural Owner invariant. A zero-enabled-member Owner group is also labelled as ineffective. |
| Conflict | Save receives a simulated stale ETag. Preserve the local draft, show that access changed elsewhere, offer Review latest and retry, and never silently overwrite. |
| Global default | Show All users, Selected users and groups, Only me. Selected mode reveals its exact grantee list and explanatory Booker grant. |
| All Items | Owner/Manager rows expose Settings and Access; Booker has Book and View; Viewer has View only; Add item availability is credible. No bulk access action. |
| Ownership transfer | Show the optional Booking transfer checkbox, permission-dependent availability, the exact outgoing/incoming effect, and the per-item behavior for a multi-selection. |
| Role-lost My Bookings | Own bookings remain visible and explicitly read-only after voluntary or involuntary role loss; configuration/calendar links and edit/cancel actions are absent. |
| Sysadmin Owner repair | The admin list flags a configuration with persisted Owner rows but no effective Owner, offers Repair access, and lets implicit sysadmin Owner access repair it without creating a fake sysadmin row. |
| Second role scheme | Render the same editor with Owner, Manager, Contributor, and Reader plus different role descriptions/capability notices. No Booking name may be baked into generic layout code. |

## Prototype state contract

Put fixture types and initial data in
`src/main/webapp/ui/src/modules/booking/prototypes/resourceAccessPrototypeFixtures.ts`.
The story owns all mutable state with React state/reducer; do not put it in
React Query, Zustand, local storage, or MSW.

Use this conceptual shape, adjusting TypeScript details only as needed:

```ts
type PrototypeRole = {
  key: string;
  label: string;
  description: string;
  rank: number;
};

type PrototypeGrantee =
  | { kind: "user"; id: string; name: string; detail: string; status: "active" | "disabled" }
  | {
      kind: "group";
      id: string;
      name: string;
      detail: string;
      status: "active" | "deleted";
      enabledMemberCount?: number;
    }
  | { kind: "audience"; id: "all-users"; name: string; detail: string; status: "active" };

type PrototypeAssignment = {
  grantee: PrototypeGrantee;
  role: string;
};

type PrototypeRoleSource = {
  kind: "direct" | "group" | "audience" | "implicit";
  label: string;
  role: string;
};

type PrototypeAccessState = {
  version: number;
  assignments: PrototypeAssignment[];
  caller: { effectiveRole: string; capabilities: string[]; sources: PrototypeRoleSource[] };
};
```

The draft must be separate from saved state. Cancel restores saved state.
Successful Save atomically replaces saved assignments and increments the
prototype `version`. Conflict mode changes the saved version behind the draft
and exercises recovery. Include a collapsible "Prototype state" panel showing
saved assignments, draft assignments, caller capabilities, and version so a
reviewer can verify every interaction.

## Current state

### Storybook and prototype conventions

`src/main/webapp/ui/.storybook/main.ts:3-8` discovers
`../src/modules/**/*.stories.tsx` and enables the accessibility and Vitest
addons. `src/main/webapp/ui/.storybook/preview.tsx:18-22` sets
`a11y.test = "error"`, so `pnpm test-storybook` turns axe violations into test
failures. The existing `.storybook/main.ts` modification is user-authored and
out of scope; preserve it unchanged.

`src/main/webapp/ui/src/modules/booking/prototypes/AllBookableItemsToolbarPrototype.stories.tsx:1-4`
is the local throwaway-prototype exemplar:

```tsx
// PROTOTYPE ONLY. ...
/* biome-ignore-all lint/style/noJsxLiterals: throwaway prototype copy is intentionally not entering the translation catalog. */
```

It keeps hard-coded English out of the production catalog, uses production UI
primitives, and renders through Storybook. Follow that convention.

The dirty worktree contains two untracked, prototype-only artifacts:

- `ResourceAccessEditorPrototype.stories.tsx`, the selected principal-first
  editor plus all surrounding scenarios and automated acceptance;
- `resourceAccessPrototypeFixtures.ts`, the shared scheme and scenario model.

The role-first and guided story plus their variant switcher were removed by
user direction. Do not recreate them.

The worktree is already on `prototype/booking-resource-access`; that branch and
`feat/booking-permissions` both point at planned base `5f230b8da3`. Treat
`feat/booking-permissions` as the delivery branch after verifying those facts.
Do not try to create the prototype branch again.

Treat principal-first as the selected base design. Use the required human and
automated acceptance checks to decide whether it is acceptable; there is no
remaining alternative-selection decision or `?variant=` state.

`ResourceAccessEditorPrototype.stories.tsx` currently imports the untracked
production primitive `src/modules/common/ui/user-badge.tsx`. That file and the
Storybook configuration change are not owned by this plan. Preserve both. Make
the prototype self-contained with existing committed UI primitives instead of
editing or depending on `user-badge.tsx`.

### The host page is already representative

`BookableItemPage.tsx:251-301` renders the current item header and Bookings,
Details, and Audit log tabs. Its header action region currently contains Create
booking and calendar-subscription controls. The prototype should reproduce the
recognizable page shell and add an Access tab alongside the existing tabs; it
must not import and mount the network-backed production page.

At `BookableItemPage.tsx:319-362`, configuration editing is currently exposed
only when `currentUser.hasSysAdminRole`. The future implementation will use
capabilities, but this prototype should show Owner, Manager, Booker, and Viewer
page actions via its scenario state.

`BookableItemPage.story.tsx:18-65` is a useful router/query harness when a story
needs the real route. This prototype does not need that route or network data;
copy only the surrounding visual context that helps the review.

### Existing reusable UI primitives

Use the wrappers in `src/main/webapp/ui/src/modules/common/ui/`, especially:

- `dialog.tsx` for the separate sysadmin Owner-repair scene;
- `combobox.tsx` for grantee search;
- `radio-group.tsx` for role and Default shared with choices;
- `table.tsx` for the editor's desktop table;
- `badge.tsx`, `button.tsx`, `card.tsx`, `alert.tsx`, and `collapsible.tsx` for
  status, actions, and the debug panel.

Do not import Base UI directly, add a dependency, or copy the legacy
`src/main/webapp/ui/src/components/ShareDialog.tsx`; that dialog belongs to the
old sharing model and is not a role-scheme adapter.

## Commands you will need

Run frontend commands from the repository root. Never add a standalone `--`
after a pnpm script name.

| Purpose | Command | Expected on success |
|---|---|---|
| Type check | `pnpm tsc` | exit 0, no TypeScript errors |
| Lint | `pnpm lint` | exit 0, no fixes applied |
| Storybook static build | `pnpm build-storybook` | exit 0; the resource-access story is included |
| Storybook interaction and axe tests | `pnpm test-storybook` | exit 0; play functions and configured accessibility checks pass |
| Interactive review | `pnpm storybook` | Storybook starts and prints its local URL; keep it running only while reviewing |
| Patch check | `git diff --check` | no output |

No production test or i18n extraction belongs in this throwaway plan. Storybook
play functions automate every accessibility behavior listed in Step 5. The
human review still judges information clarity and actual screen-reader use,
which axe and scripted keyboard input cannot prove.

## Suggested executor toolkit

- Use the `prototype` skill if it is available. This plan uses its throwaway
  branch and capture workflow but has one user-selected design, not an
  alternatives round.
- No TanStack Router source file is in scope, so no TanStack Intent command is
  required.
- Use Storybook interaction tests and its configured a11y addon for prototype
  acceptance. Plan 013 owns the production component and browser suites.

## Scope

### In scope

Create or modify only these prototype artifacts:

- `src/main/webapp/ui/src/modules/booking/prototypes/ResourceAccessEditorPrototype.stories.tsx`
- `src/main/webapp/ui/src/modules/booking/prototypes/resourceAccessPrototypeFixtures.ts`

Reuse committed production components under
`src/main/webapp/ui/src/modules/common/ui/`. Any repository-wide accessibility
baseline defect uncovered by the mandatory full Storybook suite may be fixed
narrowly, but such a fix is delivery-branch work and must not enter the
prototype capture commit.

The only other allowed modifications are:

- `plans/README.md`, status update only after the review
- this plan's `Status` and `Prototype verdict` sections after the review; update
  its Status and the README row together on the delivery branch

### Out of scope

- Any backend, database, REST, permission, Inventory, audit, or production
  Booking change
- Any edit to a production page, route, shared UI primitive, i18n catalog, MSW
  handler, query hook, store, or schema
- `src/main/webapp/ui/.storybook/main.ts` and
  `src/main/webapp/ui/src/modules/common/ui/user-badge.tsx`; both are unrelated
  dirty-worktree files and must remain untouched
- Network requests, persistence, production validation, production tests,
  snapshots, or a new dependency
- Promoting prototype components directly into production
- Additional editor variants, a separate mobile layout, community grantees,
  per-user denies/exclusions, invitation flows, booking privacy, or approval
- Redesigning Booking's navigation, calendar, audit log, or scheduling forms
- Unrelated dirty worktree files

## Git workflow

- Develop on the existing throwaway branch named
  `prototype/booking-resource-access` and commit it as part of this plan; do not
  push or open a PR. If that branch or `feat/booking-permissions` no longer has
  the relationship described in Current state, STOP.
- Before switching branches, inspect `git status --short`. If unrelated dirty
  work cannot be carried safely, STOP and ask the operator how they want the
  branch captured. Do not stash, reset, clean, or discard their work.
- Before any prototype commit or branch switch, record the verified delivery
  branch name in ignored `.claude/plan-012-review.md`. Keep raw review notes and
  the chosen verdict fields there while on the prototype branch; do not edit
  this plan or the README further on the prototype branch.
- Make one capture commit after the review, for example
  `Prototype Booking resource access editor`.
- Stage only the two resource-access prototype paths listed in Scope. Before
  committing, `git diff --cached --name-only` must list exactly those two
  paths. Never use `git add .`; the dirty Storybook configuration,
  `user-badge.tsx`, plans, ADR, and CONTEXT work do not belong in the capture.
- Record the prototype branch and commit SHA in the ignored review note. Return
  to the exact recorded delivery branch without discarding unrelated work, then
  delete the two resource-access prototype artifacts from that branch. Only
  then copy the completed verdict into this plan and update both status fields.
  The capture branch is the recoverable artifact; prototype code must not ship
  in the production diff.

## Steps

### Step 1: Create the shared fixtures and scenario state

Create `resourceAccessPrototypeFixtures.ts` with:

- the Booking role scheme and the fake Contributor/Reader scheme;
- active users, a disabled user, active Lab/Collaboration/Project groups, an
  unavailable deleted-group snapshot, a zero-enabled-member group, and the All
  users audience;
- saved assignments and role-source examples for every scenario in the table;
- caller identities/capabilities for Owner, Manager, Viewer/Booker, sysadmin,
  and run-as where useful;
- pure helpers for highest-role resolution, Owner-invariant presentation,
  staging an atomic replacement, and conflict simulation.

Keep these helpers prototype-simple. They only need to make the UI internally
consistent; do not attempt to implement production authorization.

**Verify**:

```bash
pnpm tsc
```

Expected: exit 0 and no TypeScript errors.

### Step 2: Reconcile the selected story shell

Use `ResourceAccessEditorPrototype.stories.tsx` as the sole review harness.
Give it the prototype-only header and lint exemption shown above. Render a
recognizable Confocal microscope bookable-item page with current role/status,
Create booking, calendar subscription, and an Access tab alongside the three
existing tabs. Add:

- a labelled scenario selector;
- a labelled role-scheme selector for Booking versus the fake scheme;
- a collapsible debug-state panel;
- a width-constrained story layout that can be reviewed at desktop and 320 px.

Remove its dependency on the unrelated `user-badge.tsx`; use existing committed
primitives. Keep all scenario and interaction semantics in this story and the
fixture module.

**Verify**:

```bash
pnpm build-storybook
```

Expected: exit 0 and output lists a successful Storybook build containing the
resource-access prototype story.

### Step 3: Implement the selected editor over one draft model

Implement the principal-first design exactly as described in "Selected
design". It must:

- render inside an item-specific Access tab and associated tab panel;
- expose the caller's effective role and its sources;
- distinguish direct assignments from inherited/implicit sources;
- show grantee type and unavailable status without relying on color alone;
- stage additions, role changes, removals, and leave;
- disable impossible Owner changes before Save;
- present Save changes, Cancel, dirty state, and a review summary;
- simulate a pending Save and stale conflict without timers longer than a
  normal interaction delay;
- keep an accessible name on every icon-only action;
- keep destructive leave/removal actions visually and textually distinct.

Use role definitions from the selected scheme. No generic editor JSX may test
for `"BOOKER"`, `"VIEWER"`, "Booking", or a Booking capability key.

**Verify**:

```bash
pnpm tsc
pnpm lint
pnpm build-storybook
```

Expected: all three exit 0; lint applies no fixes.

### Step 4: Add all surrounding-flow scenarios

Implement the Global default, All Items, ownership-transfer, role-lost My
Bookings, and sysadmin Owner-repair scenarios in the sole prototype story.
They can be compact guided scenes rather than full application clones,
but their labels and consequences must be
specific enough to evaluate the fixed decisions.

The ownership-transfer scenario must show mixed bulk selection: an Instrument
without a Booking configuration, an eligible configuration, and one for which
the represented user cannot change Owners. Explain that each Inventory item is
atomic and that one failure does not partially change that item's Booking
access.

The role-lost My Bookings scenario must cover voluntary and involuntary loss.
It must not leave a route or button that implies configuration/calendar access.
The caller's booking rows remain visible but read-only.

**Verify**:

```bash
pnpm build-storybook
git diff --check
```

Expected: Storybook builds successfully and the patch check prints nothing.

### Step 5: Automate accessibility behavior, then conduct the acceptance review

Add a Storybook play function for the editor and surrounding scenarios.
Use semantic queries and keyboard input. Automated acceptance must cover:

- axe checks through the configured `a11y.test = "error"` policy;
- accessible names and descriptions for search, role controls, icon buttons,
  status, Save, Cancel, and destructive actions;
- keyboard-only search, add, role change, remove, Save, and Cancel;
- correct tab semantics, arrow-key tab navigation, predictable Save/Cancel
  focus, and focus on the successful-save status after Save disables itself;
- live-region announcements for dirty state, successful save, stale conflict,
  validation failure, and request failure;
- a 320 CSS-pixel viewport with no page-level horizontal overflow or clipped
  action;
- forced-colors and reduced-motion behavior where the browser harness can
  emulate them.

Run the automated checks before asking a human to select a design:

```bash
pnpm test-storybook
```

Expected: every interaction and accessibility test passes. Do not waive an axe
rule or add a broad suppression to make the prototype pass.

Start Storybook and review the selected editor with Owner mixed-sources,
Manager, last-Owner, conflict, and fake-scheme scenarios. Then review every
surrounding scenario once. Use the Storybook viewport and accessibility panels.

Keyboard walkthrough:

1. Reach Access in the item tab list and select it without a pointer.
2. Search for and select a grantee without a pointer.
3. Assign/change a role, stage a removal, and inspect sources.
4. Reach Save changes and Cancel in a predictable order.
5. Press Escape inside the inline editor and confirm it does not unexpectedly
   navigate away or apply the draft.
6. Use Left/Right, Home, and End in the tab list and confirm the active tab and
   panel remain synchronized.
7. Trigger the last-Owner and conflict paths and confirm their recovery actions
   are reachable and announced.
8. At 320 px, confirm no page-level horizontal scrollbar and no clipped action.

**Verify**:

```bash
pnpm storybook
```

Expected: Storybook starts. The human reviewer completes the walkthrough and
checks the information hierarchy with a real screen reader. The executor
records whether the selected design is accepted in the ignored review note. If
it is not acceptable, leave this plan BLOCKED and stop. Do not start Plan 013.

### Step 6: Record, capture, and remove the prototype

Write every completed "Prototype verdict" value to the ignored review note.
Stage exactly the two Scope artifacts, verify the staged list, commit them on
`prototype/booking-resource-access`, and record the commit SHA in the note.
Return to the delivery branch recorded before Step 1 and confirm its exact name.
Delete the two resource-access prototype artifacts there. Then copy the
verdict from the note into this plan, set this plan and its README row to DONE
together, and retain the note until the final verification succeeds. Do not
delete or edit any other Booking prototype, `.storybook/main.ts`, or
`user-badge.tsx`.

**Verify**:

```bash
git branch --show-current
git log -1 --oneline prototype/booking-resource-access
git diff-tree --no-commit-id --name-only -r prototype/booking-resource-access
git ls-tree -r --name-only prototype/booking-resource-access -- \
  src/main/webapp/ui/src/modules/booking/prototypes/ResourceAccessEditorPrototype.stories.tsx \
  src/main/webapp/ui/src/modules/booking/prototypes/resourceAccessPrototypeFixtures.ts
test ! -e src/main/webapp/ui/src/modules/booking/prototypes/ResourceAccessEditorPrototype.stories.tsx
test ! -e src/main/webapp/ui/src/modules/booking/prototypes/resourceAccessPrototypeFixtures.ts
git diff --check
```

Expected: the first command exactly matches the delivery branch recorded in
`.claude/plan-012-review.md`, the second prints the recorded capture commit, and
the third lists exactly the two Scope paths and no unrelated file. The fourth
also proves that both artifacts exist on the capture branch. Both `test`
commands exit 0, `git diff --check` prints nothing, and this plan plus its README
row both say DONE. If the commit contains any other path, do not continue to
Plan 013.

## Prototype verdict

Plan 013 must not begin while any field below says `PENDING`.

- **Selected base variant**: Principal-first Access tab (sole remaining design)
- **Provisional favorite before acceptance**: principal-first
- **Elements borrowed from other variants**: None
- **Why this combination won**: The user selected principal-first and removed the alternatives before acceptance; the
  remaining design exposes direct and effective access together without a second interaction model.
- **Rejected interaction(s) and reason**: Role-first grouping and guided review were removed by user direction; no
  alternative selector remains.
- **Mobile findings at 320 px**: PASS in automation; the Access panel stays within 320 CSS px, rows reflow to labelled
  cards, controls wrap, and input focus rings do not create horizontal overflow.
- **Keyboard/focus findings**: PASS in automation for Access-tab entry and arrow navigation, search/add, role change,
  removal, Save, Cancel, and successful-save status focus. Real-screen-reader verdict remains part of Reviewer and review date.
- **Final terminology and important copy**: Access; Direct role; Effective access and sources; Booker; Save changes;
  explicit staged, blocked, conflict, request-failure, and leave consequences.
- **Second-scheme generality findings**: PASS; Dataset archive renders Owner, Manager, Contributor, and Reader through
  the same editor without Booker or Viewer copy in the generic panel.
- **Prototype branch**: `prototype/booking-resource-access`
- **Prototype commit**: `564a18ecad2d9126ada5943546904d6070148413`
- **Reviewer and review date**: Accepted by the user on 2026-08-31 after the final Access-tab review

## Test plan

This plan adds no production tests. The prototype is thrown away, has no server
interface, and must not become production by accretion. Verification consists
of:

- TypeScript and lint checks for both prototype artifacts;
- a successful static Storybook build;
- passing Storybook interaction and axe tests for the selected design;
- the explicit keyboard, 320 px, conflict, and fake-scheme walkthrough;
- automated focus and announcement checks, with forced-colors and
  reduced-motion emulation owned by Plan 013's Browser Mode acceptance;
- human screen-reader and information-clarity review;
- a recorded human acceptance verdict and recoverable prototype branch/commit;
- absence of prototype-only files from the delivery branch.

Plan 013 converts the selected interaction into production components and owns
unit and cross-browser tests.

## Done criteria

- [x] The selected principal-first editor uses one state model for every
  scenario; discarded alternatives and their switcher are absent.
- [x] Every required scenario, including the fake scheme, is reviewable.
- [x] Save/Cancel, stale conflict, last Owner, unavailable holder, leave, and
  inherited sources are interactive rather than static annotations.
- [x] `pnpm test-storybook` passes automated axe, keyboard, focus,
  announcement, and 320 px acceptance. Forced-colors and reduced-motion media
  emulation is assigned to Plan 013's Chromium Browser Mode harness, which can
  drive those media features; Storybook play cannot.
- [x] The human screen-reader and information-clarity review is complete.
- [x] Every `PENDING` field in "Prototype verdict" has been replaced.
- [x] `pnpm tsc`, `pnpm lint`, `pnpm build-storybook`, and
  `pnpm test-storybook` exit 0.
- [x] `git diff --check` prints nothing.
- [x] The throwaway branch/commit exists and is recorded.
- [x] The two resource-access prototype artifacts do not exist on the delivery
  branch; `.storybook/main.ts` and `user-badge.tsx` remain untouched.
- [x] `plans/README.md` marks Plan 012 DONE.

## STOP conditions

Stop and report; do not improvise if:

- the merged bookable-item page or Storybook/prototype infrastructure no
  longer matches "Current state";
- creating the throwaway branch would require stashing, resetting, cleaning,
  or discarding unrelated dirty work;
- a shared production component needs a non-accessibility behavior change to
  make the prototype work; repository-wide accessibility baseline fixes found
  by the mandatory full Storybook suite are allowed but stay out of capture;
- the prototype cannot be detached from the unrelated `user-badge.tsx` without
  changing that production file;
- the editor cannot represent the fake scheme without Booking-specific role
  branches;
- the Access tab cannot work at 320 px or by keyboard using existing UI
  wrappers;
- the selected design does not satisfy the evaluation criteria;
- the human reviewer has not accepted the design after the real-screen-reader
  walkthrough;
- any verification command fails twice after one reasonable correction.

## Maintenance notes

- The prototype commit is design evidence, not a code source to cherry-pick.
  Production code must be rewritten with typed API contracts, i18n, loading and
  error states, and tests.
- Reviewers should focus on whether inherited sources and atomic draft changes
  remain legible, not on polishing fixture data or animation.
- Plan 013 is intentionally blocked on the verdict because it supplies the
  exact production component shape and browser assertions.
