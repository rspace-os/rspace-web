import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import MenuItem from "@mui/material/MenuItem";
import { observer } from "mobx-react-lite";
// biome-ignore lint/style/useImportType: initial biome migration
import React, { useLayoutEffect, useRef, useState } from "react";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import StyledMenu from "../../../components/StyledMenu";
// biome-ignore lint/style/useImportType: initial biome migration
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import ContextMenu, { type ContextMenuArgs } from "./ContextMenu";
import ContextMenuButton from "./ContextMenuButton";
import ContextMenuSplitButton, { type SplitButtonOption } from "./ContextMenuSplitButton";

type ExtendedContextMenuArgs = {
  prefixActions: Array<
    | {
        disabledHelp: string;
        icon: React.ReactElement;
        key: string;
        options: Array<SplitButtonOption>;
      }
    | {
        disabledHelp: string;
        icon: React.ReactElement;
        key: string;
        label: string;
        variant?: "default" | "filled";
        onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
        active?: boolean;
      }
  >;
  selectedResults: Array<InventoryRecord>;
  onSelectOptions?: Array<SplitButtonOption>;
  menuID: string;
  basketSearch?: boolean;
} & Omit<ContextMenuArgs, "selectedResults" | "menuID" | "onSelectOptions" | "basketSearch" | "paddingTop">;

function ExtendedContextMenu({
  prefixActions,
  selectedResults,
  onSelectOptions,
  menuID,
  basketSearch,
  ...rest
}: ExtendedContextMenuArgs): React.ReactNode {
  const prefixRef = useRef<HTMLDivElement | null>(null);
  const [overflow, setOverflow] = useState<Set<number>>(new Set());
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  useLayoutEffect(() => {
    const root = prefixRef.current;
    if (!root) return;
    const io = new IntersectionObserver(
      (entries) => {
        setOverflow((prev) => {
          const next = new Set(prev);
          for (const e of entries) {
            const idx = Number((e.target as HTMLElement).dataset.idx);
            if (e.intersectionRatio < 0.1) next.add(idx);
            else next.delete(idx);
          }
          return next;
        });
      },
      { root, threshold: 0.1 },
    );
    root
      .querySelectorAll<HTMLElement>("[data-idx]")
      // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
      .forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, [prefixActions.length]);

  return (
    <Box sx={{ display: "flex", alignItems: "center" }}>
      <Box
        ref={prefixRef}
        sx={{
          display: "flex",
          gap: "4px",
          overflow: "hidden",
          minWidth: 0,
        }}
      >
        {/* Note: in the "content" ContextMenu the Select action is included in prefixActions */}
        {prefixActions.map((action, i) => {
          const { key, ...actionProps } = action;
          return (
            <Box
              key={key}
              data-idx={i}
              sx={{
                flex: "0 0 auto",
                visibility: overflow.has(i) ? "hidden" : "visible",
              }}
              aria-hidden={overflow.has(i)}
            >
              {"options" in actionProps ? (
                <ContextMenuSplitButton {...actionProps} />
              ) : (
                <ContextMenuButton {...actionProps} />
              )}
            </Box>
          );
        })}
      </Box>
      <IconButtonWithTooltip
        title="More actions"
        icon={<MoreHorizIcon />}
        aria-haspopup="menu"
        size="medium"
        sx={{
          p: 0.75,
          display: overflow.size === 0 ? "none" : "block",
        }}
        onClick={(event) => setAnchorEl(event.currentTarget)}
        disabled={overflow.size === 0}
      />
      <StyledMenu
        anchorEl={anchorEl}
        open={Boolean(anchorEl) && overflow.size > 0}
        onClose={() => setAnchorEl(null)}
        disableAutoFocusItem
        sx={{
          pr: 4,
        }}
      >
        {prefixActions.map((action, i) => {
          if (!overflow.has(i)) return null;
          if ("options" in action) {
            const first = action.options[0];
            return (
              <MenuItem
                key={action.key}
                disabled={action.disabledHelp !== "" || !first}
                onClick={() => {
                  first?.selection?.();
                  setAnchorEl(null);
                }}
              >
                {first?.text ?? "Action"}
              </MenuItem>
            );
          }
          return (
            <MenuItem
              key={action.key}
              disabled={action.disabledHelp !== ""}
              onClick={(e) => {
                action.onClick?.(e as unknown as React.MouseEvent<HTMLButtonElement>);
                setAnchorEl(null);
              }}
            >
              {action.label}
            </MenuItem>
          );
        })}
      </StyledMenu>
      {selectedResults.length > 0 && (
        <>
          <Divider orientation="vertical" flexItem sx={{ mx: "3px" }} />
          {/* NB: in other context menus ("results" and "stepper"), the Select action is part of the ContextMenu actions */}
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <ContextMenu
              selectedResults={selectedResults}
              menuID={menuID}
              onSelectOptions={onSelectOptions}
              basketSearch={basketSearch ?? false}
              paddingTop={false}
              {...rest}
            />
          </Box>
        </>
      )}
    </Box>
  );
}

export default observer(ExtendedContextMenu);
