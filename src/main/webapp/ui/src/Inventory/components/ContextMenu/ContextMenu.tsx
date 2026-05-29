import React, { useState, useLayoutEffect, useRef } from "react";
import { observer } from "mobx-react-lite";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import contextActions from "./ContextActions";
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import StyledMenu from "../../../components/StyledMenu";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";

type ContextAction = {
  hidden?: boolean;
  component: React.ReactNode;
};

export type ContextMenuArgs = {
  selectedResults: Array<InventoryRecord>;
  forceDisabled?: string;
  onSelectOptions?: Array<SplitButtonOption>;
  menuID: string;
  paddingTop: boolean;
  basketSearch: boolean;
};

function ContextMenu({
  selectedResults = [],
  forceDisabled = "",
  onSelectOptions,
  menuID,
  paddingTop,
  basketSearch,
}: ContextMenuArgs): React.ReactNode {
  const anySelected = selectedResults.length > 0;
  const mixedSelectedStatus =
    selectedResults.some((r) => r.deleted) &&
    selectedResults.some((r) => !r.deleted);

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [overflow, setOverflow] = useState<Set<number>>(new Set());
  const containerRef = useRef<HTMLDivElement | null>(null);

  const actions = contextActions({
    selectedResults,
    mixedSelectedStatus,
    forceDisabled,
    closeMenu: () => setAnchorEl(null),
    onSelectOptions,
    menuID,
    basketSearch,
  });

  const buttonList = actions("button");

  useLayoutEffect(() => {
    const root = containerRef.current;
    if (!root) return;
    const io = new IntersectionObserver(
      (entries) => {
        setOverflow((prev) => {
          const next = new Set(prev);
          for (const e of entries) {
            const idx = Number((e.target as HTMLElement).dataset.idx);
            if (e.intersectionRatio < 1) next.add(idx);
            else next.delete(idx);
          }
          return next;
        });
      },
      { root, threshold: 1 },
    );
    root
      .querySelectorAll<HTMLElement>("[data-idx]")
      .forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, [buttonList.length, selectedResults]);

  const alertMessage =
    mixedSelectedStatus &&
    `Please select only 'Current' or 'In Trash' items to view more actions`;

  return (
    <div>
      {anySelected && (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            pt: paddingTop ? 1 : 0,
          }}
        >
          <Box
            ref={containerRef}
            sx={{
              display: "flex",
              flex: 1,
              minWidth: 0,
              overflow: "hidden",
              pr: 1,
            }}
          >
            {buttonList.map((action: ContextAction, i) =>
              !action.hidden ? (
                <Box
                  key={i}
                  data-idx={i}
                  sx={{
                    flex: "0 0 auto",
                    px: "2px",
                    "&:first-of-type": { pl: 0 },
                    visibility: overflow.has(i) ? "hidden" : "visible",
                  }}
                  aria-hidden={overflow.has(i)}
                >
                  {action.component}
                </Box>
              ) : null,
            )}
          </Box>
          <IconButtonWithTooltip
            title="More actions"
            icon={<MoreHorizIcon />}
            aria-haspopup="menu"
            sx={{ p: 0.75 }}
            size="medium"
            onClick={(event) => setAnchorEl(event.currentTarget)}
            disabled={overflow.size === 0}
          />
          <StyledMenu
            anchorEl={anchorEl}
            open={Boolean(anchorEl) && overflow.size > 0}
            onClose={() => setAnchorEl(null)}
            disableAutoFocusItem={true}
          >
            {actions("menuitem").map((action: ContextAction, i) =>
              !action.hidden && overflow.has(i) ? action.component : null,
            )}
          </StyledMenu>
        </Box>
      )}
      {mixedSelectedStatus && (
        <Alert severity="warning" sx={{ my: 0.5, py: 0 }}>
          {alertMessage}
        </Alert>
      )}
    </div>
  );
}

export default observer(ContextMenu);
