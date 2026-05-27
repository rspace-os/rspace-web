import { describe, expect, test } from "vitest";
import React from "react";
import userEvent from "@testing-library/user-event";
import { render, screen } from "@/__tests__/customQueries";
import useOneDimensionalRovingTabIndex from "../useOneDimensionalRovingTabIndex";
import ListItemText from "@mui/material/ListItemText";
import ListItemButton from "@mui/material/ListItemButton";
import ListItem from "@mui/material/ListItem";
import List from "@mui/material/List";
import Button from "@mui/material/Button";
import Breadcrumbs from "@mui/material/Breadcrumbs";
import Chip from "@mui/material/Chip";

function SimpleExample(): React.ReactNode {
  const { getTabIndex, getRef, eventHandlers } =
    useOneDimensionalRovingTabIndex<HTMLDivElement>({ max: 1 });
  return (
    <>
      <Button>Before the list</Button>
      <List {...eventHandlers}>
        <ListItem>
          <ListItemButton tabIndex={getTabIndex(0)} ref={getRef(0)} onClick={() => {}}>
            <ListItemText primary="One Thing" />
          </ListItemButton>
        </ListItem>
        <ListItem>
          <ListItemButton tabIndex={getTabIndex(1)} ref={getRef(1)} onClick={() => {}}>
            <ListItemText primary="Two Thing" />
          </ListItemButton>
        </ListItem>
      </List>
      <Button>After the list</Button>
    </>
  );
}

function HorizontalExample(): React.ReactNode {
  const { getTabIndex, getRef, eventHandlers } =
    useOneDimensionalRovingTabIndex<HTMLDivElement>({
      max: 1,
      direction: "row",
    });
  return (
    <>
      <Button>Before the list</Button>
      <Breadcrumbs {...eventHandlers}>
        <Chip
          ref={getRef(0)}
          tabIndex={getTabIndex(0)}
          clickable
          label="One Thing"
        />
        <Chip
          ref={getRef(1)}
          tabIndex={getTabIndex(1)}
          clickable
          label="Two Thing"
        />
      </Breadcrumbs>
      <Button>After the list</Button>
    </>
  );
}

describe("useOneDimensionalRovingTabIndex", () => {
  test("tabs through the list between the before and after buttons", async () => {
    const user = userEvent.setup();

    render(<SimpleExample />);

    await user.tab();
    expect(
      screen.getByRole("button", { name: "Before the list" }),
    ).toHaveFocus();

    await user.tab();
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.tab();
    expect(
      screen.getByRole("button", { name: "After the list" }),
    ).toHaveFocus();
  });

  test("moves focus through the vertical list with arrow keys", async () => {
    const user = userEvent.setup();

    render(<SimpleExample />);

    await user.tab();
    await user.tab();
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("button", { name: "Two Thing" })).toHaveFocus();

    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.keyboard("{ArrowUp}");
    expect(screen.getByRole("button", { name: "Two Thing" })).toHaveFocus();
  });

  test("moves focus through the horizontal list with left and right arrows", async () => {
    const user = userEvent.setup();

    render(<HorizontalExample />);

    await user.tab();
    await user.tab();
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("button", { name: "Two Thing" })).toHaveFocus();

    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("button", { name: "One Thing" })).toHaveFocus();

    await user.keyboard("{ArrowLeft}");
    expect(screen.getByRole("button", { name: "Two Thing" })).toHaveFocus();
  });
});
