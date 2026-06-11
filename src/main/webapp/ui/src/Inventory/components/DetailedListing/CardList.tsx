import React from "react";
import { observer } from "mobx-react-lite";
import Card from "./Card";
import Grid from "@mui/material/Grid";
import Box from "@mui/material/Box";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";


type CardListArgs = {
  records: Array<InventoryRecord>;
};

function CardList({ records }: CardListArgs): React.ReactNode {
  const isSingleColumnLayout = useIsSingleColumnLayout();

  return (
    <Box sx={{ mt: 1 }}>
      <Grid
        container
        spacing={2}
        direction="row"
        sx={{ width: "100%", ml: -0.25, mb: 1, alignItems: "stretch" }}
        role="list"
        aria-label="Search results as cards"
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
