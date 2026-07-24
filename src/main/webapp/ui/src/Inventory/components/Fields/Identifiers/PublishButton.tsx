import React from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinnerButton from "../../../../components/SubmitSpinnerButton";
import type { Identifier } from "../../../../stores/definitions/Identifier";
import useStores from "../../../../stores/use-stores";

type PublishButtonArgs = {
  identifier: Identifier;
  disabled?: boolean;
};

export default function PublishButton({ identifier, disabled }: PublishButtonArgs): React.ReactNode {
  const [publishing, setPublishing] = React.useState(false);
  const { t } = useTranslation("common");
  const { uiStore, trackingStore } = useStores();

  /*
   * if the identifier has already been published, i.e. it is findable
   * then the publish button becomes a republish button
   */
  const republish = identifier.state === "findable";

  return (
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
      disabled={publishing || disabled || !identifier.isValid}
      label={republish ? t("actions.republish") : t("actions.publish")}
    />
  );
}
