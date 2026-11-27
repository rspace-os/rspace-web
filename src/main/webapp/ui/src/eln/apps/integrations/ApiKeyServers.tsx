import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import ListItemText from "@mui/material/ListItemText";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { useForm } from "@tanstack/react-form";
import type React from "react";
import { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import SecretField from "../../../components/Inputs/SecretField";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { Optional } from "../../../util/optional";
import { type Secret, secretAfterSave } from "../../../util/secret";
import { useDeleteAppOptionsMutation, useIntegrationState, useSaveAppOptionsMutation } from "../queries";
import type { IntegrationStates } from "../useIntegrationsEndpoint";

/*
 * The setup for integrations whose sysadmin configures a list of servers and
 * whose users each authenticate with some of those servers using an API key.
 * Each authenticated server is saved as its own configuration, with options
 * named after the integration, e.g. GALAXY_ALIAS, GALAXY_URL, GALAXY_APIKEY.
 */

type ServerIntegration = "GALAXY" | "PYRAT";

type Server = IntegrationStates[ServerIntegration]["credentials"]["authenticatedServers"][number];

/** The integration's translated text, as the keys differ between integrations. */
export type ApiKeyServersMessages = {
  noServers: string;
  apiKeyLabel: (alias: string) => string;
  saveSuccess: string;
  saveError: string;
  deleteSuccess: string;
  deleteError: string;
  addSuccess: string;
  addError: string;
};

const serverApiKeySchema = v.object({
  apiKey: v.nullable(v.string()),
});

function serverOptions(integration: ServerIntegration, server: { alias: string; url: string }, apiKey: Secret) {
  return {
    [`${integration}_ALIAS`]: server.alias,
    [`${integration}_URL`]: server.url,
    [`${integration}_APIKEY`]: apiKey,
  };
}

function ServerRow({
  integration,
  server,
  messages,
}: {
  integration: ServerIntegration;
  server: Server;
  messages: ApiKeyServersMessages;
}): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const save = useSaveAppOptionsMutation(integration);
  const remove = useDeleteAppOptionsMutation(integration);
  const form = useForm({
    defaultValues: { apiKey: server.apiKey },
    validators: { onChange: serverApiKeySchema },
    onSubmit: async ({ value, formApi }) => {
      try {
        const saved = await save.mutateAsync({
          optionsId: Optional.present(server.optionsId),
          option: serverOptions(integration, server, value.apiKey),
        });
        const savedServer = saved.credentials.authenticatedServers.find((s) => s.optionsId === server.optionsId);
        formApi.reset({ apiKey: savedServer?.apiKey ?? secretAfterSave(value.apiKey) });
        addAlert(mkAlert({ variant: "success", message: messages.saveSuccess }));
      } catch (e) {
        if (e instanceof Error) addAlert(mkAlert({ variant: "error", title: messages.saveError, message: e.message }));
      }
    },
  });
  const saving = save.isPending;

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        event.stopPropagation();
        void form.handleSubmit();
      }}
    >
      <Stack direction="row" spacing={1}>
        <form.Field name="apiKey">
          {(field) => (
            <SecretField
              fullWidth
              variant="outlined"
              label={messages.apiKeyLabel(server.alias)}
              size="small"
              autoComplete="new-password"
              value={field.state.value}
              storedSecretExists={server.apiKey !== ""}
              disabled={saving}
              onChange={field.handleChange}
            />
          )}
        </form.Field>
        <Button type="submit" disabled={saving}>
          {t("common:actions.save")}
        </Button>
        <Button
          disabled={saving}
          onClick={() => {
            void remove.mutateAsync(server.optionsId).then(
              () => addAlert(mkAlert({ variant: "success", message: messages.deleteSuccess })),
              (e: unknown) => {
                if (e instanceof Error)
                  addAlert(mkAlert({ variant: "error", title: messages.deleteError, message: e.message }));
              },
            );
          }}
        >
          {t("common:actions.delete")}
        </Button>
      </Stack>
    </form>
  );
}

/**
 * The list of servers the user has authenticated with, each with a form for
 * its API key, and a menu for adding the remaining configured servers.
 */
function ApiKeyServers({
  integration,
  messages,
}: {
  integration: ServerIntegration;
  messages: ApiKeyServersMessages;
}): React.ReactNode {
  const { t } = useTranslation(["apps", "common"]);
  const { addAlert } = useContext(AlertContext);
  const { configuredServers, authenticatedServers } = useIntegrationState(integration).credentials;
  const add = useSaveAppOptionsMutation(integration);
  const [addMenuAnchorEl, setAddMenuAnchorEl] = useState<null | HTMLElement>(null);
  /* Aliases whose add request is in flight, so a second click cannot create a duplicate. */
  const [pendingAliases, setPendingAliases] = useState<ReadonlySet<string>>(() => new Set());
  const setAliasPending = (alias: string, pending: boolean) => {
    setPendingAliases((current) => {
      const next = new Set(current);
      if (pending) next.add(alias);
      else next.delete(alias);
      return next;
    });
  };

  const unauthenticatedServers = configuredServers.filter(
    ({ alias }) => !authenticatedServers.some((s) => s.alias === alias),
  );

  const addServer = async (server: { alias: string; url: string }) => {
    setAliasPending(server.alias, true);
    try {
      const saved = await add.mutateAsync({
        optionsId: Optional.empty(),
        option: serverOptions(integration, server, ""),
      });
      setAddMenuAnchorEl(null);
      if (!saved.credentials.authenticatedServers.some((s) => s.alias === server.alias))
        throw new Error("Save completed but cannot show results.");
      addAlert(mkAlert({ variant: "success", message: messages.addSuccess }));
    } catch (e) {
      if (e instanceof Error) addAlert(mkAlert({ variant: "error", title: messages.addError, message: e.message }));
    } finally {
      setAliasPending(server.alias, false);
    }
  };

  return (
    <Card variant="outlined" sx={{ mt: 2 }}>
      <CardContent>
        <Stack spacing={1}>
          {authenticatedServers.length === 0 && <Typography variant="body2">{messages.noServers}</Typography>}
          {authenticatedServers.map((server) => (
            <ServerRow key={server.optionsId} integration={integration} server={server} messages={messages} />
          ))}
        </Stack>
      </CardContent>
      <CardActions>
        <Button
          onClick={(e) => {
            setAddMenuAnchorEl(e.currentTarget);
          }}
          disabled={unauthenticatedServers.length === 0}
        >
          {t("common:actions.add")}
        </Button>
        <Menu open={Boolean(addMenuAnchorEl)} anchorEl={addMenuAnchorEl} onClose={() => setAddMenuAnchorEl(null)}>
          {unauthenticatedServers.map((server) => (
            <MenuItem
              key={server.alias}
              disabled={pendingAliases.has(server.alias)}
              onClick={() => {
                void addServer(server);
              }}
            >
              <ListItemText primary={server.alias} secondary={server.url} />
            </MenuItem>
          ))}
        </Menu>
      </CardActions>
    </Card>
  );
}

export default ApiKeyServers;
