import React from "react";
import ArgosDMPDialog from "./Argos/DMPDialog";
import DMPAssistantDMPDialog from "./DMPAssistant/DMPDialog";
import DMPOnlineDMPDialog from "./DMPOnline/DMPDialog";
import DMPToolDMPDialog from "./DMPTool/DMPDialog";
import type { DswConfig } from "./DSW/DSWAccentMenuItem";
import DSWImportDialog from "./DSW/DSWImportDialog";

/** Which DMP source the user picked, and any configuration it needs. */
export type DmpImportTarget =
  | { source: "argos" }
  | { source: "dmpAssistant" }
  | { source: "dmponline" }
  | { source: "dmptool" }
  | { source: "dsw"; connection: DswConfig };

/**
 * The DMP import dialogs, rendered as a sibling of the menu that opens them
 * rather than inside it.
 *
 * Each dialog used to be rendered by its own menu item, so it was a child of
 * the Gallery create menu in the React tree. That coupled the two: tearing the
 * menu down took the open dialog with it, and a re-render or suspension while
 * both were live could strand one of them (PRT-1118, PRT-1135). The dialogs
 * already portal out of the menu in the DOM, so only the tree relationship was
 * doing harm. Hoisting them here removes it, so the menu can close as soon as a
 * dialog opens without touching the dialog.
 *
 * The last target is retained after `target` goes null so the dialog can play
 * its exit transition instead of being unmounted mid-animation.
 */
export default function DmpImportDialogs({
  target,
  onClose,
}: {
  target: DmpImportTarget | null;
  onClose: () => void;
}): React.ReactNode {
  const [lastTarget, setLastTarget] = React.useState<DmpImportTarget | null>(target);
  React.useEffect(() => {
    if (target) setLastTarget(target);
  }, [target]);

  const shown = target ?? lastTarget;
  if (!shown) return null;

  const open = Boolean(target);
  const setOpen = (isOpen: boolean) => {
    if (!isOpen) onClose();
  };

  switch (shown.source) {
    case "argos":
      return <ArgosDMPDialog open={open} setOpen={setOpen} />;
    case "dmpAssistant":
      return <DMPAssistantDMPDialog open={open} setOpen={setOpen} />;
    case "dmponline":
      return <DMPOnlineDMPDialog open={open} setOpen={setOpen} />;
    case "dmptool":
      return <DMPToolDMPDialog open={open} setOpen={setOpen} />;
    case "dsw":
      return <DSWImportDialog open={open} setOpen={setOpen} connection={shown.connection} />;
  }
}
