import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import SearchContext from "../../../stores/contexts/Search";
import useStores from "../../../stores/use-stores";
import { menuIDs } from "../../../util/menuIDs";
import SearchView from "../../Search/SearchView";
import NameWithBadge from "../NameWithBadge";
import MoveInstructions from "./MoveInstructions";

const Title = observer(() => {
  const { moveStore, peopleStore } = useStores();
  const activeResult = moveStore.activeResult;
  if (moveStore.loading || !activeResult) {
    return "Loading...";
  }
  if (activeResult.isWorkbench && activeResult.ownerLabel && peopleStore.currentUser?.bench?.globalId) {
    const ownerPrefix =
      activeResult.globalId === peopleStore.currentUser.bench.globalId ? "My" : `${activeResult.ownerLabel}'s`;
    // biome-ignore lint/style/useTemplate: initial biome migration
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
      <Stack
        sx={{
          flexWrap: "nowrap",
        }}
        spacing={1}
      >
        <MoveInstructions />
        <SearchView contextMenuId={menuIDs.NONE} />
      </Stack>
    </SearchContext.Provider>
  ) : null;
});
// biome-ignore lint/correctness/noUnusedVariables: initial biome migration
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
        sx={{
          py: 1,
        }}
        slotProps={{
          title: {
            variant: "h6",
            component: "h3",
          },
        }}
      />
      <CardContent
        sx={{
          pt: 0,
        }}
      >
        <Content />
      </CardContent>
    </Card>
  );
}
export default observer(RightPanel);
