/*
 * This is a public page, so the user may not be authenticated. As such, this
 * module MUST NOT import anything from the global Inventory stores. See the
 * note at the top of ./IdentifierPublicPage.tsx and the README in this
 * directory.
 */

import { createRoot } from "react-dom/client";
import LoaderCircular from "@/components/LoadingCircular";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { IdentifierPublicPage } from "./IdentifierPublicPage";

window.addEventListener("load", (_e) => {
  const domContainer = document.getElementById("identifierPublicPage");
  const location = window.location;
  /**
   * expected path is `/public/inventory/${rsPublicId}`
   * rsPublicId is also included in the fetched response.data
   *
   */
  const rsPublicId = location.pathname.split("/")[3];
  if (domContainer) {
    const root = createRoot(domContainer);
    root.render(
      <MuiCssLayerProvider>
        <I18nRoot namespaces={["public"]} fallback={<LoaderCircular />}>
          <IdentifierPublicPage publicId={rsPublicId} />
        </I18nRoot>
      </MuiCssLayerProvider>,
    );
  }
});
