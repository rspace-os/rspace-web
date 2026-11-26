import { ThemeProvider } from "@mui/material/styles";
import { createRoot } from "react-dom/client";
import { MuiCssLayerProvider } from "@/components/MuiCssLayerProvider";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import GroupActivity from "@/my-rspace/directory/groups/GroupUserActivity";
import materialTheme from "@/theme";

const domContainer = document.getElementById("groupActivity");
const root = createRoot(domContainer as HTMLElement);
root.render(
  <I18nRoot namespaces={["common"]}>
    <MuiCssLayerProvider>
      <ThemeProvider theme={materialTheme}>
        <GroupActivity groupId={domContainer?.dataset.groupid as string} />
      </ThemeProvider>
    </MuiCssLayerProvider>
  </I18nRoot>,
);
