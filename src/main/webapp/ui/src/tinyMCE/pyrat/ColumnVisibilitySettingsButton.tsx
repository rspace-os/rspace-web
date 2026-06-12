import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
import ExpandCollapseIcon from "../../components/ExpandCollapseIcon";

export default function ColumnVisibilitySettingsButton({
  showSettings,
  setShowSettings,
}: {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  showSettings: any;
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
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
