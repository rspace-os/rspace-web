import { createRoot } from "react-dom/client";
import { App } from "./App";
import LoaderCircular from "./components/LoadingCircular";
import {
  defaultApiV2SessionHandlers,
  SessionTokenProvider,
  setApiV2TokenProvider,
} from "./modules/common/api/v2/tokenProvider";
import I18nRoot from "./modules/common/i18n/I18nRoot";

// The feature-flag devtools make REST API v2 calls, which need the page's token provider.
setApiV2TokenProvider(new SessionTokenProvider(defaultApiV2SessionHandlers));

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  window.scrollTo(0, 1);

  if (domContainer && domContainer.dataset.reactRootMounted !== "true") {
    domContainer.dataset.reactRootMounted = "true";
    const root = createRoot(domContainer);
    root.render(
      <I18nRoot namespaces={["inventory", "common", "about"]} fallback={<LoaderCircular />}>
        <App canUseDevtools={domContainer.dataset.canUseDevtools === "true"} />
      </I18nRoot>,
    );
  }
});
