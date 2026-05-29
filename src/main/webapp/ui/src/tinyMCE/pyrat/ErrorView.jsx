import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { ErrorReason } from "./Enums";
import React from "react";
import docLinks from "../../assets/DocLinks";

export default function ErrorView({ errorReason }) {
  return (
    <Alert severity="error">
      <AlertTitle>Error</AlertTitle>
      {errorReason === ErrorReason.NetworkError && (
        <>Network Error. Please check your connections.</>
      )}
      {errorReason === ErrorReason.APIVersion && (
        <>Only version 2 of the PyRAT API is supported.</>
      )}
      {errorReason === ErrorReason.Unauthorized && (
        <>Invalid PyRAT user or client token.</>
      )}
      {errorReason === ErrorReason.Timeout && <>Request timed out.</>}
      {errorReason === ErrorReason.BadRequest && <>Bad Request.</>}
      {errorReason === ErrorReason.Unknown && <>Unknown error</>}
    </Alert>
  );
}
