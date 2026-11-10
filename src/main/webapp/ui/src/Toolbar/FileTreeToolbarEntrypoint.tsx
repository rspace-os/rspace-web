import Skeleton from "@mui/material/Skeleton";
import { createRoot } from "react-dom/client";
import I18nRoot from "../modules/common/i18n/I18nRoot";
import FileTreeToolbar from "./FileTreeToolbar";

const domContainer = document.getElementById("fileTreeToolbar");
if (!domContainer) throw new Error("File tree toolbar container is missing");
const root = createRoot(domContainer);
root.render(
  // content() uses i18n.t() directly, not the hook, so I18nRoot must gate this whole render or labels freeze as raw keys.
  <I18nRoot namespaces={["common"]} fallback={<Skeleton variant="rectangular" height={64} />}>
    <FileTreeToolbar />
  </I18nRoot>,
);
