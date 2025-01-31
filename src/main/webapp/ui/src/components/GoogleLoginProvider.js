//@flow

// that checks the deployment property for cloud

import React, { type Node } from "react";
import * as FetchingData from "../util/fetchingData";
import * as Parsers from "../util/parsers";
import { useDeploymentProperty } from "../eln/useDeploymentProperty";

/**
 * On some servers, we allow users to authenticate via Google Login; a single
 * click button that logs the user in with their Google account. This component
 * is a simple script loader that will load the Google Login script so that
 * when the user logs out they disconnct their Google account.
 *
 * The google script that is loaded by this component attaches a global object,
 * `gapi`, that is used by `./AppBar/index.js` to log the user out of their
 * Google account.
 */
export default function GoogleLoginProvider(): Node {
  const cloud = useDeploymentProperty("deployment.cloud");

  React.useEffect(() => {
    const scriptId = "google-login-script";

    FetchingData.getSuccessValue(cloud)
      .flatMap(Parsers.isBoolean)
      .flatMap(Parsers.isTrue)
      .do(() => {
        if (!document.getElementById(scriptId)) {
          const loadScript = () => {
            return new Promise((resolve, reject) => {
              const script = document.createElement("script");
              script.src = "https://accounts.google.com/gsi/client";
              script.id = scriptId;
              script.async = true;
              script.onload = () => resolve();
              script.onerror = () =>
                reject(new Error("Failed to load google login script"));
              document.body?.appendChild(script);
            });
          };

          loadScript()
            .then(() => {
              // eslint-disable-next-line no-console
              console.log(
                "Script loaded successfully, logout will now log the user out of google provided login"
              );
            })
            .catch((error) => {
              console.error(
                "Error loading google login script, logout will not trigger a logout of user via google provided login:",
                error
              );
            });
        }
      });
  }, [cloud]);

  return null;
}
