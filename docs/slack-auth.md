# Slack OAuth Configuration

- Client ID: `8516887257863.10240039617412`
- Redirect URI registered with Slack: `https://maceip.github.io/id/slack-oauth.html` (helper page now forwards to `jetoverlay://slack/oauth`)
- Deep link handled by app: `jetoverlay://slack/oauth`
- Client Secret: set in `SlackIntegration.CLIENT_SECRET` (currently placeholder; move to secure storage before release).
- User scopes requested: `channels:history, channels:read, chat:write, groups:history, groups:read, im:history, im:read, im:write, mpim:history, mpim:read, users:read`.

Flow:
1) App opens Slack authorize URL with the hosted redirect.
2) Slack sends the user to `https://maceip.github.io/id/slack-oauth.html`.
3) The helper page forwards to `jetoverlay://slack/oauth?code=...`, which the app handles and exchanges using the same redirect URI.
