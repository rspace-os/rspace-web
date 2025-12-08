import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/rspace/gallery";
import { LandmarksProvider } from "@/components/LandmarksContext";
import MoveDialog from "./MoveDialog";

export function MoveDialogStory() {
    return (
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
            <LandmarksProvider>
                <MoveDialog open={true} onClose={() => {}} section="Images" refreshListing={() => Promise.resolve()} />
            </LandmarksProvider>
        </ThemeProvider>
    );
}
