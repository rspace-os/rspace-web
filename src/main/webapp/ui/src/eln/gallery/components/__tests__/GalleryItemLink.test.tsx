import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import NavigateContext from "../../../../stores/contexts/Navigate";
import { GalleryItemLink, galleryItemHref } from "../GalleryItemLink";

const LABEL = "v1";

function renderLink(navigate: (url: string) => void, onNavigate?: () => void) {
  return render(
    (
      <NavigateContext.Provider
        value={{
          useNavigate: () => navigate,
          useLocation: () => ({ hash: "", pathname: "", search: "", state: {}, key: "" }),
        }}
      >
        <GalleryItemLink href={galleryItemHref("42", 1)} onNavigate={onNavigate}>
          {LABEL}
        </GalleryItemLink>
      </NavigateContext.Provider>
    ) as React.ReactElement,
  );
}

describe("galleryItemHref", () => {
  test("omits the version segment for the live item, which saves a redirect", () => {
    expect(galleryItemHref("42")).toBe("/gallery/item/42");
  });

  test("appends the version segment for a pinned item", () => {
    expect(galleryItemHref("42", 2)).toBe("/gallery/item/42/2");
  });
});

describe("GalleryItemLink", () => {
  test("keeps a real href, so the link can be copied", () => {
    renderLink(() => {});

    expect(screen.getByRole("link", { name: LABEL })).toHaveAttribute("href", "/gallery/item/42/1");
  });

  test("navigates in-app on a plain click rather than reloading the page", async () => {
    const navigate = vi.fn();
    const onNavigate = vi.fn();
    renderLink(navigate, onNavigate);

    await userEvent.click(screen.getByRole("link", { name: LABEL }));

    expect(navigate).toHaveBeenCalledWith("/gallery/item/42/1");
    expect(onNavigate).toHaveBeenCalled();
  });

  test("leaves a modified click to the browser, so open-in-new-tab still works", async () => {
    /*
     * The whole point of carrying a real href is that a user can cmd-click it. Calling
     * preventDefault unconditionally would swallow that and navigate the current tab instead.
     */
    const navigate = vi.fn();
    renderLink(navigate);
    // one setup() instance, so the held modifier is still down when the click is dispatched
    const user = userEvent.setup();

    await user.keyboard("{Meta>}");
    await user.click(screen.getByRole("link", { name: LABEL }));
    await user.keyboard("{/Meta}");

    expect(navigate).not.toHaveBeenCalled();
  });
});
