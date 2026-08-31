# Plan 002: Implement the A4 expanded calendar card

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving on. Stop
> when a condition in "STOP conditions" applies. Do not improvise around it.
> When finished, change this plan's row in `plans/README.md` to `DONE`.
>
> **Drift check, run first**:
>
> ```bash
> git diff --stat 37e013af4..HEAD -- \
>   src/main/webapp/ui/src/modules/booking/components/DayTimeline.tsx \
>   src/main/webapp/ui/src/modules/booking/pages/calendar/BookingEventsCalendar.tsx \
>   src/main/webapp/ui/src/modules/common/ui/popover.tsx
> ```
>
> If any listed file changed, compare the live code with "Current state" before
> editing. Stop if its data flow or public props no longer match this plan.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: direction
- **Planned at**: commit `37e013af4`, 2026-08-27
- **Status**: DONE

## Why this matters

`DayTimelineEventCard` currently expands in place to show only a period, notes,
and caller-supplied actions. The selected A4 prototype gives the expanded
calendar card a usable information order: time first, then the booked item,
booker, purpose, and actions. Blockouts use the same shape without pretending
they have a booker.

The production card sits inside a horizontally scrolling canvas. A 22rem A4
card clips at the visible edge if it stays in that canvas. The prototype's
`PoppedOutA4` story verified that the installed Base UI popover can portal the
card out, constrain it to the visible scroller, track its chip, and restore
focus without custom positioning code. Use that result as part of the A4
implementation. Each event keeps its own expanded state, so users can keep
nearby cards open for comparison even when the popovers overlap.

## Decided design

These choices are part of the plan. Do not reopen them during implementation.

1. The expanded card is a non-modal popover anchored to the existing disclosure
   button. It uses the timeline scroller as its collision boundary when one is
   available, otherwise it uses the viewport.
2. Each event card owns its expanded state. Opening or interacting with another
   event must not close an already open card. The popovers may overlap. Do not
   add sibling collision avoidance, a shared set of open IDs, or a z-index
   activation stack.
3. Treat each popup as a persistent disclosure. Cancel Base UI close requests
   caused by `outside-press` and `focus-out`. Its own trigger, X control, and
   Base UI's Escape handling may close it.
4. Its preferred width is 22rem and its actual width is
   `min(22rem, var(--available-width))`. Do not add a viewport breakpoint.
5. Keep `InventoryItem`'s existing single-line truncated name. Pass
   `idPlacement="title"` so the global ID shares the title row and the location
   gets the description row to itself.
6. Keep the current `renderEventActions` seam. The calendar caller owns route
   links and permission gating; `DayTimelineEventCard` owns the position of the
   returned action row.
7. Ship the two real actions, View details and Edit. Do not add the prototype's
   More menu, Duplicate, Delete, Confirm, or Decline. There is no current need
   for the menu and several of those operations do not exist.
8. Preserve the existing busy-booking presentation. A busy response cannot
   expose the booker or purpose, so A4 applies only to full bookings and
   blockouts.
9. The expanded card uses semantic design-system colors for confirmed bookings.
   Keep the existing amber blockout treatment and its Wrench icon, so color is
   not the only distinction.

## Current state

### Domain vocabulary

`CONTEXT.md` defines the terms this work must use:

- A **calendar card** has a compact state constrained by calendar geometry and
  an expanded state that exposes full display details without changing the
  event's time range.
- A **booking event** may identify the booker, exact period, and permitted notes.
- A **blockout event** has kind-appropriate content and no booker.
- **Booking privacy** `busy` shows only the target and occupied time.

Use those names in types, tests, translation keys, and comments. Do not call the
expanded state a dialog, editor, reservation, or availability event.

### Prototype contract

The source is
`src/main/webapp/ui/src/modules/booking/prototypes/DayTimelineExpandedCardV2.prototype.stories.tsx`:

- `VariantA4`, lines 307 to 459, defines the selected information order and
  action row.
- `EventKindsA4`, lines 897 to 950, defines the confirmed and blockout shapes.
  Its unconfirmed state is exploratory and out of scope.
- `PoppedOutA4`, lines 1035 to 1140, defines the positioning behavior.

A4 has:

- a tinted header containing the exact period, date, duration, and X collapse
  control;
- a definition list with an `InventoryItem`, Booked by, and Purpose;
- for blockouts, a Wrench eyebrow using the event title, no Booked by row, and a
  Notes label;
