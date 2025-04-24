import React from "react";
import axios from "@/common/axios";
import * as Parsers from "../../util/parsers";

/**
 * This context is a performance optimisation. By storing the set of supported
 * file extensions in a context at the root of the application, the network
 * call to get this list need not be made by each and every component that uses
 * the information.
 */
export const OfficeOnlineContext: React.Context<{
  supportedExts: Set<string> | null;
}> = React.createContext({ supportedExts: null as Set<string> | null });

/**
 * For fetching metadata about the integration with Office Online.
 *
 * The Office Online integration is used to allow users to quickly edit
 * documents that are stored in the RSpace Gallery. The document can be opened
 * in a new tab in the relevant Microsoft product, and any saved changes are
 * propagated back to RSpace as a new version of that document.
 *
 * To check if Office Online is enabled, check the `msoffice.wopi.enabled`
 * depeloyment property with
 *   const officeOnlineEnabled = useDeploymentProperty("msoffice.wopy.enabled");
 */
export default function useOfficeOnline(): {
  /**
   * The set of file extensions supported by Office Online.
   */
  supportedExts: Set<string>;
} {
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
  const context = React.useContext(OfficeOnlineContext);

  React.useEffect(() => {
    if (context.supportedExts !== null) {
      setSupportedExts(context.supportedExts);
      return;
    }
    void axios.get<unknown>("/officeOnline/supportedExts").then(({ data }) => {
      Parsers.isObject(data)
        .flatMap(Parsers.isNotNull)
        .do((obj) => {
          const newSupportedExts: Set<string> = new Set(Object.keys(obj));
          setSupportedExts(newSupportedExts);
          context.supportedExts = newSupportedExts;
        });
    });
    // we should probably store the result in session storage
    // as it doesn't need to be loaded everytime this component is mounted
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - context will not meaningfully change
     */
  }, []);

  return { supportedExts };
}
