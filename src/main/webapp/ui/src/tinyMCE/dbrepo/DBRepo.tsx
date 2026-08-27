import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import FilterListIcon from "@mui/icons-material/FilterList";
import TableChartIcon from "@mui/icons-material/TableChart";
import VisibilityIcon from "@mui/icons-material/Visibility";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Collapse from "@mui/material/Collapse";
import Divider from "@mui/material/Divider";
import FormControlLabel from "@mui/material/FormControlLabel";
import IconButton from "@mui/material/IconButton";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";

const DBREPO_LOGO_PATH = "/images/icons/dbrepo.svg";

export type DBRepoDatabase = {
  id: string;
  name: string;
  description?: string;
  url: string;
};

type DBRepoResourceType = "table" | "view" | "subset";

type DBRepoLinkedResource = {
  id: string;
  type: DBRepoResourceType;
  label: string;
  secondaryText?: string;
  url: string;
};

type DBRepoDatabaseResources = {
  databaseId: string;
  tables: DBRepoLinkedResource[];
  views: DBRepoLinkedResource[];
  subsets: DBRepoLinkedResource[];
  failedTypes: DBRepoResourceType[];
};

type ResourceState = {
  loading: boolean;
  data?: DBRepoDatabaseResources;
  error?: string;
};

type TemplateTarget = {
  name: string;
  url: string;
  dbrepoType: "database" | DBRepoResourceType;
  databaseId: string;
  resourceId: string;
  databaseName: string;
  query: string;
};

type TinyMceEditor = {
  getBody: () => HTMLElement;
  on?: (eventName: string, callback: () => void) => void;
  off?: (eventName: string, callback?: () => void) => void;
  windowManager: {
    close: () => void;
  };
};

export type DBRepoLinkTemplateData = {
  id: string;
  recordURL: string;
  name: string;
  dbrepoType: "database" | DBRepoResourceType;
  databaseId: string;
  resourceId: string;
  databaseName: string;
  query: string;
  iconPath: string;
};

type ParentWindow = {
  tinymce?: { activeEditor?: TinyMceEditor };
  RS?: {
    insertTemplateIntoTinyMCE?: (templateId: string, data: DBRepoLinkTemplateData, editor?: TinyMceEditor) => void;
  };
};

const RESOURCE_GROUPS: Array<{
  type: DBRepoResourceType;
  key: "tables" | "views" | "subsets";
  accent: string;
  Icon: typeof TableChartIcon;
}> = [
  { type: "table", key: "tables", accent: "#1565c0", Icon: TableChartIcon },
  { type: "view", key: "views", accent: "#6a1b9a", Icon: VisibilityIcon },
  { type: "subset", key: "subsets", accent: "#2e7d32", Icon: FilterListIcon },
];

function hashString(value: string): number {
  return value.split("").reduce((hash, character) => {
    return ((hash << 5) - hash + character.charCodeAt(0)) | 0;
  }, 0);
}

export function buildDBRepoLinkTemplateData(target: TemplateTarget): DBRepoLinkTemplateData {
  return {
    id: `dbrepo-${hashString(target.url)}`,
    recordURL: target.url,
    name: target.name,
    dbrepoType: target.dbrepoType,
    databaseId: target.databaseId,
    resourceId: target.resourceId,
    databaseName: target.databaseName,
    query: target.query,
    iconPath: DBREPO_LOGO_PATH,
  };
}

