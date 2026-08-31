# Plan 005: Finish the merged bookable item page and remove the legacy editor

> **Executor instructions**: Follow this plan step by step. Run every verification
> command and confirm the expected result before moving on. If a STOP condition
> occurs, stop and report it. Do not improvise. When done, update this plan's row
> in `plans/README.md` unless a reviewer says they maintain the index.
>
> **Drift check, run first**:
> `git diff --stat 37e013af4..HEAD -- src/main/webapp/ui/src/modules/booking/pages/bookable-items src/main/webapp/ui/src/modules/booking/pages/__tests__/BookingPage.test.tsx src/main/webapp/ui/src/modules/common/app/router.tsx src/main/webapp/ui/src/modules/common/collection-form src/main/webapp/ui/src/modules/common/collection/collectionConfig.ts src/main/webapp/ui/src/modules/common/ui/field.tsx src/main/webapp/ui/src/modules/common/ui/inventory-item.tsx src/main/webapp/ui/src/modules/common/ui/inventory-item.test.tsx src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json src/main/webapp/ui/src/modules/common/i18n/resources.d.ts`
> Compare any changed in-scope file with the Current state excerpts. Also run
> `git status --short` and identify operator-approved concurrent changes before
> editing. The shared form changes named under Prerequisites are expected to land
> before this plan. Any incompatible interface is a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: the concurrent shared `RenderFields` work described below
- **Category**: direction
- **Planned at**: commit `37e013af4`, 2026-08-27

## Why this matters

The branch already merged viewing and editing into `BookableItemPage`, but it
stopped at an earlier two-tab layout. Bookings still sit under Details, the edit
button is in the page header, and the obsolete numeric-ID editor remains
registered and linked. The selected prototype resolves those loose ends with a
Bookings-first tab layout and one item-details card that swaps between read and
edit modes.

This plan finishes that merge without changing the booking model or API. It
keeps ordinary-user read access, keeps editing sysadmin-only, and removes the
second implementation of the same form.

## Prerequisites and ownership boundary

Another agent owns the shared collection-form work. It must land before this
plan starts. The expected interfaces are:

- `RenderFields` accepts `layout="inline"` and preserves the existing default
  stacked layout.
- Field rendering supports compact density, UI fields, `time` widgets, and
  transparent sections.
- Inline fields align labels and controls in a shared two-column grid.

Do not reimplement, revert, or redesign those interfaces here. Run the shared
form's focused tests before editing the Booking page. If the landed interface
differs, stop and report the mismatch so the page plan can be adjusted without
colliding with that owner.

## Current state

### The merged route and edit URL state already exist

`src/main/webapp/ui/src/modules/booking/pages/bookable-items/routes.ts:33-67`
defines `tab` and `edit` search state and keeps the readable Global ID route:

```ts
export const bookableItemTabParser = parseAsStringLiteral(["details", "audit"] as const)
  .withDefault("details")
  .withOptions({ history: "replace", clearOnDefault: true });

export function createBookableItemRoute<TParentRoute extends AnyRoute>(bookingRoute: TParentRoute) {
  return createRoute({
    getParentRoute: () => bookingRoute,
    path: "/bookable-items/$globalId",
    validateSearch: bookableItemSearch,
    component: BookableItemPage,
  });
}
```

Keep `/booking/bookable-items/$globalId`. Extend the tab parser to
`"bookings" | "details" | "audit"`, make Bookings the clear-on-default value,
and retain `?tab=details&edit=true` for a direct link into the editor.

### Production is still the earlier two-tab layout

`BookableItemPage.tsx:275-331` renders Details first, puts the edit button in
`SpotlightHeader`, and keeps event lists inside the Details panel:

