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
import EventBoundary from "../../../components/EventBoundary";

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
