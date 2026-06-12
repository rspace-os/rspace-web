import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import docLinks from "../../assets/DocLinks";
import { ErrorReason } from "./Enums";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function ErrorView({ errorReason }: { errorReason: any }) {
  return (
    <Alert severity="error">
      <AlertTitle>Error</AlertTitle>
      {errorReason === ErrorReason.NetworkError && <>Network Error. Please check your connections.</>}
      {errorReason === ErrorReason.APIVersion && <>Only version 2 of the PyRAT API is supported.</>}
      {errorReason === ErrorReason.Unauthorized && <>Invalid PyRAT user or client token.</>}
      {errorReason === ErrorReason.Timeout && <>Request timed out.</>}
      {errorReason === ErrorReason.BadRequest && <>Bad Request.</>}
      {errorReason === ErrorReason.Unknown && <>Unknown error</>}
    </Alert>
  );
}
