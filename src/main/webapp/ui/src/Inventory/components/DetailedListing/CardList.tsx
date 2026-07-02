import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import Card from "./Card";

type CardListArgs = {
  records: Array<InventoryRecord>;
};

function CardList({ records }: CardListArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const isSingleColumnLayout = useIsSingleColumnLayout();

  return (
    <Box sx={{ mt: 1 }}>
      <Grid
        container
        spacing={2}
        direction="row"
        sx={{ width: "100%", ml: -0.25, mb: 1, alignItems: "stretch" }}
        role="list"
        aria-label={t("detailedListing.cardListLabel")}
      >
        {records.map((r, i) => (
          <Grid
            key={i}
            role="listitem"
            size={{
              xs: 12,
              md: isSingleColumnLayout ? 6 : 12,
              xl: isSingleColumnLayout ? 4 : 12,
            }}
          >
            <Card record={r} />
          </Grid>
        ))}
      </Grid>
    </Box>
  );
}

export default observer(CardList);