- a divided action row that disappears when no actions exist.

The prototype uses hard-coded English and raw colors. Production must use the
booking translation catalog and existing design-system tokens.

### Production code

At `src/main/webapp/ui/src/modules/booking/components/DayTimeline.tsx:19-35`,
`DayTimelineEvent` has three variants. A full booking contains `bookedBy`,
`canEdit`, and optional `notes`, but no separate collapsed title or item
identity. A blockout contains `title` and optional `notes`.

At `DayTimeline.tsx:95-183`, `DayTimelineEventCard` is the reusable calendar
card interface. Its controlled and uncontrolled expansion props already form
the correct seam:

```tsx
expanded?: boolean;
onExpandedChange?: (expanded: boolean) => void;
renderEventActions?: (
  event: Extract<DayTimelineEvent, { kind: "booking" }>,
  period: string,
) => React.ReactNode;
```

Do not replace this with an action descriptor interface. The caller already
knows routing and permissions, while the card does not.

At `DayTimeline.tsx:128-181`, expansion changes the compact article in place,
sets `w-72`, and rotates `ChevronRight`. `DayTimelineEventCard` already has an
internal expanded state when callers omit `expanded` and `onExpandedChange`.
Cards rendered outside `DayTimeline` use that path and expand independently.

At `DayTimeline.tsx:254` the timeline parent instead stores one
`expandedEventId`. Lines 388 to 406 compare every event against that ID and
replace it whenever another card opens. This parent state is the only
application-level one-open rule. Remove it and let each keyed card use the
existing internal state. Do not replace it with a set or array.

At lines 347 to 418 the cards live inside an `overflow-x-auto` scroller and a
canvas with a minimum width of 24 hours. Portal each expanded body out of this
canvas.

At
`src/main/webapp/ui/src/modules/booking/pages/calendar/BookingEventsCalendar.tsx:148-160`,
`toTimelineEvent` currently combines target name and booker into `bookedBy` and
drops the target's global ID and parent location. The API value already contains
everything A4 needs. `BookingTargetSchema` in
`src/main/webapp/ui/src/modules/booking/domain/booking.ts:14-24` has target name,
global ID, parent-container name, and parent-container global ID.

At `BookingEventsCalendar.tsx:189-212`, `BookingActions` already gates Edit on
`event.canEdit` and always offers View details. Preserve those decisions and
change only the row presentation.

At `src/main/webapp/ui/src/modules/common/ui/inventory-item.tsx:42-101`, the
name already uses single-line truncation, and the component supports placing
the global ID in the title row. Use `idPlacement="title"` without changing the
shared component. Do not reproduce the prototype's descendant selectors in
booking code.

At `src/main/webapp/ui/src/modules/common/ui/popover.tsx:6-65`, the shared
popover wrapper already provides Root, Trigger, Content, Title, and Description.
`PopoverContent` forwards only side and alignment options to the Base UI
positioner. Extend this wrapper instead of importing Base UI directly in booking
code. All new positioner props must remain optional so existing popovers keep
their current layout. The installed Base UI 1.7.0 `Popover.Root` passes
`outside-press` and `focus-out` reasons to `onOpenChange`, and
`eventDetails.cancel()` prevents those automatic closes. Use that callback in
`DayTimelineEventCard`; the shared wrapper needs no dismissal option.

## Commands you will need

Run all commands from the repository root. Never add a standalone `--` after a
pnpm script name.

| Purpose | Command | Expected on success |
|---|---|---|
| Unit tests | `pnpm test src/modules/booking/components/DayTimeline.test.tsx src/modules/booking/pages/calendar/__tests__/CalendarPage.test.tsx` | selected files pass |
| Browser test, inner loop | `VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/components/DayTimeline.spec.tsx` | selected file passes in Chromium |
| Browser test, final | `pnpm test-browser src/modules/booking/components/DayTimeline.spec.tsx` | selected file passes in Chromium, Firefox, and WebKit |
| Type check | `pnpm tsc` | exit 0, no errors |
| Lint | `pnpm lint` | exit 0, no fixes applied |
| i18n types | `pnpm run i18n:types` | generated resource types updated |
| i18n lint | `pnpm run i18n:lint` | exit 0 |
| i18n drift | `pnpm run i18n:check` | exit 0, no catalog changes needed |

## Suggested executor toolkit

