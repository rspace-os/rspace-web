import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import ExpandCollapseIcon from "../../components/ExpandCollapseIcon";

export default function ColumnVisibilitySettingsButton({
  showSettings,
  setShowSettings,
}: {
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  showSettings: any;
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  setShowSettings: any;
}) {
  return (
    <>
      <Typography sx={{ marginLeft: "15px" }} component="span" variant="body1" color="textPrimary">
        Column Visibility
      </Typography>
      <IconButton
        title={showSettings ? "Hide column visibility settings" : "Show column visibility settings"}
        onClick={() => setShowSettings(!showSettings)}
      >
        <ExpandCollapseIcon open={showSettings} />
      </IconButton>
    </>
  );
}
