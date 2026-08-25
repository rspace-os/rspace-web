import { createRoot } from "react-dom/client";
import DBRepo from "./DBRepo";

document.addEventListener("DOMContentLoaded", () => {
  const domContainer = document.getElementById("tinymce-dbrepo");
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(<DBRepo />);
  }
});
