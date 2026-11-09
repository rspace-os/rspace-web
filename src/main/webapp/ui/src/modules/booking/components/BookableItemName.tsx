import { Link } from "@tanstack/react-router";

export function BookableItemName({ name, globalId }: { name: string; globalId: string }) {
  return (
    <Link
      to="/booking/bookable-items/$globalId/{-$tab}"
      params={{ globalId, tab: undefined }}
      className="inline-flex min-h-6 items-center rounded-sm hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
    >
      {name}
    </Link>
  );
}
