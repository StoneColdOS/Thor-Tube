# Security

Thor-Tube is a hobby project, not a professionally audited app. This page says plainly what it does with your data so you can judge it yourself, and how to report a problem.

## What the app stores, and where

| What | Where | Protection |
| --- | --- | --- |
| YouTube sign-in token (only if you sign in) | App-private storage | Encrypted with a key held in Android's hardware-backed key store. Excluded from cloud backup and device-to-device transfer. |
| Settings, Home tabs, where you stopped in videos | App-private storage | Readable only by Thor-Tube. Never sent anywhere. |
| Logs | App-private storage | Never contain video links, titles, searches or account details. Only leave the device if you press Setup > Share logs. |

Your Google password never touches the app. Sign-in is the YouTube TV code flow (youtube.com/activate), and you can revoke it any time from your Google account under Security > third-party connections ("YouTube on TV").

## What the app talks to

- **YouTube**, to load videos, search, comments and captions, and (if you sign in) your feeds, library, likes and watch history.
- **GitHub's public releases page for this repo**, in release builds, at most every 6 hours, to see if there's a newer version. Nothing about you or the device is sent.

Nothing else. There's no analytics, crash reporting service, or tracking. All connections are HTTPS; plain HTTP is blocked by Android's network security config, and only system-trusted certificates are accepted.

## Permissions

- **Internet:** to reach YouTube.
- **Vibrate:** the buzz when the app moves between screens.

## How releases are built

- Releases are built by GitHub Actions on a fresh machine from the public source in this repo, not on a personal computer.
- They're signed with a private key stored only in the repo's encrypted secrets.
- Before publishing, the pipeline checks the signature matches that key and that the APK isn't debuggable.
- The GitHub Actions used are pinned to exact commits, and Dependabot proposes updates for review.

## Known limits

- Nobody with a security background has reviewed this code yet. Reviews are welcome.
- The sign-in uses the YouTube TV app's method, which is unofficial and against YouTube's terms. A spare Google account is the cautious choice.
- Video data comes from [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), a widely used open-source library; its security is its maintainers' work.

## Reporting a problem

If you find a security issue, please report it privately through GitHub's **Security > Report a vulnerability** on this repo rather than a public issue.
