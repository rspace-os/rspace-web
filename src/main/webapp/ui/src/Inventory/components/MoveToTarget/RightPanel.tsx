import SearchContext from "../../../stores/contexts/Search";
import useStores from "../../../stores/use-stores";
import { menuIDs } from "../../../util/menuIDs";
import SearchView from "../../Search/SearchView";
import NameWithBadge from "../NameWithBadge";
import MoveInstructions from "./MoveInstructions";
import { observer } from "mobx-react-lite";
import React from "react";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardContent from "@mui/material/CardContent";
import Grid from "@mui/material/Grid";

const Title = observer(() => {
  const { moveStore, peopleStore } = useStores();
  const activeResult = moveStore.activeResult;
  if (moveStore.loading || !activeResult) {
    return "Loading...";
  }
  if (
    activeResult.isWorkbench &&
    activeResult.ownerLabel &&
    peopleStore.currentUser?.bench?.globalId
  ) {
    const ownerPrefix =
      activeResult.globalId === peopleStore.currentUser.bench.globalId
        ? "My"
        : `${activeResult.ownerLabel}'s`;
    return ownerPrefix + " Bench";
  }
  return <NameWithBadge record={activeResult} />;
});

const Content = observer(() => {
  const { moveStore } = useStores();
  const activeResult = moveStore.activeResult;
  return activeResult ? (
    <SearchContext.Provider
      value={{
        search: activeResult.contentSearch,
        scopedResult: activeResult,
        differentSearchForSettingActiveResult: activeResult.contentSearch,
      }}
    >
      <Grid container direction="column" spacing={1} wrap="nowrap">
        <Grid item>
          <MoveInstructions />
        </Grid>
        <Grid item>
          <SearchView contextMenuId={menuIDs.NONE} />
        </Grid>
      </Grid>
    </SearchContext.Provider>
  ) : null;
});

type RightPanelArgs = Record<string, never>;

function RightPanel() {
  return (
    <Card elevation={0}>
      <CardHeader
        title={
          <>
            Selected Destination: <Title />
          </>
        }
        sx={{ py: 1 }}
        titleTypographyProps={{
          variant: "h6",
          component: "h3",
        }}
      />
      <CardContent sx={{ pt: 0 }}>
        <Content />
      </CardContent>
    </Card>
  );
}

export default observer(RightPanel);
