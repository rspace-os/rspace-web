import React from "react";
import Alert from "@mui/material/Alert";
import { type ReadAccessLevel } from "../../stores/definitions/Record";
import { type Person } from "../../stores/definitions/Person";

/**
 * This component shows a pinned alert that explains to the user that they do
 * not have permission to view all of the details of a particular Record. If
 * they do have full access then nothing shown.
 */
export default function LimitedAccessAlert({
  readAccessLevel,
  owner,
  whatLabel,
}: {
  /**
   * The level of access that the user has to the Record in question.
   */
  readAccessLevel: ReadAccessLevel;

  /**
   * The owner of the Record in question.
   */
  owner: Person;

  /**
   * A label that describes the Record e.g.
   *   - "container"
   *   - "document"
   *   - "subsample, and parent sample"
   * MUST be all lowercase as it will be inserted into a sentence.
   */
  whatLabel: string;
}): React.ReactNode {
  if (readAccessLevel === "full") return null;
  return (
    <Alert severity="info">
      You do not have permission to see{" "}
      {readAccessLevel === "limited" ? "all" : "any"} of the details of this{" "}
      {whatLabel}.
      <br />
      To gain full access, please contact the owner, {owner.fullName}.
    </Alert>
  );
}