```tsx
<Tabs.Root value={tab} onValueChange={(value) => setSearch({ tab: ..., edit: false })}>
  <SpotlightHeader action={canEdit && !editing && tab === "details" ? <Button ... /> : null} />
  <Tabs.List>
    <PageTab value="details">...</PageTab>
    <PageTab value="audit">...</PageTab>
  </Tabs.List>
  <Tabs.Panel value="details">
    <Card>...</Card>
    <BookingEventList period="upcoming" ... />
    <BookingEventList period="past" ... />
  </Tabs.Panel>
  <Tabs.Panel value="audit">...</Tabs.Panel>
</Tabs.Root>
```

The page already gates edit mode with `currentUser.hasSysAdminRole`, PATCHes the
numeric configuration ID returned by the Global ID lookup, and invalidates the
booking-configuration query. Preserve those behaviors. Replace its current
tab-change reset with the mounted-panel behavior below.

### The selected prototype is variant D

`src/main/webapp/ui/src/modules/booking/prototypes/MergedBookableItemPage.prototype.stories.tsx:607-611`
records the selected order:

```ts
const PAGE_TABS = [
  { value: "bookings", label: "Bookings" },
  { value: "details", label: "Details" },
  { value: "audit", label: "Audit log" },
];
```

Lines 696-740 put the event lists in Bookings and the read/edit swap in one
Details card. The prototype also records a real state bug at lines 1020-1029:
unmounting the Details panel can separate a dirty form from its page-owned edit
state. Production must keep the page-owned `edit` state and keep the Details
component tree mounted between tab switches. Do not move edit state into a
card-local hook.

TanStack Router does not provide named parallel route trees or a route-level
keep-alive primitive for this case. Search changes on the same route do not by
themselves remount the route component, but conditional tab-panel rendering can
still unmount its descendants. The installed Base UI Tabs API directly supports
`<Tabs.Panel keepMounted>`. Use it for Details so a dirty draft survives a tab
switch without another router, store, or cache. Preserve `edit=true` in search
state while another tab is selected. Disable tab changes while the PATCH is
pending so the page never hides an unresolved write.

The prototype contains a `description` field that does not exist in
`BookingConfiguration`, `BookingConfigurationUpdateInputSchema`, or the REST
resource. Do not add it. That would require persistence, API, audit, and form
work outside this page merge.

### Item identity must stay on one title line

`src/main/webapp/ui/src/modules/common/ui/inventory-item.tsx:61-82` already has
the needed row behavior, but not the required heading semantics:

```tsx
idPlacement?: GlobalIdPlacement;
...
const idInTitle = compact || idPlacement === "title";
...
<ItemTitle>
  <span className="min-w-0 truncate">{name}</span>
  {idInTitle ? badge : null}
</ItemTitle>
```

Do not pass an `h1` as `name` and do not wrap `InventoryItem` in an `h1`.
`InventoryItem` currently wraps `name` in a `span` and `ItemTitle` is a `div`,
so either approach produces nested or misleading title markup. Make the
smallest shared change in `InventoryItem`: add an optional semantic name
element such as `nameAs="h1"`, defaulting to the current `span`. Keep
`ItemTitle` as the flex-row `div`. The bookable page requests `h1`; the name
text is rendered directly inside it, and the Global ID badge is its sibling in
the same row rather than part of the heading. Put timezone and readable
inventory location, when supplied by the existing target projection, on the
description row. Do not recreate the badge/link markup inside
`BookableItemPage`.

### The legacy editor is still a separate route

- `routes.ts:70-75` exports `createEditBookableItemRoute` at
  `/config/bookable-items/$id/edit`.
- `src/modules/common/app/router.tsx:49` registers it.
- `BookableItemsPage.tsx:110-118` links every admin edit action to it.
- `EditBookableItemPage.tsx` and its test duplicate fetch, form, PATCH, and
  navigation behavior.
- `BookableItemsPage.story.tsx` and `BookingPage.test.tsx` also register it.

Delete that path rather than keeping two editors. A row whose target is null has
no Global ID destination; keep Delete available but omit View and Edit for that
row. Restoring or retargeting an unreadable instrument is separate work.

