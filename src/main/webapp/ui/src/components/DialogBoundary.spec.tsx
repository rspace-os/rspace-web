/*
 * PRT-1118 / PRT-1135. A modal rendered through DialogBoundary could finish its
 * exit transition without ever unmounting: the Modal root stayed in the DOM and
 * ModalManager never restored the `aria-hidden` it had put on the rest of the
 * page. Symptoms were a menu that stayed visually open with the page unreachable
 * by role (PRT-1135) and an invisible backdrop eating every click (PRT-1118).
 *
 * Upstream cause is mui/material-ui#32286, fixed in @mui/material 9.3.1 by
 * PR #48881, which names two triggers:
 *
 *   A. the Modal's portal container changing during an exit, and
 *   B. React reconnecting effects across a Suspense boundary during an exit,
 *      rearming the transition's completion timer for a superseded phase.
 *
 * B is the one that bites RSpace: i18next runs with `useSuspense: true` and
 * lazily loaded namespaces, so any `useTranslation` for a not-yet-loaded
 * namespace can suspend while a menu is mid-exit. That is why the Gallery create
 * menu got stuck when a DMP import dialog closed.
 *
 * These tests force each trigger rather than waiting for the intermittent race:
 * the exit window is widened to 3s so the perturbation lands inside it. On
 * @mui/material 9.2.0 test B fails deterministically (1 surviving modal root,
 * 2 leaked aria-hidden nodes); on 9.3.1 both pass.
 *
 * Assertions are positive ("the page is reachable", "no modal root survives")
 * rather than "the menu is hidden": an aria-hidden menu root cannot be matched
 * by role at all, so a hidden/absent assertion would pass *because* the bug is
 * present.
 */
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import { cleanup, render } from "@testing-library/react";
import React from "react";
import { afterEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { DialogBoundary, Menu } from "./DialogBoundary";

/** Wide enough that the perturbation reliably lands inside the exit. */
const EXIT_MS = 3000;
const PERTURB_AT_MS = 150;
/* Held as constants rather than inline JSX text to satisfy noJsxLiterals. */
const CREATE_LABEL = "Create";
const IMPORT_LABEL = "Import";
const FALLBACK_LABEL = "loading";

afterEach(cleanup);

/** Suspends on demand, standing in for a lazily loaded i18n namespace. */
function makeSuspender() {
  let resolve = (): void => {};
  let settled = false;
  let promise: Promise<void> | null = null;
  return {
    release: () => resolve(),
    Suspender: ({ armed }: { armed: boolean }) => {
      if (armed && !settled) {
        promise ??= new Promise<void>((r) => {
          resolve = () => {
            settled = true;
            r();
          };
        });
        throw promise;
      }
      return null;
    },
  };
}

function survivingModalRoots(): number {
  return document.querySelectorAll(".MuiModal-root").length;
}

function leakedAriaHidden(): number {
  return document.querySelectorAll('[aria-hidden="true"]').length;
}

/** A parent re-render lands during the menu's exit. */
function ReRenderHarness(): React.ReactNode {
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const [, setTick] = React.useState(0);
  return (
    <DialogBoundary>
      <Button onClick={(e) => setAnchorEl(e.currentTarget)}>{CREATE_LABEL}</Button>
      <Menu
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        transitionDuration={{ exit: EXIT_MS }}
      >
        <MenuItem
          onClick={() => {
            setAnchorEl(null);
            /*
             * Stands in for the Gallery Sidebar's own state updates
             * (setNewMenuAnchorEl / setSelectedSection) and the
             * allIntegrations query settling, on a deterministic timer.
             */
            setTimeout(() => setTick((t) => t + 1), PERTURB_AT_MS);
          }}
        >
          {IMPORT_LABEL}
        </MenuItem>
      </Menu>
    </DialogBoundary>
  );
}

/** A Suspense boundary above the Menu suspends during the menu's exit. */
function SuspenseHarness({ suspender }: { suspender: ReturnType<typeof makeSuspender> }): React.ReactNode {
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const [armed, setArmed] = React.useState(false);
  const { Suspender } = suspender;
  return (
    <React.Suspense fallback={<div>{FALLBACK_LABEL}</div>}>
      <Suspender armed={armed} />
      <DialogBoundary>
        <Button onClick={(e) => setAnchorEl(e.currentTarget)}>{CREATE_LABEL}</Button>
        <Menu
          open={Boolean(anchorEl)}
          anchorEl={anchorEl}
          onClose={() => setAnchorEl(null)}
          transitionDuration={{ exit: EXIT_MS }}
        >
          <MenuItem
            onClick={() => {
              setAnchorEl(null);
              /*
               * Stands in for useTranslation(["apps", "common"]) hitting a
               * namespace that has not loaded yet while the menu is exiting.
               */
              setTimeout(() => setArmed(true), PERTURB_AT_MS);
            }}
          >
            {IMPORT_LABEL}
          </MenuItem>
        </Menu>
      </DialogBoundary>
    </React.Suspense>
  );
}

async function openMenuAndDismiss(): Promise<void> {
  await page.getByRole("button", { name: CREATE_LABEL }).click();
  await expect.element(page.getByRole("menuitem", { name: IMPORT_LABEL })).toBeVisible();
  await page.getByRole("menuitem", { name: IMPORT_LABEL }).click();
}

async function expectNothingStranded(): Promise<void> {
  await expect.element(page.getByRole("button", { name: CREATE_LABEL })).toBeInTheDocument();
  expect(survivingModalRoots()).toBe(0);
  expect(leakedAriaHidden()).toBe(0);
}

describe("a modal rendered through DialogBoundary always unmounts after its exit", () => {
  test("when a parent re-render lands during the exit", async () => {
    render(<ReRenderHarness />);
    await openMenuAndDismiss();

    await new Promise((r) => setTimeout(r, EXIT_MS + 1500));

    await expectNothingStranded();
  });

  test("when a Suspense boundary suspends and resumes during the exit", async () => {
    const suspender = makeSuspender();
    render(<SuspenseHarness suspender={suspender} />);
    await openMenuAndDismiss();

    await new Promise((r) => setTimeout(r, PERTURB_AT_MS + 250));
    suspender.release();
    await new Promise((r) => setTimeout(r, EXIT_MS + 1500));

    await expectNothingStranded();
  });
});
