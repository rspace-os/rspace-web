import Box from "@mui/material/Box";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type React from "react";

export type HtmlXmlExportDetails = {
  maxLinkLevel: number;
  archiveType: "html" | "xml";
  description: string;
  allVersions: boolean;
};

export type HtmlXmlExportDetailsArgs = {
  exportDetails: HtmlXmlExportDetails;
  updateExportDetails: (
    key: keyof HtmlXmlExportDetails,
    value: HtmlXmlExportDetails[keyof HtmlXmlExportDetails],
  ) => void;
};

type HtmlXmlExportArgs = HtmlXmlExportDetailsArgs & {
  validations: {
    submitAttempt: boolean;
    inputValidations: {
      exportName: boolean;
    };
  };
};

export default function HtmlXmlExport({
  exportDetails: { maxLinkLevel, description },
  updateExportDetails,
}: HtmlXmlExportArgs): React.ReactNode {
  return (
    <Stack spacing={1}>
      <Box>
        <InputLabel htmlFor="maxLinkLevel">Should linked RSpace documents be included in export?</InputLabel>
        <Select
          variant="standard"
          value={maxLinkLevel}
          onChange={({ target: { value } }) => updateExportDetails("maxLinkLevel", value)}
          inputProps={{ id: "maxLinkLevel" }}
          data-test-id="include-links"
        >
          <MenuItem value={0} data-test-id="include-links-no">
            Don&apos;t include linked documents
          </MenuItem>
          <MenuItem value={1} data-test-id="include-links-1">
            Include linked documents to depth 1
          </MenuItem>
          <MenuItem value={2} data-test-id="include-links-2">
            Include linked documents to depth 2
          </MenuItem>
          <MenuItem value={3} data-test-id="include-links-3">
            Include linked documents to depth 3
          </MenuItem>
          <MenuItem value={4} data-test-id="include-links-infinity">
            Include linked documents to depth infinity
          </MenuItem>
        </Select>
      </Box>
      <em>Linked folders and notebooks are never included</em>
      <TextField
        variant="standard"
        fullWidth
        label="Export Description (optional)"
        multiline
        maxRows="4"
        value={description}
        onChange={({ target: { value } }) => updateExportDetails("description", value)}
        margin="normal"
        data-test-id="html-xml-description"
        slotProps={{
          htmlInput: { "aria-label": "Export Description (optional)" },
        }}
      />
    </Stack>
  );
}
