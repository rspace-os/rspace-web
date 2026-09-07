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
import { findBookingConfigurationByTarget } from "@/modules/booking/pages/bookable-items/bookingConfiguration";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";

type BookingActionProps = {
  globalId: string | null;
  isOwner: boolean;
};

function EnabledBookingAction({ globalId, isOwner }: { globalId: string; isOwner: boolean }) {
  const { t } = useTranslation("inventory");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const configuration = useQuery({
    queryKey: ["api-v2", "booking-configurations", "inventory-action", globalId],
    queryFn: ({ signal }) => findBookingConfigurationByTarget(globalId, token, signal),
  });

  if (!configuration.isSuccess) return null;

  const current = configuration.data;
  const configured = current !== null;
  const canBook = current?.state === "ACTIVE" && current.enabled && current.capabilities.canCreateBooking;
  if (!configured && !isOwner) return null;
  const action = configured ? (canBook ? "book" : "open") : "setup";
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
            {t(configured ? "instrument.booking.configured.title" : "instrument.booking.notConfigured.title")}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t(
              configured ? "instrument.booking.configured.description" : "instrument.booking.notConfigured.description",
            )}
          </Typography>
        </Box>
        <Button
          component="a"
          href={
            action === "book"
              ? `/booking/calendar/bookings/add?target=${encodeURIComponent(globalId)}`
              : action === "open"
                ? `/booking/bookable-items/${encodeURIComponent(globalId)}`
                : `/booking/bookable-items/add?target=${encodeURIComponent(globalId)}`
          }
          variant="contained"
          color="callToAction"
          startIcon={<CalendarMonthIcon />}
          sx={{ flex: "0 0 auto", alignSelf: { xs: "stretch", sm: "center" } }}
        >
          {t(
            action === "book"
              ? "instrument.booking.configured.book"
              : action === "open"
                ? "instrument.booking.configured.open"
                : "instrument.booking.notConfigured.action",
          )}
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
