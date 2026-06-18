import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { expectAccessible, render, screen } from "@/__tests__/customQueries";

import { SimplePageWithSidebarToggle } from "./SidebarToggle.story";

/*
 * Converted from SidebarToggle.spec.tsx (Playwright CT). This component renders
 * a single icon button and only exercises markup/aria/click behaviour, so it
 * gets no value from a real browser and runs as a jsdom unit test instead.
 */
describe("Functional requirements", () => {
  test("Clicking the button should toggle the sidebar.", async () => {
    const user = userEvent.setup();
    const initialSidebarOpen = Math.random() < 0.5;
    const setSidebarOpen = vi.fn();
    render(<SimplePageWithSidebarToggle sidebarOpen={initialSidebarOpen} setSidebarOpen={setSidebarOpen} />);
    await user.click(screen.getByRole("button"));
    expect(setSidebarOpen).toHaveBeenCalledWith(!initialSidebarOpen);
  });
});

describe("Accessibility", () => {
  test("Should have no axe violations.", async () => {
    const { baseElement } = render(<SimplePageWithSidebarToggle />);
    await expectAccessible(baseElement);
  });

  test("When sidebar is open, aria-expanded should be true.", () => {
    render(<SimplePageWithSidebarToggle sidebarOpen={true} />);
    expect(screen.getByRole("button").getAttribute("aria-expanded")).toBe("true");
  });

  test("When sidebar is closed, aria-expanded should be false.", () => {
    render(<SimplePageWithSidebarToggle sidebarOpen={false} />);
    expect(screen.getByRole("button").getAttribute("aria-expanded")).toBe("false");
  });

  test("Applies the sidebarId as the aria-controls attribute.", () => {
    render(<SimplePageWithSidebarToggle sidebarId="test" />);
    expect(screen.getByRole("button").getAttribute("aria-controls")).toBe("test");
  });
});
