import type { RequestHandler } from "msw";
import { setupWorker } from "msw/browser";

const worker = setupWorker();
let started: ReturnType<typeof worker.start> | undefined;

/** Keep one worker active across stories; replace only the story's handlers. */
export async function startPrototypeMocks(handlers: RequestHandler[]) {
  started ??= worker.start({ onUnhandledFrame: "bypass", quiet: true });
  await started;
  worker.resetHandlers(...handlers);
  return () => worker.resetHandlers();
}