function DBRepo(): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [databases, setDatabases] = useState<DBRepoDatabase[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [expandedDatabaseId, setExpandedDatabaseId] = useState("");
  const [resourcesByDatabase, setResourcesByDatabase] = useState<Record<string, ResourceState>>({});
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
        setSelectedId(data[0]?.id ? databaseSelectionId(data[0].id) : "");
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

  const loadResources = (databaseId: string) => {
    const existing = resourcesByDatabase[databaseId];
    if (existing?.loading || existing?.data) return;
    setResourcesByDatabase((current) => ({
      ...current,
      [databaseId]: { loading: true },
    }));
    void axios
      .get<DBRepoDatabaseResources>(`/apps/dbrepo/databases/${encodeURIComponent(databaseId)}/resources`)
      .then(({ data }) => {
        setResourcesByDatabase((current) => ({
          ...current,
          [databaseId]: { loading: false, data },
        }));
      })
      .catch((e: unknown) => {
        setResourcesByDatabase((current) => ({
          ...current,
          [databaseId]: {
            loading: false,
            error: e instanceof Error ? e.message : t("tinymce.dbrepo.errors.resources"),
          },
        }));
      });
  };

  const toggleDatabase = (databaseId: string) => {
    const nextExpandedDatabaseId = expandedDatabaseId === databaseId ? "" : databaseId;
    setSelectedId(databaseSelectionId(databaseId));
    setExpandedDatabaseId(nextExpandedDatabaseId);
    if (nextExpandedDatabaseId) {
      loadResources(nextExpandedDatabaseId);
    }
  };

  const selectedTarget = selectedTemplateTarget(selectedId, databases, resourcesByDatabase);

  const insertSelectedDatabase = useCallback(() => {
    const parentWindow = parent as unknown as ParentWindow;
    const editor = parentWindow.tinymce?.activeEditor;
    const insertTemplateIntoTinyMCE = parentWindow.RS?.insertTemplateIntoTinyMCE;
    if (!selectedTarget || !editor) return;
    insertTemplateIntoTinyMCE?.("dbrepoLink", buildDBRepoLinkTemplateData(selectedTarget), editor);
    editor.windowManager.close();
  }, [selectedTarget]);

  useEffect(() => {
    window.parent.postMessage({ mceAction: selectedTarget ? "enable" : "disable" }, "*");
  }, [selectedTarget]);

  useEffect(() => {
    const parentWindow = parent as unknown as ParentWindow;
    const editor = parentWindow.tinymce?.activeEditor;
    editor?.off?.("dbrepo-insert");
    editor?.on?.("dbrepo-insert", insertSelectedDatabase);
    return () => {
      editor?.off?.("dbrepo-insert", insertSelectedDatabase);
    };
  }, [insertSelectedDatabase]);

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
          <Box
            key={database.id}
            sx={{
              border: "1px solid",
              borderColor: "divider",
              borderRadius: 1,
              mb: 1,
              overflow: "hidden",
            }}
          >
            <Stack direction="row" spacing={1} sx={{ alignItems: "center", px: 1 }}>
              <FormControlLabel
                value={databaseSelectionId(database.id)}
                control={<Radio />}
                label={
                  <Stack>
                    <Typography>{database.name}</Typography>
                    {database.description && <Typography variant="caption">{database.description}</Typography>}
                  </Stack>
                }
                sx={{ flex: 1, mr: 0 }}
              />
              <IconButton
                aria-label={t(
                  expandedDatabaseId === database.id
                    ? "tinymce.dbrepo.collapseDatabase"
                    : "tinymce.dbrepo.expandDatabase",
                  { name: database.name },
                )}
                aria-expanded={expandedDatabaseId === database.id}
                onClick={() => toggleDatabase(database.id)}
                size="small"
              >
                <ExpandMoreIcon
                  sx={{
                    transform: expandedDatabaseId === database.id ? "rotate(180deg)" : "rotate(0deg)",
                    transition: "transform 150ms ease-in-out",
                  }}
                />
              </IconButton>
            </Stack>
            <Collapse in={expandedDatabaseId === database.id}>
              <Divider />
              <DatabaseResources
                databaseId={database.id}
                resourceState={resourcesByDatabase[database.id]}
                selectedId={selectedId}
                t={t}
              />
            </Collapse>
          </Box>
        ))}
      </RadioGroup>
    </Stack>
  );
}

