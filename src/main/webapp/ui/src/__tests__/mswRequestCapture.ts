import { http } from "msw";
import { server } from "@/__tests__/mswServer";

type Method = "get" | "post" | "put" | "delete";

/**
 * Registers a one-off MSW handler for `method`/`path` and returns the array
 * it will push each matching request into, cloned so callers can still read
 * the body (e.g. `requests[0].json()`) after the resolver has run.
 */
export function captureRequests(method: Method, path: string, response: () => Response): Request[] {
  const requests: Request[] = [];
  server.use(
    http[method](path, ({ request }) => {
      requests.push(request.clone());
      return response();
    }),
  );
  return requests;
}
