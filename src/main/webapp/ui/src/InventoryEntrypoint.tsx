// Bundle sliced Roboto fonts for Inventory without an external font stylesheet.
import "@fontsource/roboto/300.css";
import "@fontsource/roboto/400.css";
import "@fontsource/roboto/500.css";
import "@fontsource/roboto/700.css";
import "@fontsource/roboto/400-italic.css";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import LoaderCircular from "./components/LoadingCircular";
import I18nRoot from "./modules/common/i18n/I18nRoot";

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  window.scrollTo(0, 1);

  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(
      <I18nRoot namespaces={["inventory", "common", "about"]} fallback={<LoaderCircular />}>
        <App />
      </I18nRoot>,
    );
  }
});
