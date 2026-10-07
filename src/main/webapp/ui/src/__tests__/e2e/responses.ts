import type { APIResponse, Page, Response } from "@playwright/test";

type HttpMethod = "GET" | "POST" | "DELETE";

/**
 * Runs `trigger`, waits for the matching response and returns it.
 * Throws on an HTTP error, so a failed request is reported as such instead of as a later timeout.
 */
export async function responseTo(
  page: Page,
  method: HttpMethod,
  matchesUrl: (url: URL) => boolean,
  trigger: () => Promise<void>,
): Promise<Response> {
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === method && matchesUrl(new URL(response.url())),
  );
  await trigger();
  const response = await responsePromise;
  await assertOk(response, method);
  return response;
}

/**
 * Reads the body only on failure. Reading every successful body failed on WebKit in the iRODS login
 * flow with "Response body is not available for a response that was navigated away from".
 */
export async function assertOk(response: Response | APIResponse, action: string): Promise<void> {
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(
      `${action} ${new URL(response.url()).pathname} failed: HTTP ${response.status()} ${body.slice(0, 300)}`,
    );
  }
}

/** Legacy ajax endpoints report a rejected request as HTTP 200 with `success: false`. */
export function assertAjaxSuccess(body: string, action: string): void {
  let parsed: { success?: boolean };
  try {
    parsed = JSON.parse(body) as { success?: boolean };
  } catch {
    throw new Error(`${action} did not return JSON: ${body.slice(0, 300)}`);
  }
  if (parsed.success !== true) {
    throw new Error(`${action} was rejected: ${body}`);
  }
}
