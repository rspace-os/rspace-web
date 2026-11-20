import Box from "@mui/material/Box";
import type React from "react";
import { useTranslation } from "react-i18next";
import UserDetails from "@/components/UserDetails";
import { userDisplayName } from "./sampleRequest";

/**
 * A user's chip for a Requests table cell, or plain text for a deleted user. The grid track's
 * zero minimum stops the chip's nowrap label from setting the column's min-content width, so the
 * column prefers the full name but can shrink, and MUI's own ellipsis then truncates the chip.
 */
export default function RequestUserChip({
  person,
}: {
  person: { id: number; username: string; firstName: string | null; lastName: string | null };
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  if (person.firstName === null && person.lastName === null) return userDisplayName(person, t);
  return (
    <Box sx={{ display: "grid", gridTemplateColumns: "minmax(0, max-content)" }}>
      <UserDetails userId={person.id} fullName={userDisplayName(person, t)} position={["bottom", "right"]} />
    </Box>
  );
}