### Applicable repository conventions

- Run TanStack Intent guidance before editing the route parser or links.
- Use Formisch and the existing `bookingConfigurationFields` and
  `SchedulingSettingsFields` ownership. Do not create another booking-settings
  schema.
- English frontend text must use semantic i18n keys. Extract with
  `pnpm run i18n:extract --sync-primary`; never use `--sync-all`.
- Browser Mode tests use Vitest's Playwright provider, MSW, a story wrapper, and
  a page object. Follow
  `src/modules/booking/pages/all-bookable-items/BookingPages.spec.tsx`.
- Run commands from the repository root and never add a standalone `--` after a
  pnpm script name.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Router guidance | `pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core` | exit 0 |
| Navigation guidance | `pnpm dlx @tanstack/intent@latest load @tanstack/router-core#router-core/navigation` | exit 0 |
| Shared form baseline | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/common/collection-form/RenderFields.spec.tsx` | all tests pass |
| Focused unit tests | `pnpm test src/modules/common/ui/inventory-item.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemsPage.test.tsx src/modules/booking/pages/__tests__/BookingPage.test.tsx` | all tests pass |
| Browser inner loop | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` | all tests pass in Chromium |
| Browser final | `pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` | all tests pass in Chromium, Firefox, and WebKit |
| Extract English keys | `pnpm run i18n:extract --sync-primary` | exit 0; only intended English changes |
| Generate i18n types | `pnpm run i18n:types` | exit 0 |
| Validate i18n | `pnpm run i18n:lint` | exit 0 |
| Type check | `pnpm tsc` | exit 0, no TypeScript errors |
| Lint | `pnpm lint` | exit 0, no Biome errors |

## Suggested executor toolkit

- Use the `react-testing-library` skill for the focused unit tests.
- Use the `rspace-browser-tests` skill for `BookableItemPage.spec.tsx`.
- Keep the `ponytail` skill active. Delete the duplicate route and component;
  do not add a compatibility page or new state store.

## Scope

### In scope

- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/bookingConfiguration.ts`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/schedulingSettings.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/routes.ts`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemsPage.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemsPage.story.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/EditBookableItemPage.tsx` (delete)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/EditBookableItemPage.test.tsx` (delete)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/__tests__/BookableItemsPage.test.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.story.tsx` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/pageObjects/BookableItemPage.ts` (create)
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/mocks/bookableItemsMocks.ts`
- `src/main/webapp/ui/src/modules/booking/pages/__tests__/BookingPage.test.tsx`
- `src/main/webapp/ui/src/modules/common/app/router.tsx`
- `src/main/webapp/ui/src/modules/common/ui/inventory-item.tsx`
- `src/main/webapp/ui/src/modules/common/ui/inventory-item.test.tsx` (create)
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json`
- `src/main/webapp/ui/src/modules/common/i18n/resources.d.ts`
- `plans/README.md`, status only after implementation

### Out of scope

- The shared collection-form files owned by the prerequisite agent.
- Backend Java, REST resource shapes, Liquibase, and audit publication.
- A `description` property for bookable items.
- Calendar-subscription UI. Plan 007 adds it after the audit contract is stable.
- Audit endpoint or audit-table changes. Plan 006 owns them.
- A redirect or compatibility component for the removed numeric-ID edit URL.
- Editing a configuration whose target is null or unreadable.
- A global unsaved-navigation blocker. Mounted tab panels preserve the draft;
  route-leaving behavior remains unchanged.
- Deleting `MergedBookableItemPage.prototype.stories.tsx`. Plans 006 and 007
  still use its audit and calendar decisions.
- New dependencies, a new state store, or a generic editable-card abstraction.

## Git workflow

- Suggested branch: `feat/merged-bookable-item-page`
- Make logical commits for page layout and form swap, legacy route deletion,
  then tests and translations.
