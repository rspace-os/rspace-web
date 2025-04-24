import React from "react";
import Button from "@mui/material/Button";
import { styled } from "@mui/material/styles";
import { doNotAwait } from "../../../util/Util";

/**
 * A simple button, styled for use in the gallery listing to load more items.
 */
export default styled(
  ({
    onClick,
    className,
  }: {
    onClick: () => Promise<void>;
    className: string;
  }) => (
    <Button onClick={doNotAwait(onClick)} className={className}>
      Load More
    </Button>
  )
)(() => ({
  marginBottom: "16px",
  marginTop: "8px",
  marginRight: "auto",
  paddingLeft: "32px",
  paddingRight: "32px",
}));
