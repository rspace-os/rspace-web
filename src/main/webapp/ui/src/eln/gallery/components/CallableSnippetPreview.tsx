import React from "react";
import { GalleryFile } from "../useGalleryListing";
import { Dialog } from "@/components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import axios from "@/common/axios";
import { getErrorMessage } from "@/util/error";
import useOauthToken from "@/hooks/auth/useOauthToken";

const SnippetPreviewContext = React.createContext((_file: GalleryFile) => {});

export function useSnippetPreview(): {
  openSnippetPreview: (file: GalleryFile) => void;
} {
  const openSnippetPreview = React.useContext(SnippetPreviewContext);
  return {
    openSnippetPreview,
  };
}

export function CallableSnippetPreview({
  children,
}: {
  children: React.ReactNode;
}): React.ReactNode {
  const [snippetFile, setSnippetFile] = React.useState<null | GalleryFile>(
    null,
  );
  const [snippetContent, setSnippetContent] = React.useState<null | string>(
    null,
  );
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<null | string>(null);
  const { getToken } = useOauthToken();

  const fetchSnippetContent = async (fileId: string): Promise<void> => {
    setLoading(true);
    setError(null);
    try {
      const response = await axios.get<string>(`/api/v1/snippet/${fileId}/content`, {
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
      setSnippetContent(response.data);
    } catch (err) {
      const errorMessage = getErrorMessage(
        err,
        "Failed to load snippet content",
      );
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <SnippetPreviewContext.Provider
        value={(file) => {
          setSnippetFile(file);
          setSnippetContent(null);
          setError(null);
          if (!file.id) throw new Error("File ID is missing");
          void fetchSnippetContent(file.id.toString());
        }}
      >
        {children}
      </SnippetPreviewContext.Provider>
      <Dialog
        open={snippetFile !== null}
        fullWidth
        maxWidth="md"
        onClose={() => setSnippetFile(null)}
      >
        <DialogTitle>Snippet Preview: {snippetFile?.name}</DialogTitle>
        <DialogContent dividers>
          {loading ? (
            <p>Loading snippet content...</p>
          ) : error ? (
            <p>Error: {error}</p>
          ) : snippetContent ? (
            <div dangerouslySetInnerHTML={{ __html: snippetContent }} />
          ) : (
            <p>No content available</p>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSnippetFile(null)}>Close</Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
