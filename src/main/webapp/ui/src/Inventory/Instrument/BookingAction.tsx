import CalendarMonthIcon from "@mui/icons-material/CalendarMonth";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useTranslation } from "react-i18next";
import { FEATURE_FLAGS } from "@/featureFlags/generatedFeatureFlags";
import { useIsFeatureFlagEnabled } from "@/featureFlags/queries";
import { useSelectedBookableItemAvailability } from "@/modules/booking/pages/bookable-items/bookableItemAvailability";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";

type BookingActionProps = {
  globalId: string | null;
  isOwner: boolean;
};

function EnabledBookingAction({ globalId }: { globalId: string }) {
  const { t } = useTranslation("inventory");
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  const availability = useSelectedBookableItemAvailability(globalId, token);

  if (!availability.isSuccess) return null;

  const configured = availability.data !== null;
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
            configured
              ? `/booking/calendar/bookings/add?target=${encodeURIComponent(globalId)}`
              : "/booking/config/bookable-items/add"
          }
          variant="contained"
          startIcon={<CalendarMonthIcon />}
          sx={{ flex: "0 0 auto", alignSelf: { xs: "stretch", sm: "center" } }}
        >
          {t(configured ? "instrument.booking.configured.action" : "instrument.booking.notConfigured.action")}
        </Button>
      </Stack>
    </Paper>
  );
}

function OwnerBookingAction({ globalId }: { globalId: string }) {
  const bookingEnabled = useIsFeatureFlagEnabled(FEATURE_FLAGS.bookingEnabled);
  return bookingEnabled ? <EnabledBookingAction globalId={globalId} /> : null;
}

export default function BookingAction({ globalId, isOwner }: BookingActionProps) {
  return isOwner && globalId ? <OwnerBookingAction globalId={globalId} /> : null;
}
