import React from "react";
import { describe, expect, test } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SidebarToggle from "../SidebarToggle";

function renderSidebarToggle(
  props: Partial<React.ComponentProps<typeof SidebarToggle>> = {},
) {
  let sidebarOpen = props.sidebarOpen ?? true;
  const setSidebarOpen = (newSidebarOpen: boolean) => {
    sidebarOpen = newSidebarOpen;
  };

  const view = render(
    <>
      <header>
        <h1>A simple page</h1>
        <SidebarToggle
          sidebarOpen={sidebarOpen}
          setSidebarOpen={setSidebarOpen}
          sidebarId="sidebar"
          {...props}
        />
      </header>
      <main>
        <div id="sidebar" />
      </main>
    </>,
  );

  return {
    ...view,
    getSidebarOpen: () => sidebarOpen,
  };
}

describe("SidebarToggle", () => {
  test("clicking the button toggles the sidebar state", async () => {
    const user = userEvent.setup();
    const { getSidebarOpen } = renderSidebarToggle({ sidebarOpen: false });

    await user.click(screen.getByRole("button"));

    expect(getSidebarOpen()).toBe(true);
  });

  test("is accessible", async () => {
    const { baseElement } = renderSidebarToggle();
    /* eslint-disable @typescript-eslint/no-unsafe-call */
    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(baseElement).toBeAccessible();
    /* eslint-enable @typescript-eslint/no-unsafe-call */
  });

  test("sets aria-expanded when the sidebar is open", () => {
    renderSidebarToggle({ sidebarOpen: true });
    expect(screen.getByRole("button")).toHaveAttribute("aria-expanded", "true");
  });

  test("sets aria-expanded when the sidebar is closed", () => {
    renderSidebarToggle({ sidebarOpen: false });
    expect(screen.getByRole("button")).toHaveAttribute(
      "aria-expanded",
      "false",
    );
  });

  test("sets aria-controls from the sidebar id", () => {
    renderSidebarToggle({ sidebarId: "test-sidebar" });
    expect(screen.getByRole("button")).toHaveAttribute(
      "aria-controls",
      "test-sidebar",
    );
  });
});
