import { TanStackDevtools, type TanStackDevtoolsReactPlugin } from "@tanstack/react-devtools";
import FeatureFlagPanel from "./FeatureFlagPanel";

const plugins: TanStackDevtoolsReactPlugin[] = [
  {
    id: "rspace-feature-flags",
    name: "Feature Flags",
    render: (_element, { theme }) => <FeatureFlagPanel theme={theme} />,
  },
];

export default function FeatureFlagDevtools() {
  return <TanStackDevtools plugins={plugins} />;
}
