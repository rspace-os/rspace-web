import Box from "@mui/material/Box";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";

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
  const { t } = useTranslation("workspace");
  return (
    <Stack spacing={1}>
      <Box>
        <InputLabel htmlFor="maxLinkLevel">{t("export.format.htmlXml.includeLinksLabel")}</InputLabel>
        <Select
          variant="standard"
          value={maxLinkLevel}
          onChange={({ target: { value } }) => updateExportDetails("maxLinkLevel", value)}
          inputProps={{ id: "maxLinkLevel" }}
          data-test-id="include-links"
        >
          <MenuItem value={0} data-test-id="include-links-no">
            {t("export.format.htmlXml.depthNone")}
          </MenuItem>
          <MenuItem value={1} data-test-id="include-links-1">
            {t("export.format.htmlXml.depthLevel", { level: 1 })}
          </MenuItem>
          <MenuItem value={2} data-test-id="include-links-2">
            {t("export.format.htmlXml.depthLevel", { level: 2 })}
          </MenuItem>
          <MenuItem value={3} data-test-id="include-links-3">
            {t("export.format.htmlXml.depthLevel", { level: 3 })}
          </MenuItem>
          <MenuItem value={4} data-test-id="include-links-infinity">
            {t("export.format.htmlXml.depthInfinity")}
          </MenuItem>
        </Select>
      </Box>
      <em>{t("export.format.htmlXml.foldersNote")}</em>
      <TextField
        variant="standard"
        fullWidth
        label={t("export.format.htmlXml.descriptionLabel")}
        multiline
        maxRows="4"
        value={description}
        onChange={({ target: { value } }) => updateExportDetails("description", value)}
        margin="normal"
        data-test-id="html-xml-description"
        slotProps={{
          htmlInput: { "aria-label": t("export.format.htmlXml.descriptionLabel") },
        }}
      />
    </Stack>
  );
}
