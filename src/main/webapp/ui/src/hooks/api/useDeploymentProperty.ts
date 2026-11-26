import { useQuery } from "@tanstack/react-query";
import axios from "@/common/axios";
import type * as FetchingData from "../../util/fetchingData";
import { deploymentPropertyQueryClient, deploymentPropertyQueryKeys } from "./deploymentPropertyQueryClient";

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
 * Each property is fetched at most once per page load and shared by every
 * component and React island on the page, through
 * `deploymentPropertyQueryClient`. A failed request is retried the next time
 * a component using that property mounts. The error is shared too, so while
 * that retry is in flight every component using the property shows loading
 * again instead of the earlier error.
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
export function useDeploymentProperty(name: string): FetchingData.Fetched<unknown> {
  const query = useQuery(
    {
      queryKey: deploymentPropertyQueryKeys.property(name),
      // The abort signal is deliberately not passed to axios: if the last
      // consumer unmounts mid-flight, the request completes and fills the cache.
      queryFn: async () => {
        const { data } = await axios.get<unknown>(`/deploymentproperties/ajax/property`, {
          params: new URLSearchParams({ name }),
        });
        return data;
      },
      staleTime: Infinity,
      gcTime: Infinity,
      retry: false,
    },
    deploymentPropertyQueryClient,
  );
  if (query.isPending) return { tag: "loading" };
  if (query.isError) return { tag: "error", error: query.error.message };
  return { tag: "success", value: query.data };
}
