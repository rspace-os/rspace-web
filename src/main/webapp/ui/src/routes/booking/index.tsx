import { createFileRoute, redirect } from "@tanstack/react-router";

export const Route = createFileRoute("/booking/")({
  beforeLoad: () => {
    throw redirect({ to: "/booking/preferences", replace: true });
  },
});
