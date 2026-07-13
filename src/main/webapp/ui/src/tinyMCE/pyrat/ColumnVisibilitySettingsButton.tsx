import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
import { useTranslation } from "react-i18next";
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
  const { t } = useTranslation("apps");

  return (
    <>
      <Typography sx={{ marginLeft: "15px" }} component="span" variant="body1" color="textPrimary">
        {t("pyrat.columnVisibility.label")}
      </Typography>
      <IconButton
        title={showSettings ? t("pyrat.columnVisibility.hideSettings") : t("pyrat.columnVisibility.showSettings")}
        onClick={() => setShowSettings(!showSettings)}
      >
        <ExpandCollapseIcon open={showSettings} />
      </IconButton>
    </>
  );
}
