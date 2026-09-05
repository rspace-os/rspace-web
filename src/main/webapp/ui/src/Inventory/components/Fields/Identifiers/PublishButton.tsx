import React from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "../../../../components/CustomTooltip";
import SubmitSpinnerButton from "../../../../components/SubmitSpinnerButton";
import type { Identifier } from "../../../../stores/definitions/Identifier";
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
   * An accepted B2INST submission is published, and B2INST has no retract operation, so neither
   * Publish nor Republish (which retracts first) can ever succeed for it.
   */
  const publishedPidinst = identifier.doiType === "PIDINST_B2INST" && identifier.state === "accepted";

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
      disabled={publishing || disabled || !identifier.isValid || awaitingReview || publishedPidinst}
      label={republish ? t("common:actions.republish") : t("common:actions.publish")}
    />
  );

  if (awaitingReview) {
    return <CustomTooltip title={t("inventory:fields.identifiers.list.publishAwaitingReview")}>{button}</CustomTooltip>;
  }
  if (publishedPidinst) {
    return (
      <CustomTooltip title={t("inventory:fields.identifiers.list.publishPidinstPublished")}>{button}</CustomTooltip>
    );
  }
  return button;
}