- Match the repository's imperative commit style, for example
  `Implement booking item details page`.
- Do not push or open a pull request unless instructed.

## Steps

### Step 1: Confirm the shared form prerequisite

Run the shared form baseline command. Inspect the live exported types and
confirm the interfaces listed under Prerequisites exist. Confirm the stacked
default still lets Add Bookable Item and Booking Settings call their current
components without layout changes.

Do not edit the shared form files. If the baseline fails or the interfaces
differ, stop before changing the page.

**Verify**:
`VITEST_BROWSERS=chromium pnpm test-browser src/modules/common/collection-form/RenderFields.spec.tsx`
passes.

### Step 2: Make Bookings the default URL-backed tab

Run both TanStack Intent commands. In `routes.ts`:

1. Change the tab literal union to `bookings`, `details`, and `audit`.
2. Make `bookings` the parser default and clear it from the URL.
3. Keep non-default tabs linkable as `?tab=details` and `?tab=audit`.
4. Keep `edit=true` optional. It controls the mounted Details panel even while
   another tab is selected, so returning to Details restores the same draft.
5. Update the return type and normalizer without requiring every existing item
   link to pass `search`.

In `BookableItemPage.tsx`, make the default panel Bookings. Keep one stable
`cutoff` instant per page mount and render upcoming and past lists only inside
that panel. The Details panel contains only the item-details card. The Audit log
panel keeps `BookableItemAuditLog` unchanged in this plan.

Set `keepMounted` on the Details panel. The controlled tab handler changes only
`tab` and preserves `edit`. A round trip of open editor, change a value, switch
to Audit log, and return to Details must show the same unsaved draft. While a
PATCH is pending, disable the tab triggers and ignore programmatic tab-change
callbacks until the mutation settles.

Treat `keepMounted` as an accessibility risk, not only a state-preservation
mechanism. Preserve the Base UI tabs contract: one `tablist`; one `tab` per
panel; matching `tabpanel`, `aria-controls`, and `aria-labelledby`
relationships; and exactly one `aria-selected="true"` tab. Only the active
`tabpanel` may be exposed in the accessibility tree. An inactive mounted
Details panel and every form control inside it must be hidden from assistive
technology and absent from the sequential tab order. Do not replace Base UI's
hidden-state behavior with CSS that merely moves or visually conceals the
panel. Arrow keys move between tabs according to the component's documented
orientation, Tab enters the active panel, and neither the tabs nor the mounted
form may create a keyboard trap.

**Verify**:
`pnpm test src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx`
passes after updating its tab and lazy-query expectations.

### Step 3: Use the selected one-line identity header

Extend `InventoryItem` with the optional semantic name element described in
Current state, then replace the custom icon, heading, and Global ID badge
assembly with that component:

- pass the item name and Global ID;
- pass the inventory record URL and localized link label;
- set `idPlacement="title"`;
- pass `nameAs="h1"` and render the name text directly in it;
- keep the name truncatable and the ID badge non-shrinking on the same title
  row;
- show timezone and, when both existing parent fields are non-null, reuse
  `InventoryLocationLink` for the readable location on the second row;
- keep Enabled or Disabled and the later action slot at the right of the header.

Keep the page centered at the prototype's `max-w-5xl` width. Do not add another
card surface around the identity block.

Add shared-component regression coverage for the unchanged default `span` and
the optional `h1`. Add a page assertion that a deliberately long name produces
exactly one valid `h1` and one Global ID link, with no nested heading. Add a real
browser geometry assertion at narrow width that the title row does not become
two name lines or overflow horizontally.

**Verify**:
`VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx`
passes once the browser harness is added in Step 6.

### Step 4: Move editing into the Details card header

Keep `edit` controlled by the page search state. Do not copy the prototype's
card-local `useState`.

