import { render, screen } from "@testing-library/react";
import { describe, expect, test, vi } from "vitest";
import RequestsSearchbar from "../RequestsSearchbar";

describe("RequestsSearchbar", () => {
  test("the search icon is decorative, not a dead focusable button with no action", () => {
    render(<RequestsSearchbar value="" onChange={vi.fn()} />);

    // The bug this guards against: an IconButton with no onClick in the start adornment, which
    // a keyboard user could Tab to but which did nothing when activated.
    expect(
      screen.queryByRole("button", { name: "inventory:search.controls.searchbar.search" }),
    ).not.toBeInTheDocument();
  });

  test("still shows a working Clear button once there is a query to clear", () => {
    const onChange = vi.fn();
    render(<RequestsSearchbar value="some query" onChange={onChange} />);

    expect(screen.getByRole("button", { name: "inventory:search.controls.searchbar.clearSearch" })).toBeInTheDocument();
  });
});
