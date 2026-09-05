/** @prototype Storybook-only UI exploration; not production-ready. */
import Chip, { type ChipProps } from "@mui/material/Chip";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { RequestStatus } from "./types";

const COLOURS: Record<RequestStatus, ChipProps["color"]> = {
  pending: "warning",
  // ponytail: "primary" (#00adef) fails axe contrast on white; default grey passes
  approved: "default",
  prepared: "info",
  fulfilled: "success",
  rejected: "error",
};

export function useRequestStatusLabel(): (status: RequestStatus) => string {
  const { t } = useTranslation("inventory");
  return (status) => t(`requests.status.${status}`);
}

export default function RequestStatusChipPrototype({ status }: { status: RequestStatus }): React.ReactNode {
  const label = useRequestStatusLabel();
  return <Chip size="small" color={COLOURS[status]} label={label(status)} />;
}
