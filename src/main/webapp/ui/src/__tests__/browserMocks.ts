import { isAxiosError } from "axios";
import { setupWorker } from "msw/browser";
import { galleryAppShellHandlers } from "./mocks/galleryMocks";
import { oauthTokenHandler } from "./mocks/oauthTokenMocks";
import { appShellHandlers } from "./mswAppShellHandlers";

// Each isolated test-file iframe owns its MSW client and runtime handlers.
export const worker = setupWorker(
  ...appShellHandlers(),
  oauthTokenHandler(),
  oauthTokenHandler(true),
  ...galleryAppShellHandlers(),
);

/**
 * Some components intentionally fire requests whose promises are not returned
 * to the test. If the component has already unmounted, those late 404s surface
 * as global unhandled rejections and fail Vitest even though the user-visible
 * behaviour under test has completed. Use this only for known fire-and-forget
 * URLs; all other unhandled rejections still fail the run.
 */
export function suppressFireAndForget404(matchers: ReadonlyArray<RegExp | string>): () => void {
  const listener = (event: PromiseRejectionEvent) => {
    const reason = event.reason;
    const url = isAxiosError(reason) && reason.response?.status === 404 ? (reason.config?.url ?? "") : "";
    const matches = matchers.some((matcher) =>
      typeof matcher === "string" ? url.includes(matcher) : matcher.test(url),
    );
    if (matches) event.preventDefault();
  };

  window.addEventListener("unhandledrejection", listener);
  return () => {
    window.removeEventListener("unhandledrejection", listener);
  };
}