1. Add `CardAction` to the Details card header.
2. In read mode, show the sysadmin-only Edit button there.
3. In edit mode, replace that slot with same-sized Save and Cancel buttons.
   Give the Formisch `<Form>` a stable ID and use the Save button's `form`
   attribute so the header button submits the form without nested forms or an
   imperative form call.
4. Cancel clears `edit` and remounts or resets the form from the saved query
   value. It sends no PATCH.
5. A successful PATCH invalidates the existing booking-configuration query and
   clears `edit`. A failure keeps the form open, keeps user input, and shows the
   localized error.
6. Disable Save and Cancel while the mutation is pending. Keep `aria-busy` on
   Save. Disable tab navigation for the same interval. Announce the pending
   operation and its successful completion through a polite `role="status"`
   region without moving focus.
7. After Cancel or a successful PATCH replaces the form controls with the
   readout, move focus to the restored Edit button. A failed PATCH keeps focus
   in the form and exposes its error through an accessible alert.
8. Gate the mode itself on `hasSysAdminRole`, not just the Edit button. An
   ordinary user who opens `?tab=details&edit=true` remains read-only.

For every invalid scheduling field, set `aria-invalid="true"` on the exact
control and associate its localized error and correction guidance with
`aria-describedby`. Do not rely on color, an icon, or a page-level alert alone
to identify the field or explain how to correct it. Preserve the invalid value
and focus the first invalid control after an attempted Save.

Use the shared scheduling-settings owner to render the same fields in Add and
Edit. Adapt `SchedulingSettingsFields` to the landed inline layout rather than
copying its validation and full-day/buffer behavior into the page. The stacked
Add and Booking Settings forms must remain visually and behaviorally unchanged.

The readout and form should follow the same field order. The Enabled value can
remain represented by the header badge; all persisted scheduling fields must
remain editable. Do not add Description.

**Verify**:
`pnpm test src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/AddBookableItemPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookingSettingsPage.test.tsx`
passes.

### Step 5: Delete the numeric-ID editor and retarget callers

Delete `EditBookableItemPage.tsx`, its test, and
`createEditBookableItemRoute`. Remove the route import and registration from the
common router, the Booking shell test, and `BookableItemsPage.story.tsx`.

In `BookableItemsPage.tsx`, point the pencil action for a resolved target to:

```text
/booking/bookable-items/$globalId?tab=details&edit=true
```

Use a typed TanStack `Link` with `params` and `search`, not a hand-built href.
Do not render the edit link when `target` is null, because there is no Global ID
route to receive it. Update table tests for both resolved and null targets.

Remove translations that became unreferenced only because the old page was
deleted. Keep shared Save and error messages used by the merged form.

**Verify**:

```text
rg -n "createEditBookableItemRoute|EditBookableItemPage|/config/bookable-items/\$id/edit" src/main/webapp/ui/src/modules
```

returns no matches, and the focused unit-test command passes.

### Step 6: Add real-browser layout and interaction coverage

Create `BookableItemPage.story.tsx` with the same router, QueryClient,
`NuqsAdapter`, and MSW-compatible structure used by the existing Booking page
stories. Reuse `bookableItemDetailsHandlers` and extend the shared mock file only
for data the page needs. Do not duplicate a second fixture set.

Create a small page object and `BookableItemPage.spec.tsx`. Cover:

- Bookings is selected at the bare Global ID URL and both event sections are
  visible.
- Details and Audit log switch panels and update the URL.
- Edit replaces read values with controls in the same card. Save and Cancel
  occupy the card-header action slot.
- Switching tabs while editing preserves the mounted form and its unsaved
  values. Returning to Details shows the same draft and `edit=true` remains in
  the URL.
- The tab structure has the expected `tablist`, `tab`, and `tabpanel` roles,
  selected state, control/label relationships, and arrow-key behavior. At every
  switch, only the active panel is exposed; inactive mounted panels and their
  controls are absent from the accessibility tree and tab order.
