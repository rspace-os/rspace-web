import Box from "@mui/material/Box";
import { darken, useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import EmptyListingSvg from "@/assets/graphics/EmptyListing.svg";
import { type GlobalId, globalIdToInventoryRecordTypeLabel } from "../../../stores/definitions/BaseRecord";

type EmptyListingArgs = {
  parentGlobalId: GlobalId;
};

function EmptyListing({ parentGlobalId }: EmptyListingArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const color = darken(theme.palette.primary.main, 0.2);
  const recordType = globalIdToInventoryRecordTypeLabel(parentGlobalId);
  return (
    <Box
      sx={{
        mt: 6,
        fontWeight: 700,
        fontSize: "1.6rem",
        color,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        mb: 1,
      }}
    >
      <img src={EmptyListingSvg} alt={t("search.emptyListing.alt")} />
      {t("search.emptyListing.title", { recordType })}
      <Typography sx={{ textAlign: "center", color, mt: 2, fontSize: "1rem", maxWidth: "20em" }}>
        {t("search.emptyListing.body")}
      </Typography>
    </Box>
  );
}

export default EmptyListing;