- Use the `react-testing-library` skill for `DayTimeline.test.tsx`.
- Use the `rspace-browser-tests` skill before creating
  `DayTimeline.spec.tsx`. Follow its Vitest Browser Mode and page-object pattern.
- Use `ponytail` to resist adding an action model, overflow menu, duration
  library, or a second mobile presentation.

## Scope

### In scope

Only modify or create these files:

- `src/main/webapp/ui/src/modules/booking/components/DayTimeline.tsx`
- `src/main/webapp/ui/src/modules/booking/components/DayTimeline.test.tsx`
- `src/main/webapp/ui/src/modules/booking/components/DayTimeline.stories.tsx`
- `src/main/webapp/ui/src/modules/booking/components/DayTimeline.story.tsx`
- `src/main/webapp/ui/src/modules/booking/components/DayTimeline.spec.tsx`
- `src/main/webapp/ui/src/modules/booking/components/pageObjects/DayTimelinePage.ts`
- `src/main/webapp/ui/src/modules/booking/pages/calendar/BookingEventsCalendar.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/calendar/__tests__/CalendarPage.test.tsx`
- `src/main/webapp/ui/src/modules/booking/pages/calendar/pageObjects/CalendarPage.ts`
- `src/main/webapp/ui/src/__tests__/pageObjects/accessibility.ts`, WebKit's Base UI focus-guard axe exclusion only
- `src/main/webapp/ui/src/modules/common/ui/popover.tsx`
- `src/main/webapp/ui/src/modules/common/i18n/locales/en-US/booking.json`
- `src/main/webapp/ui/src/modules/common/i18n/resources.d.ts`
- `src/main/webapp/ui/src/modules/booking/prototypes/DayTimelineExpandedCardV2.prototype.stories.tsx`, delete after verification
- `plans/README.md`, status update only

### Out of scope

- Backend booking states, endpoints, or authorization
- Unconfirmed and cancelled calendar cards
- Changes to what a busy booking reveals
- Creating blockouts or adding blockout actions
- Duplicate, Delete, Confirm, Decline, Cancel, or a More menu
- The compact card's visual design, lane packing, zoom, now marker, or calendar
  layout controls
- A sheet or other second presentation for narrow windows
- New dependencies
- Changes to unrelated dirty worktree files

### Concurrent work in this worktree

A separate change is in flight on the same branch and already consumes this
plan's output. Do not edit or revert it; it is consistent with the steps below.

- `src/main/webapp/ui/src/modules/booking/prototypes/CompactAddBookingDialogPrototype.stories.tsx`
  (untracked) prototypes the inline add-booking popover. It builds
  `DayTimelineEvent` fixtures and imports `Popover`, `PopoverTrigger`,
  `PopoverContent`, and `PopoverClose`. The concurrent change owns updating its
  fixtures with the Step 1 `title` and `item` fields; do not edit this file in
  this task. It depends on the Step 2 positioner props staying optional. Step 7 deletes
  `DayTimelineExpandedCardV2.prototype.stories.tsx` only; this file belongs to
  another piece of work and must survive.
- `src/main/webapp/ui/.storybook/vite.config.ts` and
  `.storybook/vitest.config.ts` carry an unrelated `optimizeDeps.include`
  rewrite. Expect both in `git status` at the final gate.

That prototype deliberately passes no `collisionBoundary`. Its add form is
several times taller than the roughly 130px row scroller, so that boundary
leaves no room below the slot and Base UI flips the popup out to the side, away
from the slot it describes. The short expanded card this plan ships does not hit
that limit, so keep the scroller boundary in Step 3.

## Git workflow

- Work on the operator's current branch unless instructed otherwise.
- Match the repository's imperative commit style. If asked to commit, use
  `Implement the DayTimeline expanded event card`.
- Do not push or open a pull request unless instructed.

## Steps

### Step 1: Carry display identity in `DayTimelineEvent`

In `DayTimeline.tsx`, add an exported small data type for the item shown by a
full booking or blockout:

```ts
export type DayTimelineItem = {
  name: string;
  globalId: string;
  location?: { name: string; globalId: string };
};
```

Change the full-booking variant to carry:

- `title`: the compact card's visible title and accessible identity;
- `bookedBy`: only the requester label;
- `item`: `DayTimelineItem`;
- the existing `canEdit` and optional `notes`.

Require `item` on blockouts too, because A4 shows the affected item for both
event kinds. Leave the busy variant unchanged.

Update `toTimelineEvent` in `BookingEventsCalendar.tsx`:

