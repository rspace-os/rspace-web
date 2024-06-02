import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import React from "react";
import { ErrorReason } from "./Enums";

export default function ErrorView({ errorReason, errorMessage }) {
  return (
    <Alert severity="error">
      <AlertTitle>Error</AlertTitle>
      {errorReason === ErrorReason.NetworkError && (
        <>
          The Jove server at{" "}
          <a
            href={parent.tinymce.activeEditor.settings.jove_url}
            target="_blank"
            rel="noreferrer"
          >
            {parent.tinymce.activeEditor.settings.jove_url}
          </a>{" "}
          is down, or CORS for this server has not been configured properly. If
          you are responsible for setting up the Jove integration, open
          developer tools and have a look at the console and/or the network tab
          to find out what the issue is.
        </>
      )}
      {/* When Jove token is invalid Jove API responds with */}
      {errorReason === ErrorReason.Unauthorized && (
        <>Invalid Jove user or client token. Please re-connect to Jove.</>
      )}
      {errorReason === ErrorReason.Timeout && <>Request timed out.</>}
      {/* when a refresh token expires the Jove API responds with 400 response and 'invalid_grant' in the response message */}
      {errorReason === ErrorReason.BadRequest &&
        !errorMessage.includes("invalid_grant") && (
          <> There is a problem, please try again later </>
        )}
      {errorReason === ErrorReason.NotFound && (
        <>Unable to retrieve any relevant results.</>
      )}

      {errorReason === ErrorReason.UNKNOWN && (
        <>Unknown issue, please attempt to relogin to RSpace.</>
      )}
    </Alert>
  );
}
