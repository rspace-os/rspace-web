import MenuItem from "@mui/material/MenuItem";
import { render } from "@testing-library/react";
import React from "react";
import { afterAll, describe, expect, test } from "vitest";
import { DialogBoundary, Menu } from "../DialogBoundary";

const anchor = document.createElement("button");
document.body.appendChild(anchor);
afterAll(() => {
  anchor.remove();
});

/**
 * PRT-1135. MUI's Portal keys a layout effect on its `container` prop and
 * re-resolves the mount node whenever that value changes, falling back to
 * `document.body` when the container resolves to null. Passing a fresh arrow
 * on every render re-ran that on every render and re-parented the portal, and
 * a changed mount node makes React rebuild the whole portal subtree,
 * destroying any state held inside it. In the Gallery that destroyed an open
 * DMP import dialog mid-import, leaving the create menu that owned it on
 * screen.
 */
describe("DialogBoundary", () => {
  test("re-rendering the boundary does not remount what is inside a Menu", () => {
    let mounts = 0;
    function CountsItsMounts() {
      React.useEffect(() => {
        mounts += 1;
      }, []);
      return <MenuItem aria-label="item" />;
    }

    function Harness() {
      return (
        <DialogBoundary>
          <Menu open anchorEl={anchor} onClose={() => {}}>
            <CountsItsMounts />
          </Menu>
        </DialogBoundary>
      );
    }

    const { rerender } = render(<Harness />);
    expect(mounts).toBe(1);

    rerender(<Harness />);
    rerender(<Harness />);

    expect(mounts).toBe(1);
  });
});
