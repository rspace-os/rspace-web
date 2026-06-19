import MenuItem from "@mui/material/MenuItem";
import type React from "react";

/**
 * A CSV/export menu item for use as a child of MUI X's
 * `GridToolbarExportContainer`.
 *
 * The container clones its children and injects a `hideMenu` callback (used to
 * close the export menu after an action). Because MUI's `MenuItem` forwards
 * unknown props to its root `<li>`, passing a bare `<MenuItem>` as the child
 * leaks `hideMenu` onto the DOM — which React 19 reports as
 * "React does not recognize the `hideMenu` prop on a DOM element".
 *
 * This wrapper keeps `hideMenu` off the DOM and invokes it after the
 * (optionally async) click handler so the menu closes as intended.
 */
const ExportMenuItem = ({
  onClick,
  hideMenu,
  children,
  ...rest
}: Omit<React.ComponentProps<typeof MenuItem>, "onClick"> & {
  onClick: () => void | Promise<void>;
  hideMenu?: () => void;
}): React.ReactNode => (
  <MenuItem
    {...rest}
    onClick={() => {
      void (async () => {
        await onClick();
        hideMenu?.();
      })();
    }}
  >
    {children}
  </MenuItem>
);

export default ExportMenuItem;
