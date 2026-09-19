import CloseIcon from "@mui/icons-material/Close";
import SearchOutlinedIcon from "@mui/icons-material/SearchOutlined";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import Paper from "@mui/material/Paper";
import TextField, { textFieldClasses } from "@mui/material/TextField";
import type React from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "@/components/CustomTooltip";

/**
 * A search box matching the styling of the main Inventory search bar. Not
 * wired up to any filtering yet; that will come once the /sampleRequests
 * endpoint supports a free-text query.
 */
export default function RequestsSearchbar({
  value,
  onChange,
}: {
  value: string;
  onChange: (query: string) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");

  return (
    <Box sx={{ flexGrow: 1 }}>
      <Box
        sx={{
          width: "100%",
          [`& .${textFieldClasses.root}`]: {
            width: "100%",
            [`& .${outlinedInputClasses.root}`]: {
              "& input:focus, & input:hover": {
                backgroundColor: "transparent !important",
              },
            },
            [`& .${outlinedInputClasses.input}`]: {
              padding: "8px 0 8px 0",
            },
          },
        }}
      >
        <Paper sx={{ display: "flex", alignItems: "center", width: "100%" }} elevation={0}>
          <TextField
            fullWidth
            placeholder={t("search.controls.searchbar.search")}
            value={value}
            onChange={({ target: { value: newValue } }) => onChange(newValue)}
            sx={{ flexGrow: 1 }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <IconButton aria-label={t("search.controls.searchbar.search")} size="small" edge="start">
                      <SearchOutlinedIcon />
                    </IconButton>
                  </InputAdornment>
                ),
                ...(value
                  ? {
                      endAdornment: (
                        <InputAdornment position="end">
                          <CustomTooltip title={t("search.controls.searchbar.clearSearch")}>
                            <IconButton
                              size="small"
                              aria-label={t("search.controls.searchbar.clearSearch")}
                              color="inherit"
                              onClick={() => onChange("")}
                            >
                              <CloseIcon fontSize="small" />
                            </IconButton>
                          </CustomTooltip>
                        </InputAdornment>
                      ),
                    }
                  : {}),
              },
              htmlInput: {
                "aria-label": t("search.controls.searchbar.search"),
                type: "search",
              },
            }}
          />
        </Paper>
      </Box>
    </Box>
  );
}
