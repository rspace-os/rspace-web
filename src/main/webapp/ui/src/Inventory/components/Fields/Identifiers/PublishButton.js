//@flow

import React, { type Node } from "react";
import { type Identifier } from "../../../../stores/definitions/Identifier";
import SubmitSpinnerButton from "../../../../components/SubmitSpinnerButton";
import { doNotAwait } from "../../../../util/Util";

export default function PublishButton({
  identifier,
  disabled,
}: {|
  identifier: Identifier,
  disabled?: boolean,
|}): Node {
  const [publishing, setPublishing] = React.useState(false);

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
            await identifier.republish();
          } else {
            await identifier.publish();
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
