import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { getErrorMessage } from "@/util/error";
import CustomTooltip from "../../../../components/CustomTooltip";
import { mkAlert } from "../../../../stores/contexts/Alert";
import type { Identifier, PublishingState } from "../../../../stores/definitions/Identifier";
import useStores from "../../../../stores/use-stores";

type RefreshButtonArgs = {
  identifier: Identifier;
  disabled?: boolean;
};

/** B2INST states whose record can still change at the provider, so a re-read may return something new. */
const REFRESHABLE_B2INST_STATES: ReadonlyArray<PublishingState> = ["draft", "created", "submitted", "accepted"];

/** Pulls the identifier's current review status from the provider (RSDEV-1260). */
function RefreshButton({ identifier, disabled }: RefreshButtonArgs): React.ReactNode {
  const [refreshing, setRefreshing] = React.useState(false);
  const { t } = useTranslation("inventory");
  const { uiStore } = useStores();

  /*
   * B2INST only: refreshIdentifier makes no provider call for a DataCite identifier, whose state
   * changes solely through RSpace's own publish and retract. "draft" is a state both providers
   * use, so keying on the state alone would put a do-nothing button on every IGSN draft.
   */
  const refreshable = identifier.doiType === "PIDINST_B2INST" && REFRESHABLE_B2INST_STATES.includes(identifier.state);

  // the server refuses publish, retract and refresh for a linked identifier with 422 (ADR 0009)
  if (identifier.linked || !refreshable) return null;

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
