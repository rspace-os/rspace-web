import CalendarMonthIcon from "@mui/icons-material/CalendarMonth";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { FEATURE_FLAGS } from "@/featureFlags/generatedFeatureFlags";
import { useIsFeatureFlagEnabled } from "@/featureFlags/queries";
import {
  type BookingConfiguration,
  findBookingConfigurationByTarget,
} from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import { searchBookingTargets } from "@/modules/booking/pages/bookable-items/bookingConfigurationTargets";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";

type BookingActionProps = {
  globalId: string | null;
  isOwner: boolean;
};

type BookingCardState = "bookable" | "configured" | "disabled" | "archived" | "setup";

const cardText = {
  bookable: {
    title: "instrument.booking.configured.title",
    description: "instrument.booking.configured.description",
    action: "instrument.booking.configured.book",
  },
  configured: {
    title: "instrument.booking.configured.title",
    description: "instrument.booking.configured.description",
    action: "instrument.booking.configured.open",
  },
  disabled: {
    title: "instrument.booking.disabled.title",
    description: "instrument.booking.disabled.description",
    action: "instrument.booking.configured.open",
  },
  archived: {
    title: "instrument.booking.archived.title",
    description: "instrument.booking.archived.description",
    action: "instrument.booking.configured.open",
  },
  setup: {
    title: "instrument.booking.notConfigured.title",
    description: "instrument.booking.notConfigured.description",
    action: "instrument.booking.notConfigured.action",
  },
} as const;

function configuredState(configuration: BookingConfiguration): BookingCardState {
  if (configuration.state === "ARCHIVED") return "archived";
  if (!configuration.enabled) return "disabled";
  return configuration.capabilities.canCreateBooking ? "bookable" : "configured";
}

function EnabledBookingAction({ globalId, isOwner }: { globalId: string; isOwner: boolean }) {
  const { t } = useTranslation("inventory");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const configuration = useQuery({
    queryKey: ["api-v2", "booking-configurations", "inventory-action", globalId],
    queryFn: ({ signal }) => findBookingConfigurationByTarget(globalId, token, signal),
  });
  // Owners can always set an unconfigured instrument up; anyone else can if the Add form would offer it,
  // such as a sysadmin.
  const checkEligibility = configuration.isSuccess && configuration.data === null && !isOwner;
  const eligibleTargets = useQuery({
    queryKey: ["api-v2", "booking-configuration-targets", "route-target", globalId],
    queryFn: ({ signal }) => searchBookingTargets(globalId, token, signal),
    enabled: checkEligibility,
  });

  if (!configuration.isSuccess) return null;

  const current = configuration.data;
  const canSetUp =
    isOwner ||
    (eligibleTargets.data?.some((target) => target.globalId.toUpperCase() === globalId.toUpperCase()) ?? false);
  if (current === null && !canSetUp) return null;
  const state = current === null ? "setup" : configuredState(current);
  const text = cardText[state];
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack direction={{ xs: "column", sm: "row" }} sx={{ alignItems: { sm: "center" }, gap: 2 }}>
        <Box
          sx={(theme) => ({
            display: "grid",
            placeItems: "center",
            width: 44,
            height: 44,
            flex: "0 0 auto",
            borderRadius: "50%",
            bgcolor: theme.palette.record.instrument.lighter,
            color: theme.palette.record.instrument.bg,
          })}
        >
          <CalendarMonthIcon />
        </Box>
        <Box sx={{ minWidth: 0, flexGrow: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            {t(text.title)}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(text.description)}
          </Typography>
        </Box>
        <Button
          component="a"
          href={
            state === "bookable"
              ? `/booking/calendar/bookings/add?target=${encodeURIComponent(globalId)}`
              : state === "setup"
                ? `/booking/bookable-items/add?target=${encodeURIComponent(globalId)}`
                : `/booking/bookable-items/${encodeURIComponent(globalId)}`
          }
          variant="contained"
          color="callToAction"
          startIcon={<CalendarMonthIcon />}
          sx={{ flex: "0 0 auto", alignSelf: { xs: "stretch", sm: "center" } }}
        >
          {t(text.action)}
        </Button>
      </Stack>
    </Paper>
  );
}

function BookingActionWhenEnabled({ globalId, isOwner }: { globalId: string; isOwner: boolean }) {
  const bookingEnabled = useIsFeatureFlagEnabled(FEATURE_FLAGS.bookingEnabled);
  return bookingEnabled ? <EnabledBookingAction globalId={globalId} isOwner={isOwner} /> : null;
}

export default function BookingAction({ globalId, isOwner }: BookingActionProps) {
  return globalId ? <BookingActionWhenEnabled globalId={globalId} isOwner={isOwner} /> : null;
}
