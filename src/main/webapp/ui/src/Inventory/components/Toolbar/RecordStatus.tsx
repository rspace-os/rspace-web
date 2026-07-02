import Box from "@mui/material/Box";
import { cyan } from "@mui/material/colors";
import { useTheme } from "@mui/material/styles";
import type React from "react";
import type { State } from "@/stores/definitions/InventoryRecord";
import { match } from "@/util/Util";

type RecordStatusArgs = {
  recordState: State;
  deleted: boolean;
};

/*
 * It can be difficult to tell at a glance what the current state of a record
 * is. This component displays the most important information, passed as props,
 * using bold colours to make the information clear.
 */
export default function RecordStatus({ recordState, deleted }: RecordStatusArgs): React.ReactNode {
  const areEditing = recordState === "edit";
  const areCreating = recordState === "create";
  const theme = useTheme();
  const prefersMoreContrast = window.matchMedia("(prefers-contrast: more)").matches;
  const color = match<void, string>([
    [() => areEditing, cyan[prefersMoreContrast ? 900 : 800]],
    [() => areCreating, cyan[prefersMoreContrast ? 900 : 800]],
    [() => deleted, theme.palette.deletedGrey],
    [() => true, "black"],
  ])();
  const belowContextMenu = !areEditing && !areCreating;

  return areEditing || areCreating || deleted ? (
    <Box
      sx={(muiTheme) => ({
        height: muiTheme.spacing(3),
        top: belowContextMenu ? 40 : 0,
        position: "sticky",
        zIndex: 1000,
        borderTop: `${muiTheme.spacing(0.5)} solid ${color}`,
        display: "flex",
        justifyContent: "flex-end",
      })}
    >
      <Box
        sx={(muiTheme) => ({
          backgroundColor: color,
          right: muiTheme.spacing(1),
          color: "white",
          px: 1.5,
          py: 0.5,
          borderBottomLeftRadius: muiTheme.spacing(0.5),
          borderBottomRightRadius: muiTheme.spacing(0.5),
          position: "absolute",
          top: muiTheme.spacing(-0.5),
          fontWeight: 600,
        })}
      >
        {areEditing && "EDITING"}
        {areCreating && "CREATING"}
        {deleted && "IN TRASH"}
      </Box>
    </Box>
  ) : null;
}
