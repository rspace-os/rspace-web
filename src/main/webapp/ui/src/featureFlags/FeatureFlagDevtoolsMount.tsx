import { CatchBoundary } from "@tanstack/react-router";
import React from "react";
import { useCurrentUserQuery } from "@/modules/common/queries/currentUser";

const FeatureFlagDevtools = React.lazy(() => import("./FeatureFlagDevtools"));

export default function FeatureFlagDevtoolsMount(): React.ReactNode {
  return (
    <CatchBoundary getResetKey={() => "feature-flag-devtools"} errorComponent={() => null}>
      <React.Suspense fallback={null}>
        <AuthorizedFeatureFlagDevtools />
      </React.Suspense>
    </CatchBoundary>
  );
}

function AuthorizedFeatureFlagDevtools(): React.ReactNode {
  const { data: currentUser } = useCurrentUserQuery();
  if (!currentUser.session.canUseDevtools) return null;
  return <FeatureFlagDevtools />;
}
