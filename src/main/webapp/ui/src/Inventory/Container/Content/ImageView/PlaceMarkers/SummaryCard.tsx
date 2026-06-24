import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import CardMedia from "@mui/material/CardMedia";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { Location } from "../../../../../stores/definitions/Container";
import InventoryBaseRecord from "../../../../../stores/models/InventoryBaseRecord";
import { preventEventBubbling } from "../../../../../util/Util";
import useNavigateHelpers from "../../../../useNavigateHelpers";
import NumberedLocation from "../NumberedLocation";

type ActionButtonArgs = {
  children: React.ReactNode;
  onClick: (event: React.MouseEvent) => void;
  disabled?: boolean;
};

const ActionButton = ({ children, onClick, disabled = false }: ActionButtonArgs) => (
  <Button color="primary" disabled={disabled} onClick={onClick} size="small" sx={{ pointerEvents: "initial" }}>
    {children}
  </Button>
);

type SummaryCardArgs = {
  editable?: boolean;
  fullWidth: boolean;
  location: Location;
  number: number;
  onClick?: () => void;
  onRemove: () => void;
  selected?: number;
};

function SummaryCard({
  editable = false,
  number,
  location,
  onRemove,
  selected,
  onClick,
  fullWidth = false,
}: SummaryCardArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { navigateToRecord } = useNavigateHelpers();

  const helperText = location.hasContent ? "" : t("container.content.placeMarkers.emptyLocationHelper");

  const hasImage = location.content instanceof InventoryBaseRecord && Boolean(location.content.image);

  return (
    <Card sx={{ display: "flex", width: fullWidth ? "100%" : 400 }} onClick={onClick} variant="outlined">
      <Grid container>
        <Grid size={hasImage ? 7 : 12}>
          <Stack sx={{ height: "100%" }}>
            <Box sx={{ flexGrow: 1 }}>
              <CardContent sx={{ flex: "1 0 auto", pb: "8px !important" }}>
                <Typography gutterBottom variant="h5" component="h2">
                  <NumberedLocation number={number} inline selected={selected === number} />
                  {location.name ?? <em>{t("container.content.placeMarkers.emptyLocation")}</em>}
                </Typography>
                <Typography variant="body2" color="textSecondary" component="em" gutterBottom>
                  {helperText}
                </Typography>
              </CardContent>
            </Box>
            <CardActions>
              {editable && !location.hasContent && (
                <ActionButton onClick={preventEventBubbling(onRemove)}>
                  {t("container.content.placeMarkers.actions.remove")}
                </ActionButton>
              )}
              {!editable && location.content && (
                <ActionButton
                  disabled={!location.hasContent}
                  onClick={(event: React.MouseEvent) => {
                    event.stopPropagation();
                    if (location.content) void navigateToRecord(location.content);
                  }}
                >
                  {t("container.content.placeMarkers.actions.open")}
                </ActionButton>
              )}
            </CardActions>
          </Stack>
        </Grid>
        {hasImage && (
          <Grid size={5}>
            <Box sx={{ p: 0.5 }}>
              <CardMedia
                component="img"
                height="140"
                sx={{
                  objectFit: "contain",
                  height: "initial",
                  borderRadius: "3px",
                  maxHeight: 150,
                  maxWidth: 150,
                }}
                src={location.content?.image ?? ""}
                alt={location.content?.name ?? t("container.content.placeMarkers.emptyLocation")}
              />
            </Box>
          </Grid>
        )}
      </Grid>
    </Card>
  );
}

export default observer(SummaryCard);
