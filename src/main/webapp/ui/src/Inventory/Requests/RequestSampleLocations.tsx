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
import { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import NoValue from "@/components/NoValue";
import RecordTypeIcon from "@/components/RecordTypeIcon";
import NavigateContext from "@/stores/contexts/Navigate";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import ApiService from "../../common/InvApiService";

type ApiContainerInfo = { id: number; globalId: string; name: string };
type ApiSubSampleInfo = { id: number; globalId: string; name: string; parentContainers: Array<ApiContainerInfo> };
// The backend nulls `subSamples` (rather than omitting/emptying it) when the viewer only has
// limited/public read access to the sample, so a null here specifically means "restricted",
// not "no subsamples".
type ApiSampleWithSubSamples = {
  subSamples: Array<ApiSubSampleInfo> | null;
  owner: { firstName: string; lastName: string };
};

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
  const [state, setState] = useState<LoadState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    setState({ status: "loading" });
    ApiService.get<ApiSampleWithSubSamples>("samples", sampleId)
      .then(({ data }) => {
        if (cancelled) return;
        if (data.subSamples === null) {
          setState({ status: "restricted", ownerName: `${data.owner.firstName} ${data.owner.lastName}` });
        } else {
          setState({ status: "loaded", subSamples: data.subSamples });
        }
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        console.error("Failed to fetch subsample locations", error);
        setState({ status: "loaded", subSamples: [] });
      });
    return () => {
      cancelled = true;
    };
  }, [sampleId]);

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
