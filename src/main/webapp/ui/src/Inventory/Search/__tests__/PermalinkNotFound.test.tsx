/*
 * @vitest-environment jsdom
 */

import { screen } from "@testing-library/react";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { render } from "@/__tests__/customQueries";
import NavigateContext from "../../../stores/contexts/Navigate";
import PermalinkNotFound from "../PermalinkNotFound";

const navigate = vi.fn();

function renderWithNavigation(ui: React.ReactElement) {
  return render(
    <NavigateContext.Provider
      value={{
        useNavigate: () => navigate,
        useLocation: vi.fn(),
      }}
    >
      {ui}
    </NavigateContext.Provider>,
  );
}

describe("PermalinkNotFound", () => {
  test("a versioned permalink names the missing version and links to the latest", () => {
    renderWithNavigation(<PermalinkNotFound permalink={{ type: "subsample", id: 9, version: 2 }} />);

    expect(screen.getByText("permalinkNotFound.versionedTitle")).toBeVisible();
    const link = screen.getByRole("link", { name: "permalinkNotFound.latestLink" });
    expect(link).toHaveAttribute("href", "/inventory/subsample/9");
  });

  test("an unversioned permalink shows the generic not-found message", () => {
    renderWithNavigation(<PermalinkNotFound permalink={{ type: "container", id: 7, version: null }} />);

    expect(screen.getByText("permalinkNotFound.unversionedTitle")).toBeVisible();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  test("sample template permalinks are labelled with a readable type name", () => {
    renderWithNavigation(<PermalinkNotFound permalink={{ type: "sampletemplate", id: 3, version: 4 }} />);

    expect(screen.getByText("permalinkNotFound.versionedTitle")).toBeVisible();
  });

  test("the panel is accessible", async () => {
    const { baseElement } = renderWithNavigation(
      <PermalinkNotFound permalink={{ type: "subsample", id: 9, version: 2 }} />,
    );

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible(); // eslint-disable-line @typescript-eslint/no-unsafe-call
  });
});
