//@flow

import React, { type Node } from "react";
import { type Identifier } from "../../../../stores/definitions/Identifier";
import SubmitSpinnerButton from "../../../../components/SubmitSpinnerButton";
import { doNotAwait } from "../../../../util/Util";
import useStores from "../../../../stores/use-stores";

export default function PublishButton({
  identifier,
  disabled,
}: {|
  identifier: Identifier,
  disabled?: boolean,
|}): Node {
  const [publishing, setPublishing] = React.useState(false);
  const { uiStore } = useStores();

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
      onClick={doNotAwait(async () => {
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
            });
          }
        } finally {
          setPublishing(false);
        }
      })}
      disabled={publishing || disabled || !identifier.isValid}
      label={republish ? "Republish" : "Publish"}
    />
  );
}