- use target name plus booker for `title`, omitting the separator if the API's
  nullable `bookedBy` is unexpectedly absent;
- put the requester label alone in `bookedBy`;
- map target name and global ID to `item`;
- add `item.location` only when both parent-container fields are non-null;
- continue to return the privacy-safe minimal shape for `busy`.

Update the unit-test and Storybook fixtures to compile. The full-booking titles
should preserve their current compact-card text. Give blockout fixtures a real
item rather than weakening the new field to optional.

**Verify**:

```bash
pnpm tsc
```

Expected: exit 0. No production caller constructs an incomplete full booking or
blockout.

### Step 2: Extend the existing popover wrapper

In `modules/common/ui/popover.tsx`, deepen the existing wrapper rather than
adding another wrapper or importing Base UI from booking code.

1. Add optional `collisionBoundary`, `collisionPadding`, and `sticky` props to
   `PopoverContent`. Type them from `PopoverPrimitive.Positioner.Props` and pass
   them only to `PopoverPrimitive.Positioner`.
2. Add an optional `showArrow?: boolean` to `PopoverContent`. When true, render
   and style `PopoverPrimitive.Arrow` so it uses the same background and border
   as the popup.
3. Export a small `PopoverClose` wrapper over `PopoverPrimitive.Close` so the A4
   X control gets Base UI's focus restoration.
4. Keep every new prop optional and preserve current defaults for all existing
   popover consumers.

Do not expose Portal, Positioner, or Popup as separate exports. The wrapper's
interface should express the behavior callers vary, while hiding the Base UI
assembly.

**Verify**:

```bash
pnpm tsc && pnpm lint
```

Expected: exit 0. Existing popover callers need no edits.

### Step 3: Implement the A4 expanded body behind the existing card interface

Keep `DayTimelineEventCard` as the exported module interface. Add unexported
helpers such as `ExpandedEventCard` and `DurationLabel` inside
`DayTimeline.tsx`; do not create one-file pass-through modules.

Add a required `date: string` prop to `DayTimelineEventCard` and pass it from
both `DayTimeline` and `BookingEventsCalendar.EventCard`. Add
`collisionBoundary?: HTMLElement | null` for positioning. Format the plain date
with the active i18n locale and `timeZone: "UTC"`, matching the existing day
heading. Compute duration from `endMinute - startMinute`; format hours and
minutes with ICU messages in the booking catalog. Do not add a date library.

For full bookings and blockouts:

1. Wrap the collapsed card and popup in controlled `<Popover modal={false}>`.
   The existing disclosure button becomes `PopoverTrigger`. Keep its current
   `aria-expanded`, `aria-controls`, and translated accessible name. Handle
   `onOpenChange` inside `DayTimelineEventCard`. When Base UI asks to close for
   `outside-press` or `focus-out`, call `eventDetails.cancel()` and leave the
   card open. Pass every other change to the existing `setExpanded` callback.
   This preserves the current disclosure behavior without adding parent state.
2. Render `PopoverContent` with `align="start"`, `side="bottom"`,
   `sideOffset={8}`, `collisionPadding={0}`, `sticky`, `showArrow`, and the
   supplied collision boundary.
3. Set the popup width to `min(22rem, var(--available-width))`. Use `p-0`, no
   internal gap, overflow hidden, and the A4 border/ring treatment.
4. Build the header with exact period, formatted date, duration, and a
   `PopoverClose` X button. Its label remains the existing translated Hide
   details action. Blockouts add a Wrench eyebrow containing `event.title`.
5. Build a `<dl>`. Its first `<dt>` is screen-reader-only Item; the `<dd>` is an
   unchanged `InventoryItem` with `idPlacement="title"`, zero padding, a
   global-ID link to `/globalId/<id>`, and `InventoryLocationLink` when a
   location exists. Keep its existing single-line name truncation. Do not add a
   name-clamp prop, alter its styles, or reach into its descendants with CSS
   selectors. Follow it with Booked by and Purpose for full bookings. Blockouts
   omit Booked by and label their notes as Notes.
6. Render the existing `renderEventActions` result after the definition list
   only for bookings, as today. Do not call it for blockouts.
7. Keep the compact article, out-of-day edge marks, and busy-booking expanded
   content working as they do now. Busy must not render the A4 item, booker, or
   purpose fields.

