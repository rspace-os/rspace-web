import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";

export type DBRepoDatabase = {
  id: string;
  name: string;
  description?: string;
  url: string;
};

type TinyMceEditor = {
  execCommand: (command: string, ui: boolean, value: string) => void;
  windowManager: {
    close: () => void;
  };
};

export function buildDatabaseLinkHtml(database: DBRepoDatabase): string {
  const link = document.createElement("a");
  link.href = database.url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = database.name;
  return link.outerHTML;
}

function DBRepo(): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [databases, setDatabases] = useState<DBRepoDatabase[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    void axios
      .get<DBRepoDatabase[]>("/apps/dbrepo/databases")
      .then(({ data }) => {
        if (cancelled) return;
        setDatabases(data);
        setSelectedId(data[0]?.id ?? "");
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : t("tinymce.dbrepo.errors.load"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [t]);

  const selectedDatabase = databases.find((database) => database.id === selectedId);

  const insertSelectedDatabase = () => {
    const editor = (parent as unknown as { tinymce?: { activeEditor?: TinyMceEditor } })?.tinymce?.activeEditor;
    if (!selectedDatabase || !editor) return;
    editor.execCommand("mceInsertContent", false, buildDatabaseLinkHtml(selectedDatabase));
    editor.windowManager.close();
  };

  if (loading) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", p: 2 }}>
        <CircularProgress size={20} />
        <Typography>{t("tinymce.dbrepo.loading")}</Typography>
      </Stack>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ m: 2 }}>
        {t("tinymce.dbrepo.errors.load")}
      </Alert>
    );
  }

  if (databases.length === 0) {
    return (
      <Alert severity="info" sx={{ m: 2 }}>
        {t("tinymce.dbrepo.empty")}
      </Alert>
    );
  }

  return (
    <Stack spacing={2} sx={{ p: 2 }}>
      <RadioGroup
        aria-label={t("tinymce.dbrepo.databaseList")}
        value={selectedId}
        onChange={({ target: { value } }) => setSelectedId(value)}
      >
        {databases.map((database) => (
          <FormControlLabel
            key={database.id}
            value={database.id}
            control={<Radio />}
            label={
              <Stack>
                <Typography>{database.name}</Typography>
                {database.description && <Typography variant="caption">{database.description}</Typography>}
              </Stack>
            }
          />
        ))}
      </RadioGroup>
      <Button variant="contained" disabled={!selectedDatabase} onClick={insertSelectedDatabase}>
        {t("tinymce.dbrepo.insert")}
      </Button>
    </Stack>
  );
}

export default DBRepo;
