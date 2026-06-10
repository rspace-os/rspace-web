import React from "react";
import Button from "@mui/material/Button";
import { doNotAwait } from "../../../util/Util";

/**
 * A simple button, styled for use in the gallery listing to load more items.
 */
export default function LoadMoreButton({
  onClick,
}: {
  onClick: () => Promise<void>;
}): React.ReactNode {
  return (
    <Button
      onClick={doNotAwait(onClick)}
      sx={{
        marginBottom: "16px",
        marginTop: "8px",
        marginRight: "auto",
        paddingLeft: "32px",
        paddingRight: "32px",
      }}
    >
      Load More
    </Button>
  );
}
