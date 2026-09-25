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
