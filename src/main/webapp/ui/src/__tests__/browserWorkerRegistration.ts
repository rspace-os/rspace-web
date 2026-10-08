import { server } from "vitest/browser";

// Firefox can report a controller on a replacement test iframe
// while sending its requests straight to the network. Re-registering MSW runs
// its activation handler's clients.claim() for the incoming iframe. Run this
// setup file before browserSetup.ts, once per file, not after individual tests.
if (server.browser === "firefox") {
  const workerUrl = new URL("/mockServiceWorker.js", window.location.href).href;
  const registration = await navigator.serviceWorker.getRegistration(workerUrl);
  if (registration?.active?.scriptURL === workerUrl) {
    await registration.unregister();
  }
}
