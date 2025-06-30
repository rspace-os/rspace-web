import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { ErrorReason } from "./Enums";
import React from "react";

export default function ErrorView({
  errorReason,
  errorMessage,
}: {
  errorReason: (typeof ErrorReason)[keyof typeof ErrorReason];
  errorMessage: string;
}): React.ReactNode {
  return (
    <Alert severity="error">
      <AlertTitle>Error</AlertTitle>
      {errorReason === ErrorReason.NetworkError && (
        <>
          The Clustermarket server at{" "}
          <a
            // @ts-expect-error -- tinymce is defined in the parent window
            href={parent.tinymce.activeEditor.settings.clustermarket_url}
            target="_blank"
            rel="noreferrer"
          >
            {" "}
            {/* @ts-expect-error -- tinymce is defined in the parent window */}
            {parent.tinymce.activeEditor.settings.clustermarket_url}
          </a>{" "}
          is down, or CORS for this server has not been configured properly. If
          you are responsible for setting up the Clustermarket integration, open
          developer tools and have a look at the console and/or the network tab
          to find out what the issue is.
        </>
      )}
      {errorReason === ErrorReason.NotFound && (
        <>
          Please contact an Admin: Clustermarket returned HTTP status 404. Is
          Clustermarket endpoint set correctly?
        </>
      )}
      {/* when an OAuth token expires the Clustermarket API responds with 401 response.
        When a refresh token expires the Clustermarket API responds with 400 response and 'invalid_grant' in the response message */}
      {(errorReason === ErrorReason.Unauthorized ||
        errorMessage.includes("invalid_grant")) && (
        <>
          Invalid Clustermarket user or client token. Please re-connect to
          Clustermarket.
        </>
      )}
      {errorReason === ErrorReason.Timeout && <>Request timed out.</>}
      {/* when a refresh token expires the Clustermarket API responds with 400 response and 'invalid_grant' in the response message */}
      {errorReason === ErrorReason.BadRequest &&
        !errorMessage.includes("invalid_grant") && (
          <> There is a problem, please try again later </>
        )}
      {errorReason === ErrorReason.UNKNOWN && (
        <>Unknown issue, please attempt to relogin to RSpace.</>
      )}
    </Alert>
  );
}
