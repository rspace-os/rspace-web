import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import { ErrorReason } from "./Enums";
import React from "react";
import PropTypes from "prop-types";

export default function ErrorView({ errorReason, errorMessage }) {
  return (
    <Alert severity="error">
      <AlertTitle>Error</AlertTitle>
      {errorReason === ErrorReason.NetworkError && (
        <>
          The Omero server at{" "}
          <a
            href={parent.tinymce.activeEditor.settings.omero_url}
            target="_blank"
            rel="noreferrer"
          >
            {" "}
            {parent.tinymce.activeEditor.settings.omero_url}
          </a>{" "}
          is down, or CORS for this server has not been configured properly. If
          you are responsible for setting up the Omero integration, open
          developer tools and have a look at the console and/or the network tab
          to find out what the issue is.
        </>
      )}
      {errorReason === ErrorReason.NotFound && (
        <>The requested data was not found on this instance of Omero</>
      )}
      {/* when user credentials expire on user session end, server responds with 401 */}
      {(errorReason === ErrorReason.Unauthorized ||
        errorMessage.includes("invalid_grant")) && (
        <>
          Your session with Omero has expired. Please re-connect to Omero on the
          Apps page.
        </>
      )}
      {errorReason === ErrorReason.Timeout && <>Request timed out.</>}
      {/* when a refresh token expires the omero API responds with 400 response and 'invalid_grant' in the response message */}
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
ErrorView.propTypes = {
  errorReason: PropTypes.string,
  errorMessage: PropTypes.string,
};
