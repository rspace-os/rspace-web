import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router";
import LoaderCircular from "../../components/LoadingCircular";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import { Gallery } from "./index";

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <I18nRoot namespaces={["gallery", "common", "about", "inventory"]} fallback={<LoaderCircular />}>
          <BrowserRouter>
            <Gallery />
          </BrowserRouter>
        </I18nRoot>
      </React.StrictMode>,
    );
  }
});