Use semantic translation keys under `dayTimeline.expanded`. Author temporary
English with `defaultValue`. Add `item`, `bookedBy`, `purpose`, `notes`, and
`openItem` labels. Add one ICU `duration` message that accepts both `hours` and
`minutes`; do not construct its visible text by joining separately translated
fragments. Then run:

```bash
pnpm run i18n:extract --sync-primary
pnpm run i18n:types
pnpm run i18n:lint
```

Review the catalog diff, remove every `defaultValue`, then run `pnpm tsc`.
Never use `--sync-all`.

**Verify**:

```bash
pnpm test src/modules/booking/components/DayTimeline.test.tsx
pnpm tsc
pnpm run i18n:check
```

Expected: the focused tests pass, TypeScript reports no errors, and the catalog
check makes no changes.

### Step 4: Make popup state independent and remove obsolete positioning

In `DayTimeline`, delete `expandedEventId`. Do not replace it with another
collection. Stop passing `expanded` and `onExpandedChange` to timeline cards so
each keyed `DayTimelineEventCard` uses its existing internal state.

Obtain the scroller element with a callback ref that also preserves the
existing imperative scroll ref. Pass that element as each card's collision
boundary.

Because Base UI now positions the popup:

- remove `alignExpandedEnd` from `DayTimelineEventCard`;
- remove its use at `DayTimeline.tsx:407`;
- remove `alignExpandedEnd` from `BookingEventsCalendar.EventCard` and all its
  call sites;
- stop deriving an `expanded` boolean in the event map;
- give every timeline `<li>` its normal closed-card z-index. The portalled
  content has its own z-index.

Opening a second event must leave the first open. Closing through one card's X
must leave its siblings open and return focus to that card's disclosure trigger.
Let Base UI do the focus work. Do not query the DOM, write a focus effect, or
manage which overlapping popup is on top.

**Verify**:

```bash
pnpm test src/modules/booking/components/DayTimeline.test.tsx src/modules/booking/pages/calendar/__tests__/CalendarPage.test.tsx
```

Expected: both files pass, and unit tests prove two cards can remain open and
closing either one does not close the other.

### Step 5: Present the real actions as A4 cells

Change only the markup in `BookingEventsCalendar.BookingActions`:

- render a divided grid with one track per visible link;
- keep View details for every calendar event;
- keep Edit only when `event.canEdit`;
- make one remaining action fill the full row;
- use the current routes, params, search values, labels, and link semantics.

Do not move permission logic into `DayTimelineEventCard`. Do not add an action
descriptor type or More menu.

Extend `CalendarPage.test.tsx` to assert that an editable full booking has both
links in the expanded popup and a read-only or busy booking has no Edit link.

**Verify**:

```bash
pnpm test src/modules/booking/pages/calendar/__tests__/CalendarPage.test.tsx
```

Expected: the focused calendar tests pass.

### Step 6: Add real-browser geometry and accessibility coverage

Follow the `rspace-browser-tests` skill. Create:

- `DayTimeline.story.tsx`, a provider-wrapped fixture with a long item name,
  location, editable full booking, read-only full booking, blockout, and busy
  booking;
- `pageObjects/DayTimelinePage.ts`, containing semantic locator getters and
  interaction methods only;
- `DayTimeline.spec.tsx`, containing assertions and cleanup.

No MSW handler is needed because this story renders `DayTimeline` directly.
Cover these cases:

1. At a 320px scroller, the open popup's left and right edges stay within the
   scroller's box and its width is no greater than the scroller width.
2. At a 480px or wider scroller, the popup width is approximately 352px.
3. After horizontal scrolling moves the trigger out of view, the sticky popup
   remains within the collision boundary.
4. The global-ID link shares the title row with the item name, the location link
   remains visible in the description row, and a long name stays contained
   using the existing single-line truncation.
5. Open two nearby full bookings. Both popups remain visible and their bounding
   rectangles overlap; opening or interacting with the second does not dismiss
   the first.
6. X closes only its popup, focus returns to that event's disclosure trigger,
   and the other popup remains open.
7. An outside press away from all event cards does not close an expanded card.
8. A full booking exposes item, booker, purpose, and actions. A blockout exposes
   item and notes but no booker or action row. Busy exposes no private fields.
9. `expectNoAxeViolations()` passes with two overlapping non-modal popups open.
   The rest of the timeline must not gain `aria-hidden`.

Use `expect.poll` for bounding-box and scroll assertions. Do not use fixed
sleeps or screenshot assertions.

