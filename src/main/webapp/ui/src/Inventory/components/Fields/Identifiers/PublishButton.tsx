import React from "react";
import SubmitSpinnerButton from "../../../../components/SubmitSpinnerButton";
import type { Identifier } from "../../../../stores/definitions/Identifier";
import useStores from "../../../../stores/use-stores";
import { doNotAwait } from "../../../../util/Util";

type PublishButtonArgs = {
    identifier: Identifier;
    disabled?: boolean;
};

export default function PublishButton({ identifier, disabled }: PublishButtonArgs): React.ReactNode {
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