- Tab switches are unavailable while a PATCH is pending and become available
  again after success or failure.
- Keyboard-only use covers tab navigation, entering edit mode, changing a
  field, and Save/Cancel. Cancel and successful Save return focus to Edit; a
  failed Save keeps the form and its accessible error available. Include an
  invalid-field case that asserts `aria-invalid`, `aria-describedby`, useful
  correction text, and focus on the first invalid control. Assert polite PATCH
  pending/completion announcements and no keyboard trap.
- At a 320px viewport, the identity, tabs, card actions, and inline form fit
  without document-level horizontal overflow.
- The long item name remains one truncated title row beside the Global ID badge.
- The read and edit presentations pass the browser accessibility scan.

Treat this as explicit acceptance evidence for WCAG 2.2 SC 1.4.3, 1.4.4,
1.4.10, 1.4.11, 1.4.12, 2.1.1, 2.1.2, 2.4.3, 2.4.7, 2.4.11, 2.5.8,
3.3.1, 3.3.2, 3.3.3, 4.1.2, and 4.1.3. Manually verify the affected page and
form at default and 200% text size, at
400% browser zoom/320 CSS px width, with the WCAG text-spacing overrides
(line-height 1.5, paragraph spacing 2 times the font size, letter spacing
0.12em, and word spacing 0.16em), in light and dark themes, and in forced-colors
or the platform high-contrast mode. There must be no loss of content or
function, overlap, clipping, or two-dimensional page scrolling. Confirm text
contrast of at least 4.5:1 (3:1 for large text), non-text UI and focus-indicator
contrast of at least 3:1, visible focus that is not entirely obscured, and
pointer targets of at least 24 by 24 CSS px or a documented WCAG spacing
exception. For the practical AAA goals in SC 2.4.12, 2.4.13, and 2.5.5, keep
focus wholly unobscured, use a focus
indicator equivalent to a 2 CSS px perimeter with 3:1 state contrast, and
prefer 44 by 44 CSS px targets.

Perform a manual screen-reader pass with NVDA in Firefox or Chrome and
VoiceOver in Safari. Confirm the heading, tabs, active panel, edit fields,
errors, and PATCH announcements are understandable in reading and interaction
order. Record the browser, assistive technology, theme/mode, and result. The
automated accessibility scan and component assertions are regression aids;
they do not by themselves establish WCAG conformance. Plan 007 owns the final
integrated full-page and complete-process check, but this plan must pass the
component-level and page-level checks for everything it changes.

Use geometry assertions only for the layout claims that jsdom cannot test. Use
semantic locators for everything else.

**Verify**:
`pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx`
passes in Chromium, Firefox, and WebKit.

### Step 7: Extract translations and run the final gate

While authoring, use literal `defaultValue` strings only as extraction input.
Run `pnpm run i18n:extract --sync-primary`, review the English diff, remove the
`defaultValue` literals, and regenerate types. Never use `--sync-all`.

Run the full focused and static gate from Commands you will need. Check that no
shared form file changed in this plan's diff.

**Verify**: every command below exits 0:

```text
pnpm run i18n:types
pnpm run i18n:lint
pnpm tsc
pnpm lint
pnpm test src/modules/common/ui/inventory-item.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemsPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/AddBookableItemPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookingSettingsPage.test.tsx src/modules/booking/pages/__tests__/BookingPage.test.tsx
pnpm test-browser src/modules/booking/pages/bookable-items/BookableItemPage.spec.tsx
```

## Test plan

- Keep fetch, PATCH, authorization-gating, retry, and timezone-refresh coverage
  in `BookableItemPage.test.tsx`.
- Move the old editor's successful PATCH and failed PATCH cases into that file
  before deleting `EditBookableItemPage.test.tsx`.
- Keep link and null-target regressions in `BookableItemsPage.test.tsx`.
- Keep route-shell coverage in `BookingPage.test.tsx`.
- Put responsive geometry, tab presentation, inline read/edit swap, and real
  accessibility checks in `BookableItemPage.spec.tsx`.
