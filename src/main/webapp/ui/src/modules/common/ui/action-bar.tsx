import { MoreHorizontal } from "lucide-react";
import type * as React from "react";
import { Button } from "@/modules/common/ui/button";
import { Menu, MenuContent, MenuItem, MenuTrigger } from "@/modules/common/ui/menu";
import { cn } from "@/modules/common/utils/cn";

export type ActionBarAction = {
  label: string;
  /**
   * Optional. Actions without one still get the slot reserved in the overflow menu, so labels stay
   * on one left edge whether or not their neighbours are illustrated.
   */
  icon?: React.ComponentType<{ className?: string }>;
  onClick?: () => void;
  disabled?: boolean;
  /**
   * Mirrors `UIAlertAction.Style`. `cancel` is placed by its role rather than by its position in
   * the array, so it always leads and is never sent to the overflow menu, whichever order it was
   * given in. Only the first `cancel` is treated as one, as on iOS.
   */
  style?: "default" | "cancel" | "destructive";
  /**
   * Mirrors `preferredAction`: emphasises the action without moving it. Position comes from role
   * and order alone, so marking something preferred never reshuffles the row.
   */
  preferred?: boolean;
  /** Keeps this action in the row at every width. */
  alwaysVisible?: boolean;
};

/** Beyond this the bar stops being a bar. Extra actions are dropped rather than silently crowded. */
const MAXIMUM_ACTIONS = 5;

/**
 * Actions share the leftover width evenly; More does not. A grid of equal `fr` tracks cannot size
 * one track to its content, so the row is a flex line: actions grow, More keeps a square 44px
 * touch target and gives everything else back.
 */
const ACTION_CLASS = "h-11 min-w-0 flex-1 basis-0 rounded-none bg-clip-border text-sm";

const MORE_CLASS =
  "flex h-11 w-11 shrink-0 items-center justify-center rounded-none outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/30";

/**
 * Width each action is given before the next one is allowed to appear. Kept as whole literal class
 * strings, not built by template, because Tailwind scans source text and never sees a computed one.
 *
 * Index is the action's position: the first is always visible, the second appears once the bar can
 * seat two, and so on.
 */
const REVEAL_AT = ["", "@min-[16rem]:flex", "@min-[24rem]:flex", "@min-[32rem]:flex", "@min-[40rem]:flex"];

/** Indexed by how many actions there are in total: More goes once the bar can seat all of them. */
const HIDE_MORE_AT = [
  "hidden",
  "hidden",
  "@min-[16rem]:hidden",
  "@min-[24rem]:hidden",
  "@min-[32rem]:hidden",
  "@min-[40rem]:hidden",
];

/**
 * The action row that closes a popover or dialog: full-bleed, equal tracks, hairline dividers, sat
 * against the bottom edge so the surface's own rounding clips it.
 *
 * Placement follows Apple's alert rules. A `cancel` action leads, because on iOS the leading button
 * is the one that goes back without committing, and everything else follows in the order given.
 * With the usual two actions that puts the confirming one on the trailing edge, where the HIG wants
 * the button people are most likely to press.
 *
 * Order among the non-cancel actions is priority order, which is what decides overflow: the first
 * of them is always visible, later ones appear as the container widens, and anything not shown is
 * reachable from the More menu. Cancel and the leading action are never overflowed, so the row
 * never hides the two choices an alert is actually asking about.
 *
 * The reveal is a container query rather than measurement, so it responds to the surface it is
 * placed in with no resize observer, no layout effect, and no reflow. The cost is that the menu
 * lists every overflowable action rather than only the hidden ones: menus render through a portal,
 * outside the container being queried, so they cannot answer the same query. At two or three
 * actions, the common cases, there is no redundancy at all.
 */
export function ActionBar({ actions, className }: { actions: readonly ActionBarAction[]; className?: string }) {
  const capped = actions.slice(0, MAXIMUM_ACTIONS);
  const cancel = capped.find((action) => action.style === "cancel");
  const rest = capped.filter((action) => action !== cancel);
  const ordered = cancel ? [cancel, ...rest] : rest;
  // Cancel plus the leading action stay put; only what comes after them can be overflowed.
  const pinned = cancel ? 2 : 1;
  const overflow = ordered.filter((action, index) => index >= pinned && !action.alwaysVisible);
  if (ordered.length === 0) return null;

  return (
    <div data-slot="action-bar" className={cn("@container shrink-0 border-border border-t", className)}>
      <div className="flex divide-x divide-border">
        {ordered.map((action, index) => (
          <Button
            key={action.label}
            data-slot="action-bar-item"
            type="button"
            variant="ghost"
            disabled={action.disabled}
            onClick={action.onClick}
            className={cn(
              ACTION_CLASS,
              action.preferred && "font-semibold",
              action.style === "destructive" ? "text-destructive" : action.preferred && "text-primary",
              index < pinned || action.alwaysVisible ? "flex" : cn("hidden", REVEAL_AT[index]),
            )}
          >
            {action.icon ? <action.icon className="size-4" aria-hidden="true" /> : null}
            {action.label}
          </Button>
        ))}

        {overflow.length > 0 ? (
          <Menu>
            <MenuTrigger
              data-slot="action-bar-more"
              aria-label="More actions"
              className={cn(MORE_CLASS, HIDE_MORE_AT[ordered.length])}
            >
              <MoreHorizontal className="size-4" aria-hidden="true" />
            </MenuTrigger>
            <MenuContent className="w-56">
              {overflow.map((action) => (
                <MenuItem
                  key={action.label}
                  disabled={action.disabled}
                  onClick={action.onClick}
                  className={cn(action.style === "destructive" && "text-destructive")}
                >
                  {/* Reserved whether or not there is an icon, so every label starts at one edge. */}
                  <span className="grid size-4 shrink-0 place-items-center">
                    {action.icon ? <action.icon className="size-4" aria-hidden="true" /> : null}
                  </span>
                  {action.label}
                </MenuItem>
              ))}
            </MenuContent>
          </Menu>
        ) : null}
      </div>
    </div>
  );
}
