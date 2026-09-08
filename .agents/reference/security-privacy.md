# Security and privacy

## Data minimization

Collect, persist, log, and expose only data required for the feature.

Never log:

- credentials/tokens;
- private keys;
- sensitive user content;
- accessibility node text unless an explicit product/security requirement justifies it;
- identifiers not needed for diagnosis.

## Secrets

Never commit:

- production keystores;
- signing passwords;
- API secrets;
- private environment files;
- real service credentials.

Use CI secret stores/environment variables/local ignored files according to repository conventions.

## Permissions

Request the narrowest capability that satisfies product behavior.

Special permissions such as overlay, broad package visibility, accessibility, exact alarms, VPN, notification listener, and background location require explicit product need and policy review.

## Intents/components

- minimize exported components;
- validate incoming intents/deep links;
- use immutable PendingIntent where possible;
- do not expose internal files through unsafe URIs;
- validate provider/path permissions.

## Web/network

- HTTPS by default;
- do not disable certificate validation;
- sanitize untrusted WebView content and minimize JS bridges;
- keep network debug logging out of release when it can expose data.

## Persistence

Do not store sensitive data in plain SharedPreferences/Room merely for convenience. Use platform security mechanisms appropriate to threat model.

## Accessibility apps

Treat accessibility access as high privilege. Do not inspect node content when package/window metadata suffices. Keep local debug journals free of screen text by default.
