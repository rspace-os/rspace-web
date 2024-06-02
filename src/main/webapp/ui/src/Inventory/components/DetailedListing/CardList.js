// @flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import Card from "./Card";
import Grid from "@mui/material/Grid";
import { withStyles } from "Styles";
import Box from "@mui/material/Box";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";

const GridContainer = withStyles<{| children: Node |}, { root: string }>(
  (theme) => ({
    root: {
      width: "100%",
      marginLeft: theme.spacing(-0.25),
      marginBottom: theme.spacing(1),
      alignItems: "stretch",
    },
  })
)(({ children, classes }) => (
  <Grid
    container
    spacing={2}
    direction="row"
    classes={classes}
    role="list"
    aria-label="Search results as cards"
  >
    {children}
  </Grid>
));

type CardListArgs = {|
  records: Array<InventoryRecord>,
|};

function CardList({ records }: CardListArgs): Node {
  const { uiStore } = useStores();

  return (
    <Box mt={1}>
      <GridContainer>
        {records.map((r, i) => (
          <Grid
            item
            xs={12}
            md={uiStore.isSingleColumnLayout ? 6 : 12}
            xl={uiStore.isSingleColumnLayout ? 4 : 12}
            key={i}
            role="listitem"
          >
            <Card record={r} />
          </Grid>
        ))}
      </GridContainer>
    </Box>
  );
}

export default (observer(CardList): ComponentType<CardListArgs>);
