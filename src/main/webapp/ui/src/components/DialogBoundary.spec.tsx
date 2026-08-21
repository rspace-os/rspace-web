/*
 * PRT-1118 and PRT-1135 cover mui/material-ui#32286, fixed in MUI 9.3.1 by PR #48881.
 * A Modal could remain mounted after its exit transition. This left an invisible backdrop
 * intercepting clicks (PRT-1118) and leaked `aria-hidden` attributes (PRT-1135).
 *
 * The upstream issue identifies two triggers:
 *
 *   A. The Modal portal container changes during an exit.
 *   B. React reconnects effects across a Suspense boundary during an exit and rearms an obsolete
 *      transition timer.
 *
 * RSpace encountered the second trigger. With `useSuspense: true`, i18next can suspend while it
 * loads a namespace during the Gallery Create menu exit.
 *
 * The tests extend the exit to 3 seconds to reproduce both triggers. On MUI 9.2.0, test B leaves
 * one modal root and two `aria-hidden` nodes. Both tests pass on 9.3.1. The assertions count DOM
 * nodes because role queries cannot match an `aria-hidden` Modal.
 */
import Button from "@mui/material/Button";
import DialogContent from "@mui/material/DialogContent";
import MenuItem from "@mui/material/MenuItem";
import { Suspense } from "@suspensive/react";
import { cleanup, render } from "@testing-library/react";
import React from "react";
import { afterEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { Dialog, DialogBoundary, Menu } from "./DialogBoundary";

/** Allows the perturbation to run before the exit completes. */
const EXIT_MS = 3000;
const PERTURB_AT_MS = 150;
/* Held as constants rather than inline JSX text to satisfy noJsxLiterals. */
const CREATE_LABEL = "Create";
const IMPORT_LABEL = "Import";
const FALLBACK_LABEL = "loading";
const OUTER_LABEL = "outer modal";
const INNER_LABEL = "inner modal";
/* Proves that cleanup restores the prior value instead of clearing it. */
const SENTINEL_OVERFLOW = "clip";

afterEach(cleanup);

/** Simulates a lazily loaded i18n namespace. */
function makeSuspender() {
  const { promise, resolve: release } = Promise.withResolvers<void>();

  return {
    release,
    Suspender: ({ armed }: { armed: boolean }) => {
      if (armed) React.use(promise);
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
             * Simulates Gallery state updates and the allIntegrations query settling during
             * the exit.
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

function SuspenseHarness({ suspender }: { suspender: ReturnType<typeof makeSuspender> }): React.ReactNode {
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const [armed, setArmed] = React.useState(false);
  const { Suspender } = suspender;
  return (
    <Suspense fallback={<div>{FALLBACK_LABEL}</div>}>
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
              /* Simulates useTranslation suspending for an unloaded namespace during the exit. */
              setTimeout(() => setArmed(true), PERTURB_AT_MS);
            }}
          >
            {IMPORT_LABEL}
          </MenuItem>
        </Menu>
      </DialogBoundary>
    </Suspense>
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

/*
 * Nested overlays share a reference-counted body lock. The final cleanup restores the previous
 * overflow value, including when an open overlay unmounts.
 */
describe("the body scroll lock", () => {
  function Nested({ outer, inner }: { outer: boolean; inner: boolean }): React.ReactNode {
    return (
      <DialogBoundary>
        <Dialog open={outer}>
          <DialogContent>{OUTER_LABEL}</DialogContent>
        </Dialog>
        <Dialog open={inner}>
          <DialogContent>{INNER_LABEL}</DialogContent>
        </Dialog>
      </DialogBoundary>
    );
  }

  test("stays locked while an outer modal is still open, then restores", async () => {
    document.body.style.overflow = SENTINEL_OVERFLOW;

    const screen = render(<Nested outer={true} inner={true} />);
    await expect.element(page.getByText(INNER_LABEL)).toBeVisible();
    expect(document.body.style.overflow).toBe("hidden");

    screen.rerender(<Nested outer={true} inner={false} />);
    await expect.poll(() => document.body.style.overflow).toBe("hidden");

    screen.rerender(<Nested outer={false} inner={false} />);
    await expect.poll(() => document.body.style.overflow).toBe(SENTINEL_OVERFLOW);

    document.body.style.overflow = "";
  });

  test("releases the lock when a modal unmounts while still open", async () => {
    document.body.style.overflow = SENTINEL_OVERFLOW;

    const screen = render(<Nested outer={true} inner={false} />);
    await expect.element(page.getByText(OUTER_LABEL)).toBeVisible();
    expect(document.body.style.overflow).toBe("hidden");

    screen.unmount();
    await expect.poll(() => document.body.style.overflow).toBe(SENTINEL_OVERFLOW);

    document.body.style.overflow = "";
  });
});
