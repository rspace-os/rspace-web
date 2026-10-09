import Box from "@mui/material/Box";
import Breadcrumbs from "@mui/material/Breadcrumbs";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Radio from "@mui/material/Radio";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import NoValue from "@/components/NoValue";
import RecordTypeIcon from "@/components/RecordTypeIcon";
import NavigateContext from "@/stores/contexts/Navigate";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import { type ApiSubSampleInfo, useSampleWithSubSamplesQuery } from "./queries";

type LoadState =
  | { status: "loading" }
  | { status: "restricted"; ownerName: string }
  | { status: "loaded"; subSamples: Array<ApiSubSampleInfo> };

/**
 * A chip showing a record's full name (not its Global ID), reusing the same
 * icon/navigation behaviour as the shared `GlobalId` chip. `GlobalId` always
 * labels itself with the Global ID, so that component can't be reused here.
 */
function NamedRecordChip({ globalId, name }: { globalId: string; name: string }): React.ReactNode {
  const record = new LinkableRecordFromGlobalId(globalId);
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  return (
    <Chip
      component="a"
      sx={(theme) => ({
        userSelect: "unset",
        pl: 1.5,
        height: theme.spacing(3),
        fontWeight: theme.typography.fontWeightRegular,
        "&:focus-visible": { outline: `2px solid ${theme.palette.primary.main}` },
      })}
      href={record.permalinkURL}
      label={name}
      icon={<RecordTypeIcon record={record} aria-hidden={true} />}
      clickable
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        navigate(record.permalinkURL);
      }}
      color="default"
    />
  );
}

/**
 * For each of a sample's subsamples, shows a chip for the subsample plus a
 * breadcrumb of the containers it is stored in, mirroring the format of the
 * "Location" field shown for Subsamples/Containers elsewhere in Inventory.
 * Fetched from the existing GET /samples/{id} endpoint rather than extending
 * the SampleRequest API.
 */
export default function RequestSampleLocations({
  sampleId,
  selectable = false,
  selectedSubsampleId = null,
  onSelectSubsample,
}: {
  sampleId: number;
  selectable?: boolean;
  selectedSubsampleId?: number | null;
  onSelectSubsample?: (subSample: { id: number; name: string }) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  // Shared with RequestDetailPanel's own call to the same hook (one cached fetch of GET
  // /samples/{id}, not two) - this reads the rest of the response that one only needs a count
  // from.
  const query = useSampleWithSubSamplesQuery(sampleId);
  const state: LoadState = query.isLoading
    ? { status: "loading" }
    : query.data?.subSamples === null
      ? { status: "restricted", ownerName: `${query.data.owner.firstName} ${query.data.owner.lastName}` }
      : // A fetch error degrades to "no subsamples" rather than surfacing an error state of its
        // own - there's nothing actionable for the user to do about it here, matching the previous
        // behaviour.
        { status: "loaded", subSamples: query.data?.subSamples ?? [] };

  if (state.status === "loading") {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
        <CircularProgress size="1em" />
        <Typography variant="body2">{t("requestsManagement.detail.fields.loadingLocations")}</Typography>
      </Stack>
    );
  }

  if (state.status === "restricted") {
    return (
      <Typography variant="body2">
        {t("requestsManagement.detail.fields.sampleLocationRestricted", { owner: state.ownerName })}
      </Typography>
    );
  }

  const { subSamples } = state;

  if (subSamples.length === 0) {
    return <NoValue label={t("requestsManagement.detail.fields.noSubsamples")} />;
  }

  return (
    <TableContainer>
      <Table size="small" stickyHeader>
        <TableHead>
          <TableRow>
            {selectable && <TableCell padding="checkbox" />}
            <TableCell>{t("requestsManagement.detail.fields.subsampleColumn")}</TableCell>
            <TableCell>{t("requestsManagement.detail.fields.locationColumn")}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {subSamples.map((subSample) => (
            <TableRow key={subSample.id}>
              {selectable && (
                <TableCell padding="checkbox">
                  <Radio
                    checked={selectedSubsampleId === subSample.id}
                    onChange={() => onSelectSubsample?.({ id: subSample.id, name: subSample.name })}
                    size="small"
                    slotProps={{
                      input: {
                        "aria-label": t("requestsManagement.detail.fields.selectSubsampleLabel", {
                          subsample: subSample.name,
                        }),
                      },
                    }}
                  />
                </TableCell>
              )}
              <TableCell>
                <NamedRecordChip globalId={subSample.globalId} name={subSample.name} />
              </TableCell>
              <TableCell>
                {(() => {
                  // A subsample kept directly on a user's bench has that bench (prefix "BE") as
                  // its top parent; benches aren't a normal Inventory record type and are never
                  // shown as a chip elsewhere in the app, so exclude them from the breadcrumb.
                  const containers = subSample.parentContainers.filter(
                    (container) => !container.globalId.startsWith("BE"),
                  );
                  if (containers.length === 0) {
                    return <NoValue label={t("requestsManagement.detail.fields.notInContainer")} />;
                  }
                  return (
                    <Breadcrumbs aria-label={t("breadcrumbs.label")}>
                      {containers.toReversed().map((container) => (
                        <Box key={container.id}>
                          <NamedRecordChip globalId={container.globalId} name={container.name} />
                        </Box>
                      ))}
                    </Breadcrumbs>
                  );
                })()}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
