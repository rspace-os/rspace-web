import React from "react";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";

const FeatureFlagDevtools = React.lazy(() => import("./FeatureFlagDevtools"));

export default function FeatureFlagDevtoolsMount(): React.ReactNode {
  const { data: currentUser } = useCurrentUserQuery();
  if (!currentUser.session.canUseDevtools) return null;
  return (
    <React.Suspense fallback={null}>
      <FeatureFlagDevtools />
    </React.Suspense>
  );
}
