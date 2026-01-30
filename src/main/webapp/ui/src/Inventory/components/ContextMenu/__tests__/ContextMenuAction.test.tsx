/*
 */
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React from "react";
import {
  render,
  cleanup,
  screen,
  fireEvent,
} from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import ContextMenuAction from "../ContextMenuAction";
import fc from "fast-check";

beforeEach(() => {
  vi.clearAllMocks();
});


function OuterComponent({
  onKeyDown,
  children,
}: {
  onKeyDown: () => void;
  children: React.ReactNode;
}) {
  return <div onKeyDown={onKeyDown}>{children}</div>;
}

function InnerComponent() {
  return <input />;
}

describe("ContextMenuAction", () => {
  it("When as a menuitem, keyDown events should not propagated through ContextMenuAction", async () => {
    const onKeyDown = vi.fn();
    render(
      <OuterComponent onKeyDown={onKeyDown}>
        <ContextMenuAction
          as="menuitem"
          onClick={() => {}}
          icon={<></>}
          label="Foo"
          disabledHelp=""
        >
          <InnerComponent />
        </ContextMenuAction>
      </OuterComponent>
    );

    const input = await screen.findByRole("textbox");
    fireEvent.keyDown(input, {});

    expect(onKeyDown).not.toHaveBeenCalled();
  });

  describe("Disabled state", () => {
    it("When disabled, should render aria-disabled.", () => {
      fc.assert(
        fc.property(fc.string(), (disabledHelp) => {
          cleanup();
          render(
            <ContextMenuAction
              as="menuitem"
              icon={<></>}
              disabledHelp={disabledHelp}
              label="Foo"
              onClick={() => {}}
            />
          );
          expect(
            screen.getByRole("menuitem", { name: /^Foo/ })
          ).toHaveAttribute("aria-disabled", (disabledHelp !== "").toString());
        })
      );
    });
  });
});


