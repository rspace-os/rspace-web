/*
 * @vitest-environment jsdom
 */
import { describe, expect, test, vi } from "vitest";
import React from "react";
import { screen } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { render } from "@/__tests__/customQueries";
import PermalinkNotFound from "../PermalinkNotFound";
import NavigateContext from "../../../stores/contexts/Navigate";

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
    renderWithNavigation(
      <PermalinkNotFound
        permalink={{ type: "subsample", id: 9, version: 2 }}
      />,
    );

    expect(screen.getByText(/version 2 of this subsample/i)).toBeVisible();
    const link = screen.getByRole("link", { name: /latest version/i });
    expect(link).toHaveAttribute("href", "/inventory/subsample/9");
  });

  test("an unversioned permalink shows the generic not-found message", () => {
    renderWithNavigation(
      <PermalinkNotFound
        permalink={{ type: "container", id: 7, version: null }}
      />,
    );

    expect(
      screen.getByText(/this container could not be found/i),
    ).toBeVisible();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  test("sample template permalinks are labelled with a readable type name", () => {
    renderWithNavigation(
      <PermalinkNotFound
        permalink={{ type: "sampletemplate", id: 3, version: 4 }}
      />,
    );

    expect(
      screen.getByText(/version 4 of this sample template/i),
    ).toBeVisible();
  });

  test("the panel is accessible", async () => {
    const { baseElement } = renderWithNavigation(
      <PermalinkNotFound
        permalink={{ type: "subsample", id: 9, version: 2 }}
      />,
    );

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible(); // eslint-disable-line @typescript-eslint/no-unsafe-call
  });
});
