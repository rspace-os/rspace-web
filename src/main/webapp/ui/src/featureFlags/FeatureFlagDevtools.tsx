import { TanStackDevtools, type TanStackDevtoolsReactPlugin } from "@tanstack/react-devtools";
import { useTranslation } from "react-i18next";
import FeatureFlagPanel from "./FeatureFlagPanel";

export default function FeatureFlagDevtools() {
  const { t } = useTranslation("common");
  const plugins: TanStackDevtoolsReactPlugin[] = [
    {
      id: "rspace-feature-flags",
      name: t("featureFlags.title"),
      render: (_element, { theme }) => <FeatureFlagPanel theme={theme} />,
    },
  ];
  return <TanStackDevtools plugins={plugins} />;
}
