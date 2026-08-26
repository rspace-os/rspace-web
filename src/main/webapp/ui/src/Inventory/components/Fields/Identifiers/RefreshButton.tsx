import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { getErrorMessage } from "@/util/error";
import CustomTooltip from "../../../../components/CustomTooltip";
import { mkAlert } from "../../../../stores/contexts/Alert";
import type { Identifier } from "../../../../stores/definitions/Identifier";
import useStores from "../../../../stores/use-stores";

type RefreshButtonArgs = {
  identifier: Identifier;
  disabled?: boolean;
};

/**
 * Pulls the identifier's current review status from the provider (RSDEV-1260). Only rendered
 * while the B2INST community review is open ("submitted"), when the outcome is decided outside
 * RSpace and this is the one useful action.
 */
function RefreshButton({ identifier, disabled }: RefreshButtonArgs): React.ReactNode {
  const [refreshing, setRefreshing] = React.useState(false);
  const { t } = useTranslation("inventory");
  const { uiStore } = useStores();

  if (identifier.state !== "submitted") return null;

  return (
    <CustomTooltip title={t("fields.identifiers.list.tooltips.refresh")}>
      <Button
        color="callToAction"
        variant="outlined"
        size="small"
        /*
         * A refresh makes up to three B2INST calls at a 30s read timeout each, so a greyed-out
         * button alone leaves the user with no sign that anything is happening.
         */
        loading={refreshing}
        disabled={refreshing || disabled}
        onClick={() => {
          void (async () => {
            try {
              setRefreshing(true);
              await identifier.refresh({
                addAlert: (...args) => uiStore.addAlert(...args),
              });
            } catch (error) {
              /*
               * refresh() reports provider failures itself, but its own preconditions (no API
               * service, no identifier id) throw before that handling. Without this they would
               * escape the voided promise as an unhandled rejection and the user would see the
               * button flicker and nothing else.
               */
              uiStore.addAlert(
                mkAlert({
                  title: t("identifierModel.alerts.refreshFailed"),
                  message: getErrorMessage(error, t("errors.unknownReason")),
                  variant: "error",
                }),
              );
            } finally {
              setRefreshing(false);
            }
          })();
        }}
      >
        {t("fields.identifiers.list.refresh")}
      </Button>
    </CustomTooltip>
  );
}

export default observer(RefreshButton);
