import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardHeader from "@mui/material/CardHeader";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import { useTranslation } from "react-i18next";
import SearchContext from "../../../stores/contexts/Search";
import useStores from "../../../stores/use-stores";
import { menuIDs } from "../../../util/menuIDs";
import SearchView from "../../Search/SearchView";
import NameWithBadge from "../NameWithBadge";
import MoveInstructions from "./MoveInstructions";

const Title = observer(() => {
  const { moveStore, peopleStore } = useStores();
  const { t } = useTranslation("inventory");
  const activeResult = moveStore.activeResult;
  if (moveStore.loading || !activeResult) {
    return t("moveToTarget.loadingEllipsis");
  }
  if (activeResult.isWorkbench && activeResult.ownerLabel && peopleStore.currentUser?.bench?.globalId) {
    const ownerPrefix =
      activeResult.globalId === peopleStore.currentUser.bench.globalId
        ? t("moveToTarget.myBench")
        : t("moveToTarget.ownerBench", { owner: activeResult.ownerLabel });
    return ownerPrefix;
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
  const { t } = useTranslation("inventory");
  return (
    <Card elevation={0}>
      <CardHeader
        title={
          <>
            {t("moveToTarget.selectedDestination")} <Title />
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
