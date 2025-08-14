import React from "react";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../theme";
import FolderTree from "./FolderTree";
import { type FolderRecord } from "../hooks/api/useFolders";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

/**
 * Simple test component for specs
 */
export function TestFolderTreeExample() {
  const [selectedFolder, setSelectedFolder] =
    React.useState<FolderRecord | null>(null);

  return (
    <ThemeProvider theme={materialTheme}>
      <Box sx={{ p: 2 }}>
        <FolderTree
          onFolderSelect={setSelectedFolder}
          selectedFolderId={selectedFolder?.id}
        />
        {selectedFolder && (
          <div data-testid="selected-folder">
            <span data-testid="folder-name">{selectedFolder.name}</span>
            <span data-testid="folder-id">{selectedFolder.id}</span>
          </div>
        )}
      </Box>
    </ThemeProvider>
  );
}

/**
 * Simple example of FolderTree component
 */
export function SimpleFolderTreeExample() {
  const [selectedFolder, setSelectedFolder] =
    React.useState<FolderRecord | null>(null);

  return (
    <ThemeProvider theme={materialTheme}>
      <Box sx={{ p: 2 }}>
        <Typography variant="h4" gutterBottom>
          Folder Tree
        </Typography>
        <Box sx={{ display: "flex", gap: 2 }}>
          <Box sx={{ width: 300, border: 1, borderColor: "grey.300", p: 1 }}>
            <FolderTree
              onFolderSelect={setSelectedFolder}
              selectedFolderId={selectedFolder?.id}
            />
          </Box>
          <Box sx={{ flex: 1, p: 2, border: 1, borderColor: "grey.300" }}>
            <Typography variant="h6">Selected Folder:</Typography>
            {selectedFolder ? (
              <div>
                <p>
                  <strong>ID:</strong> {selectedFolder.id}
                </p>
                <p>
                  <strong>Name:</strong> {selectedFolder.name}
                </p>
                <p>
                  <strong>Global ID:</strong> {selectedFolder.globalId}
                </p>
                <p>
                  <strong>Type:</strong> {selectedFolder.type}
                </p>
              </div>
            ) : (
              <Typography color="text.secondary">No folder selected</Typography>
            )}
          </Box>
        </Box>
      </Box>
    </ThemeProvider>
  );
}
