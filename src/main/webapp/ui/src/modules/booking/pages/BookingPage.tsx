import { Outlet } from "@tanstack/react-router";

export default function BookingPage() {
  return (
    <div className="mx-auto w-full max-w-7xl">
      <Outlet />
    </div>
  );
}
