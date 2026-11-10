import Box from "@mui/material/Box";
import FormControl from "@mui/material/FormControl";
import type React from "react";
import { useId } from "react";
import CustomTooltip from "@/components/CustomTooltip";
import { Heading } from "@/components/DynamicHeadingLevel";

export default function DetailField({
  label,
  tooltip,
  children,
}: {
  label: string;
  tooltip?: string;
  children: React.ReactNode;
}): React.ReactNode {
  const labelId = useId();
  const heading = (
    <Heading sx={{ mt: 0 }} id={labelId}>
      {label}
    </Heading>
  );
  return (
    <FormControl fullWidth role="group" aria-labelledby={labelId}>
      {tooltip ? <CustomTooltip title={tooltip}>{heading}</CustomTooltip> : heading}
      {/* break-word only splits a word that's too long to fit on its own line (e.g. a long
          global id), rather than break-all's every-line mid-word wrapping of ordinary text
          like the requester's note or the approver's comment. */}
      <Box sx={{ overflowWrap: "break-word" }}>{children}</Box>
    </FormControl>
  );
}
