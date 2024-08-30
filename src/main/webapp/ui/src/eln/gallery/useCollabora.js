//@flow

import React from "react";
import axios from "axios";
import * as Parsers from "../../util/parsers";

/**
 * For fetching metadata about the integration with Collabora.
 *
 * The Collabora integration is used to allow users to quickly edit
 * documents that are stored in the RSpace Gallery. The document can be opened
 * in a new tab in the relevant LibreOffice product, and any saved changes are
 * propagated back to RSpace as a new version of that document.
 *
 * To check if Collabora is enabled, check the `collabora.wopi.enabled`
 * depeloyment property with
 *   const collaboraEnabled = useDeploymentProperty("collabora.wopi.enabled");
 */
export default function useCollabora(): {|
  /**
   * The set of file extensions supported by Collabora.
   */
  supportedExts: Set<string>,
|} {
  /*
   * Whilst loading the set, and in the event that the network request fails,
   * this set is empty so that when the UI code queries whether the selected
   * file's extension is in the set it will not find it and thus the "Edit"
   * button will not be shown. As such, we don't need to expose the complexity
   * of promises and FetchingData to the caller.
   */
  const [supportedExts, setSupportedExts] = React.useState<Set<string>>(
    new Set()
  );

  React.useEffect(() => {
    void axios.get<mixed>("/collaboraOnline/supportedExts").then(({ data }) => {
      Parsers.isObject(data)
        .flatMap(Parsers.isNotNull)
        .do((obj) => setSupportedExts(new Set(Object.keys(obj))));
    });
    // we should probably store the result in session storage
    // as it doesn't need to be loaded everytime this component is mounted
  }, []);

  return { supportedExts };
}
