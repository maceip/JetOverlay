# Slack OAuth Configuration

- Client ID: `8516887257863.10240039617412`
- Redirect URI registered with Slack: `https://maceip.github.io/id/slack-oauth.html`
  - That page must forward to the in-app deep link `jetoverlay://slack/oauth` so the app can receive the `code`.
- Deep link handled by app: `jetoverlay://slack/oauth`
- Client Secret: set in `SlackIntegration.CLIENT_SECRET` (currently placeholder; move to secure storage before release).
- User scopes requested: `channels:history, channels:read, chat:write, groups:history, groups:read, im:history, im:read, im:write, mpim:history, mpim:read, users:read`.

Flow:
1) App opens Slack authorize URL with the above redirect.
2) Slack sends the user to `https://maceip.github.io/id/slack-oauth.html`.
3) That page should redirect to `jetoverlay://slack/oauth?code=...` which the app handles and exchanges using the same redirect URI.
