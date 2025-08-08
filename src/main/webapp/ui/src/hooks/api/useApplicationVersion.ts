import React from "react";
import axios from "../../common/axios";
import * as FetchingData from "../../util/fetchingData";

/**
 * Custom hook to fetch the application version from the /public/version endpoint.
 *
 * @returns a Fetched object containing the version string.
 */
function useApplicationVersion(): FetchingData.Fetched<string> {
  const [version, setVersion] = React.useState<FetchingData.Fetched<string>>({
    tag: "loading",
  });

  React.useEffect(() => {
    const fetchVersion = async () => {
      try {
        const response = await axios.get<string>("/public/version");
        setVersion({ tag: "success", value: response.data });
      } catch (error) {
        setVersion({
          tag: "error",
          error: error instanceof Error ? error.message : "Unknown error",
        });
      }
    };

    void fetchVersion();
  }, []);

  return version;
}

export default useApplicationVersion;
