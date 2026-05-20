import { describe, expect, test, vi } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@/__tests__/customQueries";
import SidebarToggle from "../SidebarToggle";

describe("SidebarToggle", () => {
  test("toggles the sidebar when clicked", async () => {
    const user = userEvent.setup();
    const setSidebarOpen = vi.fn();

    render(
      <SidebarToggle
        sidebarOpen={false}
        sidebarId="sidebar"
        setSidebarOpen={setSidebarOpen}
      />,
    );

    await user.click(screen.getByRole("button", { name: /open sidebar/i }));

    expect(setSidebarOpen).toHaveBeenCalledWith(true);
  });

  test("sets aria-expanded when open", () => {
    render(
      <SidebarToggle
        sidebarOpen={true}
        sidebarId="sidebar"
        setSidebarOpen={() => {}}
      />,
    );

    expect(screen.getByRole("button", { name: /close sidebar/i })).toHaveAttribute(
      "aria-expanded",
      "true",
    );
  });

  test("sets aria-expanded when closed", () => {
    render(
      <SidebarToggle
        sidebarOpen={false}
        sidebarId="sidebar"
        setSidebarOpen={() => {}}
      />,
    );

    expect(screen.getByRole("button", { name: /open sidebar/i })).toHaveAttribute(
      "aria-expanded",
      "false",
    );
  });

  test("applies the sidebar id as aria-controls", () => {
    render(
      <SidebarToggle
        sidebarOpen={false}
        sidebarId="test-sidebar"
        setSidebarOpen={() => {}}
      />,
    );

    expect(screen.getByRole("button", { name: /open sidebar/i })).toHaveAttribute(
      "aria-controls",
      "test-sidebar",
    );
  });

  test("is accessible", async () => {
    const { baseElement } = render(
      <SidebarToggle
        sidebarOpen={false}
        sidebarId="sidebar"
        setSidebarOpen={() => {}}
      />,
    );

    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(baseElement).toBeAccessible();
  });
});
