/*
 * @vitest-environment jsdom
 */

import { screen } from "@testing-library/react";
import type React from "react";
import { describe, expect, test, vi } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import inventoryEn from "@/modules/common/i18n/locales/en-US/inventory.json";
import NavigateContext from "../../../stores/contexts/Navigate";
import PermalinkNotFound from "../PermalinkNotFound";

const navigate = vi.fn();

function renderWithNavigation(ui: React.ReactElement) {
  return renderWithRealI18n(
    <NavigateContext.Provider
      value={{
        useNavigate: () => navigate,
        useLocation: vi.fn(),
      }}
    >
      {ui}
    </NavigateContext.Provider>,
    { resources: { inventory: inventoryEn }, defaultNS: "inventory" },
  );
}

describe("PermalinkNotFound", () => {
  test("a versioned permalink names the missing version and links to the latest", async () => {
    await renderWithNavigation(<PermalinkNotFound permalink={{ type: "subsample", id: 9, version: 2 }} />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Version 2 of this subsample could not be found.");
    expect(alert).toHaveTextContent("The version may never have existed. View the latest version.");
    const link = screen.getByRole("link", { name: "View the latest version" });
    expect(link).toHaveAttribute("href", "/inventory/subsample/9");
  });

  test("an unversioned permalink shows the generic not-found message", async () => {
    await renderWithNavigation(<PermalinkNotFound permalink={{ type: "container", id: 7, version: null }} />);

    expect(screen.getByText("This container could not be found.")).toBeVisible();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  test("sample template permalinks are labelled with a readable type name", async () => {
    await renderWithNavigation(<PermalinkNotFound permalink={{ type: "sampletemplate", id: 3, version: 4 }} />);

    expect(screen.getByText("Version 4 of this sample template could not be found.")).toBeVisible();
  });

  test("the panel is accessible", async () => {
    const { baseElement } = await renderWithNavigation(
      <PermalinkNotFound permalink={{ type: "subsample", id: 9, version: 2 }} />,
    );

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    await expect(baseElement).toBeAccessible(); // eslint-disable-line @typescript-eslint/no-unsafe-call
  });
});