**Verify**:

```bash
VITEST_BROWSERS=chromium pnpm test-browser src/modules/booking/components/DayTimeline.spec.tsx
pnpm test-browser src/modules/booking/components/DayTimeline.spec.tsx
```

Expected: the file passes first in Chromium, then in Chromium, Firefox, and
WebKit.

### Step 7: Remove the prototype and run the final gates

Delete
`src/main/webapp/ui/src/modules/booking/prototypes/DayTimelineExpandedCardV2.prototype.stories.tsx`.
Do not copy prototype-only stress actions or unconfirmed states into production.

Run the full focused gate:

```bash
pnpm test src/modules/booking/components/DayTimeline.test.tsx src/modules/booking/pages/calendar/__tests__/CalendarPage.test.tsx
pnpm test-browser src/modules/booking/components/DayTimeline.spec.tsx
pnpm run i18n:lint
pnpm run i18n:check
pnpm tsc
pnpm lint
```

Expected: every command exits 0. `git status --short` lists only in-scope files
plus unrelated changes that were already present before execution.

## Test plan summary

- jsdom unit tests protect the event union, information hierarchy, privacy
  branches, action gating, independent open state, focus behavior, and blockout
  rules.
- Calendar page tests protect the adapter from `BookingListDocument` and the
  real action routes.
- Vitest Browser Mode protects popover collision behavior, sticky positioning,
  overlapping open cards, persistent disclosure behavior, title-row ID
  placement, existing item-name truncation, focus restoration, and
  accessibility in Chromium, Firefox, and WebKit.

## Done criteria

All must hold:

- [x] A full booking expands to the A4 header, item, Booked by, Purpose, and
  caller-owned action row.
- [x] A blockout has Wrench plus its title, item, Notes, no Booked by, and no
  actions.
- [x] A busy booking reveals no booker or purpose.
- [x] The popup stays inside a 320px timeline scroller and uses 22rem when room
  permits.
- [x] The item name stays on one truncated line, the global ID shares its title
  row, and the location remains visible in the description row.
- [x] At least two event cards can stay open and overlap without either closing
  when the user opens or interacts with the other.
- [x] Closing one card leaves other open cards unchanged and returns focus to
  the closed card's trigger.
- [x] Outside press and focus-out do not dismiss an expanded event card.
- [x] `InventoryItem` remains unchanged, and booking code does not target its
  descendants with CSS selectors.
- [x] No raw user-facing English was added outside the booking catalog.
- [x] The A4 prototype file is deleted.
- [x] Focused unit and three-engine browser tests pass.
- [x] `pnpm run i18n:lint`, `pnpm run i18n:check`, `pnpm tsc`, and `pnpm lint`
  all exit 0.
- [x] `plans/README.md` marks plan 002 `DONE` only after every preceding item is
  true.

## STOP conditions

Stop and report instead of expanding scope if:

- an in-scope source file drifted from the interfaces described above;
- Base UI cannot keep the popup within the scroller in any supported engine;
- `modal={false}` still adds `aria-hidden` to the timeline;
- extending `PopoverContent` changes an existing popover's layout or behavior;
- Base UI does not honor `eventDetails.cancel()` for `outside-press` or
  `focus-out` in a supported browser engine;
- target or parent-location identity is absent from the calendar API response;
- A4 requires exposing booker or purpose data for a `busy` event;
- the work appears to require backend changes, new dependencies, or an
  out-of-scope file;
- a verification command fails twice after one reasonable correction.

## Maintenance notes

- Reviewers should inspect privacy first. The richer card must branch on the
  timeline event's privacy variant, not on whether nullable text happens to be
  present.
- `renderEventActions` remains the seam for routes and authorization-shaped
  affordances. Add an action descriptor type only if a second caller needs the
  card to lay out structurally different action sets.
- Expanded cards intentionally remain open until their trigger, X control, or
  Escape closes them. Keep state inside each card. A shared set of IDs or a
  last-open z-index stack is unnecessary while visual overlap is acceptable.
- Add a More menu only when a real third action ships and the flat row becomes
  crowded. Use the existing menu primitive then.
- Unconfirmed bookings need a backend state, transitions, and authorization.
  Their prototype styling is not implementation-ready on its own.
- If blockout creation later reaches the calendar API, its adapter must provide
  the required `DayTimelineItem`; do not make the item optional to avoid doing
  that mapping.
