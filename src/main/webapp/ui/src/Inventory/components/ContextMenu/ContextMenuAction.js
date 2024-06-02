//@flow

import React, { type Node, type ComponentType, forwardRef } from "react";
import { Observer } from "mobx-react-lite";
import ContextMenuButton from "./ContextMenuButton";
import ContextMenuSplitButton, {
  type SplitButtonOption,
} from "./ContextMenuSplitButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import { StyledMenuItem } from "../../../components/StyledMenu";

/*
 * All of the DOM events that happen inside of a context menu action, such as
 * events within a dialog, shouln't propagate outside as the context menu will
 * take them to be events that it should respond to by providing keyboard
 * navigation. See ../../../../QuirksOfMaterialUi.md, secion
 * "Dialogs inside Menus", for more information.
 */
const EventBoundary = ({ children }: { children: Node }) => (
  <div
    onKeyDown={(e) => {
      e.stopPropagation();
    }}
    onMouseDown={(e) => {
      e.stopPropagation();
    }}
    onClick={(e) => {
      e.stopPropagation();
    }}
  >
    {children}
  </div>
);

export type ContextMenuRenderOptions = "button" | "menuitem";

type CommonArgs = {|
  as: ContextMenuRenderOptions,
  icon: Node,
  disabledHelp: string,
  children?: Node,
|};

type SplitButtonArgs = {|
  options: Array<SplitButtonOption>,
|};

type RegularButtonArgs = {|
  onClick: (Event) => void,
  active?: boolean,
  label: string,
|};

type ContextMenuActionsArgs = {|
  ...CommonArgs,
  ...SplitButtonArgs | RegularButtonArgs,
|};

const ContextMenuAction: ComponentType<ContextMenuActionsArgs> = forwardRef(
  (
    {
      as,
      icon,
      onClick = () => {},
      options = [],
      label = "",
      disabledHelp,
      active = false,
      children,
    }: ContextMenuActionsArgs,
    ref
  ) => {
    return (
      <Observer>
        {() =>
          as === "button" ? (
            options && options.length > 0 ? (
              <>
                <ContextMenuSplitButton
                  options={options}
                  icon={icon}
                  disabledHelp={disabledHelp}
                />
                {children}
              </>
            ) : (
              <>
                <ContextMenuButton
                  onClick={onClick}
                  icon={icon}
                  label={label}
                  disabledHelp={disabledHelp}
                  active={active}
                />
                {children}
              </>
            )
          ) : (
            <StyledMenuItem
              ref={ref}
              onClick={onClick}
              disabled={disabledHelp !== ""}
              aria-disabled={disabledHelp !== ""}
              sx={{ my: 0.5, py: 1 }}
            >
              <ListItemIcon>{icon}</ListItemIcon>
              <ListItemText
                primary={label}
                secondary={disabledHelp}
                secondaryTypographyProps={{
                  style: {
                    whiteSpace: "break-spaces",
                  },
                }}
              />
              {Boolean(children) && (
                <EventBoundary>{children ?? null}</EventBoundary>
              )}
            </StyledMenuItem>
          )
        }
      </Observer>
    );
  }
);

ContextMenuAction.displayName = "ContextMenuAction";
export default ContextMenuAction;
