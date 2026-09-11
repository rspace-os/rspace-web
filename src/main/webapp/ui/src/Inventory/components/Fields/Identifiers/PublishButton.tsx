import React from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "../../../../components/CustomTooltip";
import SubmitSpinnerButton from "../../../../components/SubmitSpinnerButton";
import { type Identifier, isB2instReviewOver } from "../../../../stores/definitions/Identifier";
import useStores from "../../../../stores/use-stores";

type PublishButtonArgs = {
  identifier: Identifier;
  disabled?: boolean;
};

export default function PublishButton({ identifier, disabled }: PublishButtonArgs): React.ReactNode {
  const [publishing, setPublishing] = React.useState(false);
  const { t } = useTranslation(["common", "inventory"]);
  const { uiStore, trackingStore } = useStores();

  /*
   * if the identifier has already been published, i.e. it is findable
   * then the publish button becomes a republish button
   */
  const republish = identifier.state === "findable";

  /*
   * Publishing a PIDINST identifier submits the B2INST record to a community for curator review.
   * While that review is open the outcome rests with the curator, and re-submitting would be
   * rejected, so there is nothing useful for the user to do here. A "created" review is different:
   * it exists but was never submitted (the submit call was lost), and pressing Publish again is
   * exactly how it is driven forward, so it stays enabled.
   */
  const awaitingReview = identifier.state === "submitted";

  /*
   * A B2INST record whose community review is over can never be published or republished from
   * RSpace: an accepted submission is already published and B2INST has no retract operation, and a
   * declined, cancelled or expired review is closed. The button used to be shown disabled with an
   * explanatory tooltip; it is now not offered at all, because a control that can never do
   * anything is worse than no control (RSDEV-1326).
   */
  const publishedPidinst = identifier.doiType === "PIDINST_B2INST" && isB2instReviewOver(identifier.state);

  /*
   * A linked identifier is a PID another party minted, attached by an instrument import. RSpace
   * owns nothing on the provider side for it, so publish, retract and refresh are all refused with
   * 422 (ADR 0009) - whichever provider it came from. Offering the action would only produce that
   * error. Identifiers RSpace minted itself are unaffected, DataCite's Republish included.
   */
  if (identifier.linked || publishedPidinst) {
    return null;
  }

  const button = (
    <SubmitSpinnerButton
      size="small"
      loading={publishing}
      type="button"
      onClick={() => {
        void (async () => {
          try {
            setPublishing(true);
            if (republish) {
              await identifier.republish({
                addAlert: (...args) => uiStore.addAlert(...args),
              });
            } else {
              await identifier.publish({
                confirm: (...args) => uiStore.confirm(...args),
                addAlert: (...args) => uiStore.addAlert(...args),
                onPublished: identifier.doiType.startsWith("PIDINST")
                  ? () =>
                      trackingStore.trackEvent("user:publish:pidinst:inventory", {
                        type: identifier.doiType === "PIDINST_B2INST" ? "B2INST" : "DataCite",
                      })
                  : undefined,
              });
            }
          } finally {
            setPublishing(false);
          }
        })();
      }}
      disabled={publishing || disabled || !identifier.isValid || awaitingReview}
      label={republish ? t("common:actions.republish") : t("common:actions.publish")}
    />
  );

  if (awaitingReview) {
    return <CustomTooltip title={t("inventory:fields.identifiers.list.publishAwaitingReview")}>{button}</CustomTooltip>;
  }
  return button;
}