- Keep keyboard navigation and post-mutation focus assertions in the browser
  spec. An axe scan does not prove either behavior.
- Browser and component tests explicitly own mounted-panel exposure, tab
  semantics, invalid-field relationships, status/error announcements, and the
  absence of keyboard traps. Manual checks own contrast, zoom/reflow, text
  spacing, forced colors, target size, focus visibility/obscuring, and
  screen-reader comprehension; automated scans alone do not prove conformance.
- Model the browser harness on `BookingPages.spec.tsx` and its story/page-object
  files. Use MSW's shared worker and fail on relevant unhandled requests.

## Done criteria

- [ ] `/booking/bookable-items/$globalId` is the only bookable-item view/edit
  route.
- [ ] The bare route opens Bookings; Details and Audit log are URL-backed tabs.
- [ ] Upcoming and past events exist only in the Bookings panel.
- [ ] The item name is the page's single valid `h1`; the Global ID is its
  same-row sibling through the opt-in semantic `InventoryItem` title.
- [ ] The Details card owns the Edit, Save, and Cancel placement while the page
  owns edit state.
- [ ] Details uses `Tabs.Panel keepMounted`; a tab change preserves a dirty
  draft and pending PATCHes prevent tab navigation. Inactive panels and their
  controls are absent from the accessibility tree and tab order.
- [ ] Tabs and edit controls work by keyboard, with deterministic focus after
  Cancel, successful Save, and failed Save.
- [ ] Invalid fields expose `aria-invalid`, associated correction text, and
  first-invalid-field focus; PATCH progress/completion uses status semantics
  and failures use alert semantics.
- [ ] WCAG 2.2 AA checks cover contrast, non-text contrast, 200% text resize,
  400% zoom/320px reflow, text spacing, visible/unobscured focus, 24px targets,
  forced colors, light/dark themes, NVDA, and VoiceOver, with AAA focus and
  44px target improvements applied where practical.
- [ ] Only sysadmins can enter edit mode; ordinary users retain read access.
- [ ] All current persisted scheduling fields still round-trip through PATCH.
- [ ] No description field, backend change, new state store, or dependency was
  added.
- [ ] The legacy route, component, test, imports, and internal links have no
  remaining matches.
- [ ] Unit tests, all three Browser Mode engines, i18n lint, TypeScript, and
  Biome pass.
- [ ] `git status --short` shows no source changes outside Scope and no shared
  form file changed by this executor.
- [ ] The plan status in `plans/README.md` is DONE.

## STOP conditions

Stop and report instead of improvising if:

- The prerequisite shared-form interfaces are missing, renamed, or failing
  their focused test.
- Implementing inline scheduling fields requires changing a shared form file
  owned by the prerequisite agent.
- Product intent requires preserving bookmarks to the numeric-ID edit route.
- A configuration with an unreadable or null target must remain editable.
- The page must add a bookable-item description or another persisted field.
- Ordinary users must edit booking configurations, or existing REST write
  authorization differs from the sysadmin rule.
- Preserving the Details tree requires a second router, global store, or custom
  keep-alive mechanism because the installed Base UI `keepMounted` behavior is
  unavailable or fails its browser test.
- A focused verification command fails twice after a reasonable correction.
- Implementation requires a file listed as out of scope.

## Maintenance notes

- Reviewers should test direct URLs, not only clicks: the bare URL, each tab,
  `?tab=details&edit=true`, and a non-admin opening that edit URL.
- Keep the Global ID route as the public identity. The numeric configuration ID
  remains an internal PATCH detail.
- When scheduling fields change, update the shared field owner, readout order,
  and merged-form tests together.
- Do not delete the merged-page prototype in this plan. Plan 007 is the parity
  gate for its final calendar-subscription decision.
