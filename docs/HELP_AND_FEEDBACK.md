# Help & feedback, rating and sharing

Added 2026-11-25 after the closed test's report from Testers Community (no bugs found; it asked for a way to rate the
app, share it and get help, and looked for them in Settings). Owner decisions the same day: a Help & feedback section in
Settings, a "Rate Lucento" row plus Google Play's rating card at a good moment, and **no walkthrough, checklist or tab
hints** ("interested people will explore").

## Where it is

**Settings › Help & feedback** (`feature/settings/SettingsHub.kt`, under the page rows, above About):

| Row | Opens |
|---|---|
| Questions and answers | The website's FAQ, `https://www.lucentoapp.com/#faq` (a section of the home page) |
| Contact support | A new email to `LegalDocuments.CONTACT_EMAIL` in the person's email app: subject "Lucento <version>", room to write, then the app version, Android version and phone model, labelled "you can delete this". With no email app, a message gives the address with **Copy**. |
| Lucento Community | The Facebook group (`LucentoLinks.COMMUNITY`) |
| Rate Lucento | Lucento's listing in the Play Store app (`LucentoLinks.PLAY_STORE`, the https link with `setPackage("com.android.vending")`, as Google's "Linking to Google Play" shows), or the same link in the browser without the Play Store. Always the released id `com.lucento.app`, never the debug build's. |
| Share Lucento | The phone's share sheet with `LucentoLinks.shareMessage()` |

The **profile menu's Community group** keeps Community, Follow, Website and Share, and gains **Rate Lucento** after
Share. Both places go through `feature/help/LucentoActions.kt`, so they behave the same. Draft PR #77 (Contact support
form, `feature/contact-support`) adds a "Help" section with its own Contact support page: when it lands, its row takes
the place of this section's email row rather than adding a second section.

Every row hands over to another app on a tap; Lucento itself sends nothing. When nothing on the phone can open a link, a
message says so instead of the tap doing nothing.

## Google Play's rating card

`domain/ReviewPromptPolicy.kt` (pure, `ReviewPromptPolicyTest`) decides; `AppViewModel.claimRatingCard()` records the
ask; `feature/help/PlayRatingCard.kt` asks Play (In-App Review API, `com.google.android.play:review` 2.0.2, the plain
artifact, its two Tasks wrapped in coroutines); `navigation/LucentoRoot.kt` triggers it.

- **When:** a moment after the composer's save has committed and its "saved" confirmation has gone, and only when all
  hold: the Play listing is public (`LucentoLinks.PLAY_LISTING_PUBLIC`), Lucento was first installed on the phone at
  least **7 days** ago (`PackageInfo.firstInstallTime`), the person has recorded at least **20** incomes and expenses
  (counted as the awards count them), and this phone hasn't asked in the last **90 days**.
- **Not over** the Pro welcome, a plan notice or the app lock (the check is skipped; a later save checks again).
- **Recorded before asking**, as `rating_card_asked_at` in DataStore: a phone fact, kept across sign-outs, never synced.
  Google Play keeps its own limit and may show nothing; the API doesn't say whether the card appeared or how anyone
  rated, and Lucento never asks.
- **Closed testers are never asked:** the card waits for `PLAY_LISTING_PUBLIC`, which turns on with the launch build
  (TRUTH_RULES T25: no rating requests to Testers Community testers). "Rate Lucento" works for anyone who chooses it; a
  closed tester's rating is private feedback to the developer.
- **Play's rules followed:** no button triggers the card (the Settings row opens the listing instead), nothing is asked
  before it ("do you like Lucento?" is against Play policy), nothing is offered for a rating, and everyone who meets the
  usage rule may see it, not only happy users.
- The library adds no permission. It merges Play Core's `PlayCoreDialogWrapperActivity` (not exported, transparent),
  which hosts the card. It has none of the window calls the CI edge-to-edge guard rejects (checked 2026-11-25).

### Testing it

Sources: developer.android.com/guide/playcore/in-app-review (and its "test" page), checked 2026-11-25.

- Debug builds (`com.lucento.app.debug`, not on Play) never show the card, and the policy keeps it off before launch.
  The rule and the record are unit-tested (`ReviewPromptPolicyTest`, `AppViewModelRatingCardTest`).
- To see the card: an account on the **internal testing** track that installed Lucento from Play (Play doesn't apply its
  quota there), or **internal app sharing** (the card shows with Submit disabled), with a build that passes
  `listingPublic = true` and meets the rule, then save in the composer.
- On the **closed** track a tester could submit, and it would reach the developer privately (Play Console › Ratings and
  reviews › Testing feedback), but the card stays off there by design.
- Play's guide: never tell the person about an error in the flow, and carry on whatever the result. The code does both.

## Sharing, tagged

`LucentoLinks.shareLink` is the website until the listing is public, then
`LucentoLinks.PLAY_STORE_SHARE`: the Play link with `referrer=utm_source=app_share&utm_medium=referral&utm_campaign=share_lucento`
(encoded), the row the channel kit reserved (docs/marketing/launch/CHANNEL_KIT.md §3). Play Console › Grow users › Store
performance › Conversion analysis can filter listing visitors by UTM source and campaign (with traffic source set to ads
and referrals; there's no medium dimension, and only visitors who don't have Lucento yet are counted). The app never
reads the referrer. "Rate Lucento" is left untagged: its visitors already have the app.

## Privacy

The Privacy Policy (version 2026-11-26) has "Rating, sharing and contacting us": the card's conditions, that Google Play
decides and keeps the rating, that Lucento never learns it, and that the rows only open other apps. Not a new Lucento
network path. The website's `legal.ts` is regenerated with the release that carries it.

Data safety: Google's own table for the library lists only the rating and review the person types, used for the review
on Google Play (private to the developer on a closed track), and says the Play Store handles it under Google Play's
terms. Lucento never receives it, so no answer changes; the form remains the owner's to decide.

## At launch

The launch build flips `LucentoLinks.PLAY_LISTING_PUBLIC` to true (with the website's `playListed`). That one change
sends the tagged Play link from Share Lucento and lets the rating card be asked for.
