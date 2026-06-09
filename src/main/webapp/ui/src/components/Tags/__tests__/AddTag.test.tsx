import React from "react";
import { describe, expect, test, vi, beforeEach, afterEach } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import AddTag from "../AddTag";

describe("AddTag", () => {
  beforeEach(() => {
    fetchMock.mockResponse(JSON.stringify({ data: [] }));
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    })) as unknown as typeof window.matchMedia;
  });
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  test("opening the popover does not log a missing-input-element error", async () => {
    fetchMock.mockResponse(JSON.stringify({ data: ["alpha", "beta"] }));
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

    render(
      <AddTag enforceOntologies={false} onSelection={() => {}} value={[]} />,
    );

    fireEvent.click(screen.getByText("Add Tag"));

    await waitFor(() => {
      expect(screen.getByLabelText("Filter suggested tags")).toBeVisible();
    });

    // give effects a tick to flush
    await new Promise((r) => setTimeout(r, 50));

    const messages = errorSpy.mock.calls.map((c) =>
      c.map((arg) => String(arg)).join(" "),
    );

    expect(
      messages.filter((m) => m.includes("Unable to find the input element")),
    ).toEqual([]);
    expect(
      messages.filter((m) =>
        m.includes('A props object containing a "key" prop is being spread'),
      ),
    ).toEqual([]);
  });
});