function DatabaseResources({
  databaseId,
  resourceState,
  selectedId,
  t,
}: {
  databaseId: string;
  resourceState?: ResourceState;
  selectedId: string;
  t: ReturnType<typeof useTranslation<["workspace", "common"]>>["t"];
}): React.ReactNode {
  if (resourceState?.loading || !resourceState) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", p: 1.5, pl: 4 }}>
        <CircularProgress size={16} />
        <Typography variant="body2">{t("tinymce.dbrepo.loadingResources")}</Typography>
      </Stack>
    );
  }

  if (resourceState.error) {
    return (
      <Alert severity="error" sx={{ m: 1.5 }}>
        {t("tinymce.dbrepo.errors.resources")}
      </Alert>
    );
  }

  const data = resourceState.data;
  if (!data) return null;

  return (
    <Stack spacing={1.5} sx={{ p: 1.5, pl: 4 }}>
      {RESOURCE_GROUPS.map(({ type, key, accent, Icon }) => {
        const resources = data[key];
        const failed = data.failedTypes.includes(type);
        return (
          <Stack key={type} spacing={0.5}>
            <Stack direction="row" spacing={1} sx={{ alignItems: "center", color: accent }}>
              <Icon fontSize="small" />
              <Typography variant="subtitle2">{t(`tinymce.dbrepo.categories.${key}`)}</Typography>
              <Typography variant="caption">
                {t("tinymce.dbrepo.categoryCount", { count: resources.length })}
              </Typography>
            </Stack>
            {failed && (
              <Alert severity="warning" sx={{ py: 0 }}>
                {t(`tinymce.dbrepo.errors.${type}`)}
              </Alert>
            )}
            {resources.length === 0 && !failed && (
              <Typography variant="caption" color="text.secondary">
                {t("tinymce.dbrepo.emptyCategory")}
              </Typography>
            )}
            {resources.map((resource) => (
              <FormControlLabel
                key={resourceSelectionId(databaseId, resource)}
                value={resourceSelectionId(databaseId, resource)}
                control={<Radio size="small" />}
                checked={selectedId === resourceSelectionId(databaseId, resource)}
                label={
                  <Stack>
                    <Typography variant="body2">{resource.label}</Typography>
                    {resource.secondaryText && (
                      <Typography
                        variant="caption"
                        sx={{
                          color: "text.secondary",
                          fontFamily: "monospace",
                          overflowWrap: "anywhere",
                        }}
                      >
                        {resource.secondaryText}
                      </Typography>
                    )}
                  </Stack>
                }
                sx={{ ml: 0 }}
              />
            ))}
          </Stack>
        );
      })}
    </Stack>
  );
}

function databaseSelectionId(databaseId: string): string {
  return `database:${databaseId}`;
}

function resourceSelectionId(databaseId: string, resource: DBRepoLinkedResource): string {
  return `${resource.type}:${databaseId}:${resource.id}`;
}

function selectedTemplateTarget(
  selectedId: string,
  databases: DBRepoDatabase[],
  resourcesByDatabase: Record<string, ResourceState>,
): TemplateTarget | undefined {
  if (selectedId.startsWith("database:")) {
    const databaseId = selectedId.slice("database:".length);
    const database = databases.find((candidate) => candidate.id === databaseId);
    return database
      ? {
          name: database.name,
          url: database.url,
          dbrepoType: "database",
          databaseId: database.id,
          resourceId: "",
          databaseName: database.name,
          query: "",
        }
      : undefined;
  }
  for (const [databaseId, resourceState] of Object.entries(resourcesByDatabase)) {
    const database = databases.find((candidate) => candidate.id === databaseId);
    const resources = [
      ...(resourceState.data?.tables ?? []),
      ...(resourceState.data?.views ?? []),
      ...(resourceState.data?.subsets ?? []),
    ];
    const resource = resources.find((candidate) => selectedId === resourceSelectionId(databaseId, candidate));
    if (resource) {
      return {
        name: resource.label,
        url: resource.url,
        dbrepoType: resource.type,
        databaseId,
        resourceId: resource.id,
        databaseName: database?.name ?? "",
        query: resource.type === "subset" ? resource.label : (resource.secondaryText ?? ""),
      };
    }
  }
  return undefined;
}

export default DBRepo;
