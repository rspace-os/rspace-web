import { env } from "@/__tests__/e2e/env";

/** Drives the mock server's stand-in for a B2INST curator decision on `rid`. */
export async function setB2instReviewStatus(rid: string, status: "accepted" | "declined"): Promise<void> {
  const response = await fetch(`${env.mockBaseUrl}/__e2e/b2inst/review-status/${rid}`, {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ status }),
  });
  if (!response.ok) {
    throw new Error(`Could not set mock B2INST review status for ${rid}: ${response.status}`);
  }
}

/** The last on-save draft-update body the mock server received for `rid`, or null if none arrived. */
export async function getB2instDraftUpdate(rid: string): Promise<{ metadata?: { Name?: string } } | null> {
  const response = await fetch(`${env.mockBaseUrl}/__e2e/b2inst/draft-update/${rid}`);
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`Could not read mock B2INST draft update for ${rid}: ${response.status}`);
  }
  return response.json() as Promise<{ metadata?: { Name?: string } }>;
}
