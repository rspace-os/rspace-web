import React from "react";
import Grid from "@mui/material/Grid";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import InputLabel from "@mui/material/InputLabel";
import TextField from "@mui/material/TextField";

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
    value: HtmlXmlExportDetails[keyof HtmlXmlExportDetails]
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
    <Grid container direction="column" spacing={1}>
      <Grid item>
        <InputLabel htmlFor="maxLinkLevel">
          Should linked RSpace documents be included in export?
        </InputLabel>
        <Select
          variant="standard"
          value={maxLinkLevel}
          onChange={({ target: { value } }) =>
            updateExportDetails("maxLinkLevel", value)
          }
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
      </Grid>
      <Grid item>
        <em>Linked folders and notebooks are never included</em>
      </Grid>
      <Grid item>
        <TextField
          variant="standard"
          fullWidth
          label="Export Description (optional)"
          inputProps={{ "aria-label": "Export Description (optional)" }}
          multiline
          maxRows="4"
          value={description}
          onChange={({ target: { value } }) =>
            updateExportDetails("description", value)
          }
          margin="normal"
          data-test-id="html-xml-description"
        />
      </Grid>
    </Grid>
  );
}
