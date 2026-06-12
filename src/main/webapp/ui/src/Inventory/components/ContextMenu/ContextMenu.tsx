import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useLayoutEffect, useRef, useState } from "react";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import StyledMenu from "../../../components/StyledMenu";
// biome-ignore lint/style/useImportType: initial biome migration
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type SplitButtonOption } from "../../components/ContextMenu/ContextMenuSplitButton";
import contextActions from "./ContextActions";

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
  const mixedSelectedStatus = selectedResults.some((r) => r.deleted) && selectedResults.some((r) => !r.deleted);

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [overflow, setOverflow] = useState<Set<number>>(new Set());
  // Buttons stay hidden until the first width measurement so that only the
  // ones that actually fit are ever shown, rather than flashing the full set.
  const [measured, setMeasured] = useState(false);
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

    /*
     * Measure synchronously, before the browser paints, which buttons fit. The
     * buttons render hidden until this runs (see `measured`), so the first
     * painted frame shows only the buttons that fit rather than flashing the
     * full set and then hiding the overflow once the IntersectionObserver
     * (which reports asynchronously, after the first paint) catches up. A
     * button overflows when its right edge extends past the container's visible
     * right edge (its content box, i.e. excluding the trailing padding).
     */
    const visibleRight =
      root.getBoundingClientRect().right - (parseFloat(window.getComputedStyle(root).paddingRight) || 0);
    const initialOverflow = new Set<number>();
    root.querySelectorAll<HTMLElement>("[data-idx]").forEach((el) => {
      if (el.getBoundingClientRect().right > visibleRight + 1) initialOverflow.add(Number(el.dataset.idx));
    });
    setOverflow((prev) =>
      prev.size === initialOverflow.size && [...prev].every((i) => initialOverflow.has(i)) ? prev : initialOverflow,
    );
    setMeasured(true);

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
      // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
      .forEach((el) => io.observe(el));
    return () => io.disconnect();
  }, [buttonList.length, selectedResults]);

  const alertMessage = mixedSelectedStatus && `Please select only 'Current' or 'In Trash' items to view more actions`;

  return (
    /*
     * This sits inside a `colSpan` header cell of an auto-layout table. Letting
     * its content (the action buttons and the warning alert) define the cell's
     * intrinsic width would widen the whole table past its container. Pinning
     * `width: 0` keeps the cell's intrinsic width contribution at ~0 (so the
     * table stays sized by its data rows), while `minWidth: 100%` makes this
     * box fill the cell at layout time. That gives the overflow container below
     * a real width to measure against, so surplus buttons move into the menu.
     */
    <Box sx={{ width: 0, minWidth: "100%" }}>
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
                    visibility: measured && !overflow.has(i) ? "visible" : "hidden",
                  }}
                  aria-hidden={!measured || overflow.has(i)}
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
    </Box>
  );
}

export default observer(ContextMenu);
