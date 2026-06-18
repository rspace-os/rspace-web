import Box from "@mui/material/Box";
import { darken, useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import EmptyListingSvg from "@/assets/graphics/EmptyListing.svg";
import { type GlobalId, globalIdToInventoryRecordTypeLabel } from "../../../stores/definitions/BaseRecord";

type EmptyListingArgs = {
  parentGlobalId: GlobalId;
};

function EmptyListing({ parentGlobalId }: EmptyListingArgs): React.ReactNode {
  const theme = useTheme();
  const color = darken(theme.palette.primary.main, 0.2);
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
      <img src={EmptyListingSvg} alt="Empty Listing" />
      Empty {globalIdToInventoryRecordTypeLabel(parentGlobalId)}
      <Typography sx={{ textAlign: "center", color, mt: 2, fontSize: "1rem", maxWidth: "20em" }}>
        Nothing here yet.
      </Typography>
    </Box>
  );
}

export default EmptyListing;
