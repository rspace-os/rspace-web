import { env } from "@/__tests__/e2e/env";

/** How many metadata PUTs the mock DataCite server has received for `doi`. */
export async function getDataciteUpdateCount(doi: string): Promise<number> {
  const response = await fetch(`${env.mockBaseUrl}/__e2e/datacite/put-count/${doi}`);
  if (!response.ok) {
    throw new Error(`Could not read mock DataCite update count for ${doi}: ${response.status}`);
  }
  const { count } = (await response.json()) as { count: number };
  return count;
}
