# Plan 001: Implement booking details and configuration pages

## Status

- Priority: P1
- Effort: L
- Risk: HIGH
- Depends on: none
- Category: direction
- Planned at: commit `8c41cad80`, 2026-09-08

## Why this matters

The repository currently has prototype-only detail-page studies, while the production booking experience is split between booking-event and bookable-item pages. This plan turns the validated prototype structure into two maintainable product pages without losing the reordered booking facts or the resource-access workflow.

## Current state

- `src/main/webapp/ui/src/modules/booking/pages/bookings/BookingEventPage.tsx` owns the existing booking details route and edit flow.
- `src/main/webapp/ui/src/modules/booking/pages/bookable-items/BookableItemPage.tsx` owns configuration tabs and mounts the production `ResourceAccessEditor`.
- `src/main/webapp/ui/src/modules/booking/prototypes/BookingDetailPagesPrototype.stories.tsx` contains the two prototype stories, reordered Reserved time, configuration tabs, and stress scenarios.
- `src/main/webapp/ui/src/modules/booking/prototypes/ResourceAccessPrototypeEditor.tsx` contains extracted in-memory access editor state, identity rows, role menus, responsive rendering, and save/cancel footer.
- Existing test exemplars: `BookingEventPage.test.tsx`, `BookableItemPage.test.tsx`, and `ResourceAccessEditor.test.tsx`.

## Commands

| Purpose | Command | Expected |
|---|---|---|
| Typecheck | `pnpm tsc` | exit 0; task-caused errors absent |
| Unit tests | `pnpm test src/modules/booking/pages/bookings/BookingEventPage.test.tsx src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx src/modules/common/resource-access/__tests__/ResourceAccessEditor.test.tsx` | all pass |
| Story tests | `pnpm test-storybook src/modules/booking/prototypes/BookingDetailPagesPrototype.stories.tsx` | all pass |
| Lint | `pnpm lint` | exit 0 |
| Browser | Playwright MCP against Storybook and the production routes | no console errors; desktop and 320px flows pass |

## Scope

In scope: the two booking page components, their route/page-object tests, shared booking detail presentation components, i18n keys required by the pages, and focused fixtures/MSW handlers.

Out of scope: backend API shape changes, production database changes, replacing the shared ResourceAccessEditor, and unrelated translation errors.

## Steps

### Step 1: Agree the page contract

Use separate routes in the existing route families. Follow each caller's existing read/write access: preserve current backend capability checks, expose read-only views where read access exists, and expose editing only where existing write access permits it. Define loading/error states, edit persistence, and the exact tabs before implementation.

**Verify:** review checklist has explicit answers for every unresolved question below.

### Step 2: Extract shared presentation

Create a production shared detail shell using the existing MUI/Tailwind conventions. Preserve the prototype’s header, status/edit alignment, reordered Reserved time with inline duration, People & location placement, responsive spacing, and unsaved-warning treatment. Do not copy story-only fixture logic into production components.

**Verify:** focused component tests render both pages and assert the key headings, facts, edit controls, and responsive-safe structure.

### Step 3: Implement booking details

Adapt `BookingEventPage.tsx` to the shared shell. Keep booking validation and timezone formatting in existing domain helpers. Ensure edit/save/discard and invalid-period states remain accessible.

**Verify:** `pnpm test src/modules/booking/pages/bookings/BookingEventPage.test.tsx` passes, including save/discard and invalid input cases.

### Step 4: Implement booking configuration

Adapt `BookableItemPage.tsx` to the shared shell and current tabbed UI. Place Opening hours and Maximum duration under Booking rules. Keep Access as the production `ResourceAccessEditor` with booking adapter, and keep Audit log behavior unchanged. Derive read/write visibility from the existing caller capabilities rather than adding a new permission model.

**Verify:** `pnpm test src/modules/booking/pages/bookable-items/__tests__/BookableItemPage.test.tsx src/modules/common/resource-access/__tests__/ResourceAccessEditor.test.tsx` passes.

### Step 5: Verify visually and end to end

Use Playwright MCP at desktop and 320px. Exercise view/edit/save/discard, tab navigation, access search, role changes, add/remove/restore, and configuration changes. Capture screenshots before and after interactions; inspect console and network requests for unexpected failures or duplicate calls.

**Verify:** no horizontal overflow at 320px, no new console errors, expected request counts, and no unexplained layout differences from the approved prototype.

## Resolved decisions

1. Booking details and configuration use separate routes within the existing route families.
2. Read-only and write controls follow each user's existing backend read/write access. Do not introduce a parallel permission model; hide or disable controls according to the capabilities already returned by the page APIs.
3. Save/Discard drafts use the existing dirty-navigation guard and last only for the current editing session; reload discards them.
4. “Booked by” displays the actual requester or delegated principal; “Created by” remains the audit creator.
5. Overnight and DST-crossing bookings are valid when accepted by existing zoned-time helpers and remain subject to the configured maximum duration.
6. Configuration opens on Details by default; an explicit URL tab parameter may select Access or Audit log.
7. Audit log remains a tab at every viewport width.
8. Stale access saves preserve the local draft, show server changes, and offer “Review latest and retry,” matching ResourceAccessEditor.

## Done criteria

- [ ] Both production pages use the shared detail shell and current tabbed UI.
- [ ] Reserved time, inline duration, and People & location match the approved prototype.
- [ ] Configuration Access uses the shared production ResourceAccessEditor and booking adapter.
- [ ] All unresolved questions have explicit product decisions.
- [ ] Focused unit, Storybook, TypeScript, lint, and Playwright checks pass.
- [ ] No files outside scope are modified.

## STOP conditions

- Stop if route ownership or permission policy is undecided.
- Stop if implementing the page requires changing backend contracts or database schema.
- Stop if DST/overnight validity cannot be reconciled with existing domain helpers.
- Stop after two failed verification attempts for the same issue and report the evidence.
