import Box from "@mui/material/Box";
import type React from "react";
import type { State } from "../../stores/definitions/InventoryRecord";
import RecordStatus from "./Toolbar/RecordStatus";

type StickyStatusArgs = {
  recordState: State;
  deleted: boolean;
};

/*
 * RecordStatus, with the additional styles to position it absolutely
 */
export default function StickyStatus({ recordState, deleted }: StickyStatusArgs): React.ReactNode {
  return ["create", "edit"].includes(recordState) || deleted ? (
    <Box
      sx={{
        position: "absolute",
        height: "100%",
        width: "100%",
        top: 0,
        pointerEvents: "none",
        pb: 0.5,
      }}
    >
      <RecordStatus recordState={recordState} deleted={deleted} />
    </Box>
  ) : null;
}
