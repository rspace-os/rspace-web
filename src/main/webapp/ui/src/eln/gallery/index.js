//@flow

import React from "react";
import { createRoot } from "react-dom/client";
import GalleryPicker from "./picker";
import ErrorBoundary from "../../components/ErrorBoundary";

window.addEventListener("load", () => {
  const domContainer = document.getElementById("app");
  if (domContainer) {
    window.scrollTo(0, 1);

    const root = createRoot(domContainer);
    root.render(
      <React.StrictMode>
        <ErrorBoundary>
          <GalleryPicker open={true} onClose={() => {}} />
        </ErrorBoundary>
      </React.StrictMode>
    );
  }
});
