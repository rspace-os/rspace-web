import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { getErrorMessage } from "@/util/error";
import CustomTooltip from "../../../../components/CustomTooltip";
import { mkAlert } from "../../../../stores/contexts/Alert";
import type { Identifier } from "../../../../stores/definitions/Identifier";
import { B2INST_CLOSED_REVIEW_STATES } from "../../../../stores/definitions/Identifier";
import useStores from "../../../../stores/use-stores";

type RefreshButtonArgs = {
  identifier: Identifier;
  disabled?: boolean;
};

/**
 * Pulls the identifier's current review status from the provider (RSDEV-1260). Offered for a
 * B2INST identifier whenever its community review can still move, which is every state except the
 * three that close it without publishing. It used to be offered for "submitted" alone, plus an
 * accepted record that had not yet reported its Handle (Copilot review, PR 1066), and was widened
 * to accepted and then to draft on Nico's request (RSDEV-1326).
 *
 * The status of a B2INST record is decided outside RSpace at every point: a draft can be submitted
 * for review, or deleted, in the B2INST UI; a created review can be accepted by a curator; and an
 * accepted record's minted ePIC PID, landing page and record page can all still arrive or move.
 * None of that reaches RSpace any other way.
 */
function RefreshButton({ identifier, disabled }: RefreshButtonArgs): React.ReactNode {
  const [refreshing, setRefreshing] = React.useState(false);
  const { t } = useTranslation("inventory");
  const { uiStore } = useStores();

  /*
   * A review closed without publishing - declined, cancelled, expired - is terminal at B2INST, so
   * that is the whole of what has nothing left to pull. The provider check is what makes "draft"
   * safe to include: both providers use that state, and for DataCite the server makes no provider
   * call at all, since its state only ever changes through RSpace's own publish and retract. Every
   * IGSN draft would otherwise grow a button that reports success having done nothing.
   */
  const refreshable =
    identifier.doiType === "PIDINST_B2INST" && !B2INST_CLOSED_REVIEW_STATES.includes(identifier.state);

  /*
   * A linked identifier is a PID another party minted. RSpace holds no provider-side record of its
   * own for it, so refresh is refused with 422 exactly like publish and retract (ADR 0009), and
   * offering the action would only produce that error. It has to be withheld on the flag: an
   * imported PID is accepted and arrives with its Handle, so nothing else here excludes it.
   */
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
