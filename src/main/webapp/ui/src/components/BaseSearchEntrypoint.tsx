import { createRoot } from "react-dom/client";
import WrappedBaseSearch from "./BaseSearch";

function renderElements() {
  document.addEventListener("DOMContentLoaded", () => {
    const domContainers = document.getElementsByClassName("base-search");
    if (domContainers.length) {
      [].map.call(domContainers, (container) => {
        const htmlContainer = container as HTMLElement;
        const { dataset } = htmlContainer;
        const variant = dataset.variant === "outlined" || dataset.variant === "elevation" ? dataset.variant : undefined;
        const root = createRoot(container);
        root.render(
          <WrappedBaseSearch
            placeholder={dataset.placeholder ?? ""}
            onSubmit={dataset.onsubmit}
            elId={dataset.elid ?? "base-search"}
            variant={variant}
          />,
        );
      });
    }
  });
}

renderElements();
