# Mission Control as a desktop app: options (2026-11-20)

Owner, 2026-11-20: "can you explore the possibility of migrating the web app to desktop app so we have better interface."
Later the same day: "mission control need not be mobile friendly as I expect to handle them through my laptop." The
laptop is the device; phones are not a target for any option below.

Mission Control is a Next.js console on Vercel: every page is rendered on the server, every change is a server action,
the database decides who may do what (`mc.gate`), and the keys for Resend, Play, Meta, Google and TikTok live only on
the server. Whatever the desktop shell, that server stays: a desktop app is a better window onto it, not a replacement.

## The four ways

| | **A. Installable app (PWA)** | **B. Tauri shell** | **C. Electron shell** | **D. Native rewrite** |
|---|---|---|---|---|
| What it is | The same console, installed from Edge or Chrome: its own window and taskbar icon, no browser bars | A small Windows program (WebView2) that opens the console's address | A larger program with its own Chromium that opens the console | A new Windows program talking to a new API |
| Build effort | About a day (manifest, icons, badge, shortcuts) | 3–5 days, then upkeep (Rust toolchain, installer builds) | 3–5 days, then upkeep (Chromium updates every few weeks) | Weeks; a second client to keep in step with the console and its security review |
| Cost | ₱0 | A code-signing certificate (about US$100–400 a year) or Windows SmartScreen warns on every install and update | Same certificate | Same certificate |
| Updates | Every Vercel deploy, at once | Its own updater and signed releases | Its own updater and signed releases | Its own releases |
| Sign-in, authenticator, cookies | Unchanged (the real browser) | Works for password + code | Works for password + code | New sign-in to build |
| Connecting YouTube, Facebook/Instagram, TikTok (auto-posting) | Unchanged | **Breaks**: Google refuses OAuth in embedded webviews (403 `disallowed_useragent`); Facebook refuses embedded browsers on Android and only half-documents desktop. Needs the system browser plus a hand-off back into the app | Same problem (Electron is an embedded browser to Google) | Same problem |
| Taskbar badge with what's to do | Yes (Badging API, Chrome/Edge 81+ on Windows, installed apps) | Yes | Yes | Yes |
| Starts with Windows | Yes (Edge › Apps › **Auto-start on device login**) | Yes | Yes | Yes |
| Notifications when Claude adds or answers a task | Yes, with Web Push: a second step (a service worker, push keys, and the console sending a push) | Yes | Yes | Yes |
| Tray icon, global shortcut | No | Yes | Yes | Yes |

## Recommendation: A, in two steps

1. **Installable app now** (about a day): a web app manifest (name "Mission Control", its own icon so it doesn't look
   like the Lucento app, theme colours for light and dark), a taskbar badge with the number of tasks to do (the same
   count as the Tasks menu item), jump-list shortcuts (Tasks, Marketing, Support, Overview), and one window that is
   reused instead of a second copy. You install it once from Edge (**⋯** › **Apps** › **Install Mission Control**) and
   tick **Auto-start on device login** if you want it open every morning. Nothing changes for sign-in, the
   authenticator or auto-posting's account connections.
2. **Notifications later, if you want them**: a Windows notification when Claude adds a task, answers your question, or
   a post needs your yes. It needs a service worker and push keys (free) and a small table of where to send pushes,
   with your permission asked once.

B and C buy a tray icon and a global shortcut, at the price of a yearly certificate (or SmartScreen warnings), an
updater, and auto-posting's account connections breaking inside the embedded browser. D rebuilds what already works.
None of that is "better interface"; the interface itself is the same pages in every option. If the pages themselves
feel cramped or slow, that is a design pass on the console (Tasks went through one), which every option would need.
With the laptop as the only device, that pass can use the width a laptop has (Tasks: the list beside the open task)
instead of designing every page down to a phone.

## Sources

- Chrome no longer needs a service worker to offer install from the menu (Chrome 112 on desktop):
  https://developer.chrome.com/blog/update-install-criteria
- Installable web apps, browser support: https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Installable_PWAs
- Badging API (Windows, Chrome/Edge 81+, installed apps): https://developer.chrome.com/docs/capabilities/web-apis/badging-api
  and https://learn.microsoft.com/microsoft-edge/progressive-web-apps/how-to/notifications-badges
- Edge: run an installed app at sign-in: https://learn.microsoft.com/microsoft-edge/progressive-web-apps/ux
- Google blocks OAuth in embedded webviews: https://developers.googleblog.com/en/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/
- Facebook Login and embedded browsers: https://developers.facebook.com/blog/post/2021/06/28/deprecating-support-fb-login-authentication-android-embedded-browsers/
  and https://developers.facebook.com/docs/facebook-login/guides/advanced/manual-flow
- Tauri 2: remote pages get no IPC unless a capability allows them: https://v2.tauri.app/security/capabilities/
