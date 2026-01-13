# Slack OAuth Configuration

- Client ID: `8516887257863.10240039617412`
- Redirect URI registered with Slack: `https://maceip.github.io/id/slack-oauth.html`
  - That page must forward to the in-app deep link `jetoverlay://slack-callback` so the app can receive the `code`.
- Deep link handled by app: `jetoverlay://slack-callback`
- Client Secret: set in `SlackIntegration.CLIENT_SECRET` (currently placeholder; move to secure storage before release).
- Scopes requested: `channels:history, groups:history, im:history, mpim:history`.

Flow:
1) App opens Slack authorize URL with the above redirect.
2) Slack sends the user to `https://maceip.github.io/id/slack-oauth.html`.
3) That page should redirect to `jetoverlay://slack-callback?code=...` which the app handles and exchanges using the same redirect URI.
