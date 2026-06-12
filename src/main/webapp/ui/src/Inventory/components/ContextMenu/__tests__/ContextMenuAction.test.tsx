import MenuList from "@mui/material/MenuList";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import fc from "fast-check";
// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";
import { describe, expect, test, vi } from "vitest";
import ContextMenuAction from "../ContextMenuAction";

function OuterComponent({ onKeyDown, children }: { onKeyDown: () => void; children: React.ReactNode }) {
  // biome-ignore lint/a11y/noStaticElementInteractions: initial biome migration
  return <div onKeyDown={onKeyDown}>{children}</div>;
}
function InnerComponent() {
  return <input />;
}
describe("ContextMenuAction", () => {
  test("When as a menuitem, keyDown events should not propagated through ContextMenuAction", async () => {
    const onKeyDown = vi.fn();
    render(
      <OuterComponent onKeyDown={onKeyDown}>
        <MenuList>
          <ContextMenuAction
            as="menuitem"
            onClick={() => {}}
            // biome-ignore lint/complexity/noUselessFragments: initial biome migration
            icon={<></>}
            label="Foo"
            disabledHelp=""
          >
            <InnerComponent />
          </ContextMenuAction>
        </MenuList>
      </OuterComponent>,
    );
    const input = await screen.findByRole("textbox");

    fireEvent.keyDown(input, {});
    expect(onKeyDown).not.toHaveBeenCalled();
  });
  describe("Disabled state", () => {
    test("When disabled, should render aria-disabled.", () => {
      fc.assert(
        fc.property(fc.string(), (disabledHelp) => {
          cleanup();
          render(
            <MenuList>
              <ContextMenuAction
                as="menuitem"
                // biome-ignore lint/complexity/noUselessFragments: initial biome migration
                icon={<></>}
                disabledHelp={disabledHelp}
                label="Foo"
                onClick={() => {}}
              />
            </MenuList>,
          );
          expect(screen.getByRole("menuitem", { name: /^Foo/ })).toHaveAttribute(
            "aria-disabled",
            (disabledHelp !== "").toString(),
          );
        }),
      );
    });
  });
});
