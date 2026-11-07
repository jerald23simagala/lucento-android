# Terms of Use and Privacy Policy

The app carries both documents in plain language, in `feature/legal/LegalDocuments.kt`. They work offline.

**Status: draft (2026-11-07), pending legal review.** They describe what this version of Lucento actually does. The owner must review them, and
they should be checked legally, before Lucento is published on Google Play. Until then, they must not be presented as
final anywhere else, including the website.

## Where they appear

| Place | What happens |
|---|---|
| Create account (onboarding's "Use email instead" page, the invitation path, Settings › Cloud sync) | A checkbox reads "I agree to the Terms of Use and the Privacy Policy". Both names are links that open the document in a sheet. The account can't be created until it is ticked. |
| "Continue with Google", where it can create an account | A line under the buttons reads "By continuing with Google, you agree to the Terms of Use and the Privacy Policy." |
| Settings › Privacy › Legal | Both documents can be opened any time. |

A new email account records the agreement in its Supabase user metadata, with two fields:

- `terms_version`: `LegalDocuments.VERSION`, the date of the text.
- `terms_accepted_at`: an ISO instant.

Google accounts get no metadata, because their consent is given by continuing.

## Keeping them true

- When a feature changes what is collected, sent or kept, update the text in the same change.
- Bump `VERSION` and `UPDATED` whenever the meaning changes.
- The tests in `AccountFormTest` check only the structure. Accuracy is a review job.
- The website's `/privacy` page is older and differs: it says there is no crash reporting, and it doesn't mention Lucento Shared, exchange rates or usage counts. Bring it in line when the owner approves a website change.

## Keeping the website in step

The website's `/privacy` and `/terms` are generated from this file:

```sh
python tools/legal/export_legal.py ../lucento-website/src/data/legal.ts
```

Run it after every change here, in the same piece of work, and deploy the site with the app release that carries the
new text.

## Open before Google Play

1. ~~Contact address~~ **Done 2026-11-07:** lucentoapp@gmail.com (`LegalDocuments.CONTACT_EMAIL`, owner's choice).
2. ~~Deleting the account from inside the app~~ **Done 2026-11-07:** docs/ACCOUNT_DELETION.md. The owner applies the
   migration (NEXT_TASKS OT-15).
3. **Legal review**, still open, including:
   - the minimum age (the policy says Lucento isn't directed at children; Play's target audience is 18+);
   - the governing law and where Lucento is offered;
   - whether the limitation wording fits each market;
   - the developer name the documents and the deletion page use ("Lucento"), which must match the Play listing.
