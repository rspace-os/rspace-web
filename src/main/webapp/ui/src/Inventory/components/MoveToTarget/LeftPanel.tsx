import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import { useTranslation } from "react-i18next";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import ContainerModel from "../../../stores/models/ContainerModel";
import useStores from "../../../stores/use-stores";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import InventoryPicker from "../Picker/Picker";

type LeftPanelArgs = Record<string, never>;

function LeftPanel(_: LeftPanelArgs) {
  const { t } = useTranslation("inventory");
  const { moveStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();

  const selectionHelpText = () => {
    if (!(moveStore.search?.activeResult instanceof ContainerModel)) return null;
    if (moveStore.search.activeResult.isWorkbench && moveStore.search.fetcher.parentIsBench) {
      if (isSingleColumnLayout) {
        return t("moveToTarget.selectionHelp.singleColumnBench");
      }
      return t("moveToTarget.selectionHelp.wideBench");
    }
    return null;
  };

  return (
    <>
      {moveStore.search ? (
        <InventoryPicker
          search={moveStore.search}
          onAddition={() => {
            /*
             * Do nothing; having passed moveStore.search, its activeResult
             * will automatically be the picked destination
             */
          }}
          header={
            <Typography variant="h6" component="h3">
              {t("moveToTarget.pickDestination")}{" "}
              <HelpLinkIcon link={helpDocsArticleUrl("moving")} title={t("moveToTarget.helpTitle")} />
            </Typography>
          }
          selectionHelpText={selectionHelpText()}
          testId="movePicker"
        />
      ) : (
        <Typography variant="h5">{t("moveToTarget.loading")}</Typography>
      )}
    </>
  );
}

export default observer(LeftPanel);
