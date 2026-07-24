import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserSetup";
import { galleryAppShellHandlers } from "@/__tests__/mocks/galleryMocks";
import { DefaultSidebar } from "./Sidebar.story";

/*
 * PRT-1118. With a DMP integration enabled, dismissing the Gallery create menu
 * froze the page: a re-render landing while the menu was mid-exit cancelled
 * react-transition-group's `onExited` (mui/material-ui#32286), so the menu's
 * Modal never unmounted and its invisible (opacity-0) backdrop kept intercepting
 * every click. The fix (Sidebar.tsx) makes the closed menu click-through.
 *
 * The freeze only manifests under a production React build (`-DgenerateReactDist`,
 * not `-DreactDevMode`, which StrictMode-masks it) and the underlying race is too
 * timing-dependent to reproduce deterministically here, so there is no automated
 * test for the frozen state. This spec guards the deterministic half in the normal
 * browser suite: the OPEN menu must stay interactive (the pointer-events condition
 * must never be inverted onto the open state).
 */

function integrationInfoHandlers() {
  const info = (name: string, o: { available?: boolean; enabled?: boolean } = {}) => ({
    data: {
      name,
      displayName: name,
      available: o.available ?? false,
      enabled: o.enabled ?? false,
      oauthConnected: false,
      options: {},
    },
    error: null,
    success: true,
    errorMsg: null,
  });
  return [
    http.get("/integration/integrationInfo", ({ request }) => {
      const name = new URL(request.url).searchParams.get("name") ?? "";
      return HttpResponse.json(name === "DMPTOOL" ? info("DMPTOOL", { available: true, enabled: true }) : info(name));
    }),
    http.get("/integration/allIntegrations", () =>
      HttpResponse.json({ success: true, data: { DSW: { options: {} } }, error: null }),
    ),
    http.get("/apps/dmptool/baseUrlHost", () => HttpResponse.text("example.com")),
    http.get("/apps/dmptool/plans*", () => HttpResponse.json({ success: true, data: { items: [] } })),
    http.get("/api/v1/gallery/filesystems", () => HttpResponse.json([])),
  ];
}

describe("Gallery create menu (DMP enabled)", () => {
  beforeEach(() => {
    worker.use(...galleryAppShellHandlers(), ...integrationInfoHandlers());
  });
  afterEach(() => cleanup());

  test("the open menu and its DMP option are interactive (pointer-events not disabled)", async () => {
    await page.viewport(1440, 900);
    render(<DefaultSidebar />);

    await page.getByRole("button", { name: "Create" }).click();
    const dmptool = page.getByRole("menuitem", { name: /dmptool/i });
    await expect.element(dmptool).toBeVisible();

    // The fix disables pointer-events on the CLOSED menu; guard that it is never
    // (e.g. via an inverted condition) applied to the OPEN menu, which would make
    // every create action dead. Real-browser hit-testing resolves the emotion
    // `sx` rule reliably, unlike jsdom.
    const menu = document.querySelector(".MuiMenu-root") as HTMLElement | null;
    expect(menu).not.toBeNull();
    expect(getComputedStyle(menu as HTMLElement).pointerEvents).not.toBe("none");
    expect(getComputedStyle(dmptool.element() as HTMLElement).pointerEvents).not.toBe("none");
  });
});
