import ArrowBackIosIcon from "@mui/icons-material/ArrowBackIos";
import IconButton from "@mui/material/IconButton";
import type { SxProps, Theme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import useStores from "@/stores/use-stores";
import { useIsSingleColumnLayout } from "../components/Layout/Layout2x1";

/**
 * Returns from the Requests page's detail pane to the list. Only rendered when the layout is a single
 * column, as both panes are otherwise visible side by side.
 */
function BackToListButton({ sx }: { sx?: SxProps<Theme> }): React.ReactNode {
  const { t } = useTranslation("common");
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();

  if (!isSingleColumnLayout) return null;
  return (
    <IconButton
      aria-label={t("actions.back")}
      onClick={() => uiStore.setSampleRequestsPanel("left")}
      size="small"
      sx={sx}
    >
      <ArrowBackIosIcon fontSize="small" sx={{ ml: 0.5 }} />
    </IconButton>
  );
}

export default observer(BackToListButton);
