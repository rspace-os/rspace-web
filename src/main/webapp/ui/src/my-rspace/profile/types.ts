export interface OAuthApp {
    clientId: string;
    appName: string;
}

export interface ConnectedOAuthApp {
    clientId: string;
    clientName: string;
    appName: string;
    scope: string;
}
