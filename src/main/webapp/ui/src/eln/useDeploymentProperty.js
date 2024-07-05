//@flow strict

import React from "react";
import axios from "axios";
import * as FetchingData from "../util/fetchingData";

/**
 * The RSpace product has a wide variety of configuration options that allow
 * for system admins to adjust how the product works on a per-deployment basis.
 * This includes enabling functionality, providing credentials for
 * authenticating with third-party integrations, and much else besides.
 *
 * This custom hook provides a mechanism for the React-based UIs to adapt to
 * the value of a given deployment property. It makes a network request to get
 * the value when the component from which it is called is first rendered and
 * returns the value wrapped in a [`Fetched`]{@link ../util/fetchingData} type.
 *
 * @returns The current value of the deployment property wrapped in a
 *          `Fetched`. The wrapped value could be any JSON value so further
 *          parsing may be required. To get at the value, using some default
 *          value whilst the network request is in flight and in the case that
 *          an error response should be returned, call `getSuccessValue` and
 *          use the returned [`Result`'s]{@link ../util/result} `orElse`
 *          method.
 *
 * In the following example, the label will show "Example is not available"
 * - For a split second whilst the network call is made
 * - Should the response from the network call be an error (i.e. not 200)
 * - Should the response from the network call not be a boolean
 * - Should the response from the network call be the boolean `false`
 * As a result, the only time "Example Available" will be shown is when the
 * response from the network call is the boolean value `true`.
 *
 * @example
 *   const IntegrationLabel = () => {
 *     const exampleIntegrationAvailable = useDeploymentProperty("exampleIntegration.available");
 *     return (
 *       <span>
 *         {FetchingData.getSuccessValue(exampleIntegrationAvailable)
 *           .flatMap(Parsers.isBoolean)
 *           .orElse(false)
 *           ? "Example available"
 *           : "Example is not available"
 *         }
 *       </span>
 *     );
 *   }
 *
 * If you want to show a different value in each of these different scenarios
 * then consider this example
 *
 * @example
 *   const IntegrationLabel = () => {
 *     const exampleIntegrationAvailable = useDeploymentProperty("exampleIntegration.available");
 *     return (
 *       <span>
 *         {FetchingData.match(exampleIntegrationAvailable, {
 *           loading: () => "loading",
 *           error: (e) => `ERROR: ${e}`,
 *           success: (isAvailable) => {
 *             if(typeof isAvailable !== "boolean") return "ERROR: Invalid API response";
 *             if(isAvailable) return "Example available";
 *             return "Example is not available";
 *           },
 *         })}
 *       </span>
 *     );
 *   }
 */
export function useDeploymentProperty(
  name: string
): FetchingData.Fetched<mixed> {
  const [value, setValue] = React.useState<FetchingData.Fetched<mixed>>({
    tag: "loading",
  });
  const map = React.useContext(DeploymentPropertyContext);

  React.useEffect(() => {
    if (map.has(name)) {
      setValue({ tag: "success", value: map.get(name) });
      return;
    }
    void (async () => {
      try {
        const { data } = await axios.get<mixed>(
          `/deploymentproperties/ajax/property`,
          { params: new URLSearchParams({ name }) }
        );
        setValue({ tag: "success", value: data });
        map.set(name, data);
      } catch (error) {
        setValue({ tag: "error", error: error.message });
      }
    })();
    /*
     * Only fetching the value on first re-render is fine as deployment
     * properties can only be changed with a server restart.
     */
  }, []);

  return value;
}

/**
 * This context acts as a cache for the fetched deployment properties so that
 * they need not be fetched more than once each per page load. If the same
 * deployment property is used in multiple component, or in a single component
 * is that un-mounted and re-mounted, then only the first call to
 * useDeploymentProperty will trigger a network call.
 *
 * This context is not exported, and is only used in the custom hook above.
 * There is not need to instantiate it with the
 * DeploymentPropertyContext.Provider component; the default value of an empty
 * Map suffices as a page-wide cache.
 */
const DeploymentPropertyContext = React.createContext(new Map<string, mixed>());
