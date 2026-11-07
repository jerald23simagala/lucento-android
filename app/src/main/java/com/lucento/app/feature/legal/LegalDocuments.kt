package com.lucento.app.feature.legal

/**
 * Lucento's Terms of Use and Privacy Policy, in plain language, as they apply to this version of the app (see
 * docs/LEGAL.md). Shown from account creation (where agreeing is required), and from Settings › Privacy.
 *
 * They describe what the app actually does; when a feature changes what is collected or sent, update the text here and
 * bump [VERSION] in the same change. A new account records the version it agreed to.
 */
object LegalDocuments {
    /** Recorded with each new email account (`terms_version` in the account's metadata). */
    const val VERSION = "2026-11-07"
    const val UPDATED = "7 November 2026"

    /** Where questions, privacy requests and account-deletion requests go (Play's privacy point of contact). */
    const val CONTACT_EMAIL = "lucentoapp@gmail.com"

    /** The public page for deleting an account without the app (Play's "Delete account URL"). */
    const val DELETE_ACCOUNT_URL = "https://www.lucentoapp.com/delete-account"

    data class Section(val title: String, val items: List<String>)

    data class Document(val title: String, val intro: String, val sections: List<Section>)

    val terms = Document(
        title = "Terms of Use",
        intro = "These terms cover your use of the Lucento app, its optional online services and Lucento Pro. By " +
            "creating a Lucento account or buying Lucento Pro you agree to them.",
        sections = listOf(
            Section(
                "What Lucento is",
                listOf(
                    "Lucento helps you record and understand your own money: wallets, spending, budgets, goals, debts " +
                        "and scheduled payments. It works fully on your phone; an account is optional and only needed for " +
                        "cloud sync, Lucento Shared and the AI features.",
                    "Lucento doesn’t connect to your bank, move money or make payments. Everything in it is what you " +
                        "entered or imported.",
                ),
            ),
            Section(
                "Not financial advice",
                listOf(
                    "Totals, budgets, projections, insights and Ask Lucento answers are information to help you think, " +
                        "not financial, investment, tax or legal advice. They can be wrong, especially when entries are " +
                        "missing or exchange rates are out of date.",
                    "Check anything important — and talk to a qualified adviser — before making a financial decision.",
                ),
            ),
            Section(
                "Your account",
                listOf(
                    "Use an email address you own and keep your password to yourself. You’re responsible for what " +
                        "happens with your account.",
                    "One account is for one person. To share your finances with someone, use Lucento Shared instead of " +
                        "sharing a password.",
                    "You can stop using Lucento at any time. Signing out removes your account’s data from that phone.",
                    "You can delete your account at any time in Settings › Cloud sync › Delete account, or by writing to " +
                        "us. It deletes the account and everything stored with it in Lucento’s cloud.",
                ),
            ),
            Section(
                "Your data",
                listOf(
                    "Your records are yours. We store a copy only to provide the features you turn on, as described in the " +
                        "Privacy Policy.",
                    "Keep a backup of anything you can’t afford to lose — cloud sync or a backup file from Settings › Your " +
                        "data. We work hard to protect your records, but we can’t promise they will never be lost.",
                ),
            ),
            Section(
                "Lucento Shared",
                listOf(
                    "When you share your finances, the people you invite can see and change the shared records. Invite " +
                        "only people you trust; the owner can remove access at any time.",
                ),
            ),
            Section(
                "Lucento Pro and purchases",
                listOf(
                    "Lucento’s core features are free. Lucento Pro is sold through Google Play as a subscription (monthly " +
                        "or yearly) or as a one-time Lifetime purchase. The price, in your currency, is shown in the app " +
                        "and by Google Play before you pay.",
                    "Subscriptions renew automatically until you cancel them in Google Play (Payments & subscriptions › " +
                        "Subscriptions). When a free trial is offered, it becomes a paid subscription at its end unless " +
                        "you cancel before then. Cancelling stops the next renewal; Pro stays until the end of the period " +
                        "you paid for. Deleting the app or your Lucento account doesn’t cancel a subscription.",
                    "Lifetime unlocks every Pro feature on your phone with no end date, and includes 12 months of the " +
                        "online services (cloud sync, Lucento Shared and the Pro AI allowance). After those 12 months, " +
                        "the online services continue with the optional Lucento Cloud add-on.",
                    "Google Play processes payments, refunds and billing under its own terms and refund policy. Lucento " +
                        "never sees your card or payment details.",
                    "A purchase belongs to the Lucento account that first confirmed it. If you need it on a different " +
                        "account, write to us.",
                ),
            ),
            Section(
                "Fair use",
                listOf(
                    "Don’t try to reach other people’s data, disrupt or overload Lucento’s services, or use them for " +
                        "anything unlawful. AI features have an allowance that depends on your plan.",
                    "We may suspend an account that abuses the service.",
                ),
            ),
            Section(
                "Services Lucento relies on",
                listOf(
                    "Online features use other companies’ services: Supabase (accounts and sync), Google (sign-in, " +
                        "Google Play purchases, and Gemini for the AI features you turn on), Resend (account emails), " +
                        "Vercel (Lucento’s server) and public exchange-rate sources. They may be unavailable at times.",
                ),
            ),
            Section(
                "Changes and availability",
                listOf(
                    "Features may change or stop, and online services may be interrupted. Your records on your phone " +
                        "keep working without them.",
                    "When these terms change in a way that matters, we’ll tell you in the app before the change applies.",
                ),
            ),
            Section(
                "Responsibility",
                listOf(
                    "Lucento is provided as it is. As far as the law allows, we aren’t responsible for losses from " +
                        "decisions based on what the app shows, or from data you didn’t back up. Nothing here limits rights " +
                        "you have under consumer law where you live.",
                ),
            ),
            Section(
                "Contact",
                listOf(
                    "Questions, requests and complaints: email $CONTACT_EMAIL.",
                ),
            ),
        ),
    )

    val privacy = Document(
        title = "Privacy Policy",
        intro = "Lucento is built to work without sending your money anywhere. This explains what stays on your phone, " +
            "what leaves it, and when — only for the features you choose.",
        sections = listOf(
            Section(
                "On your phone",
                listOf(
                    "Your wallets, transactions, budgets, goals, debts, scheduled payments and settings are stored in a " +
                        "database on your phone. Every core feature works offline.",
                    "Receipt scanning reads the photo on your phone; the photo isn’t uploaded. A profile photo stays on " +
                        "your phone too.",
                    "Lucento doesn’t use Android’s automatic cloud backup, so nothing is copied to a backup you didn’t " +
                        "choose. There are no ads.",
                ),
            ),
            Section(
                "Your account (optional)",
                listOf(
                    "Creating an account stores your email address, a scrambled form of your password (never the password " +
                        "itself), when you agreed to these terms, and — for Google sign-in — the name and picture link on " +
                        "your Google account.",
                    "We send emails only about your account, such as the link to verify your address. They’re delivered by " +
                        "our email provider, Resend.",
                    "When you choose a password, Lucento checks it against known data leaks by sending only the first five " +
                        "characters of a scrambled version (Have I Been Pwned). Your password is never sent there.",
                ),
            ),
            Section(
                "Cloud sync (with an account)",
                listOf(
                    "With sync on, a copy of your records is kept in Lucento’s cloud database (Supabase), so they’re backed " +
                        "up and the same on every phone you sign in to.",
                    "Database rules let only your signed-in account — and people you share with through Lucento Shared — " +
                        "read or change your records.",
                    "Signing out removes your account’s data from that phone; the cloud copy stays for when you sign in " +
                        "again. The cloud copy is kept while your account exists.",
                ),
            ),
            Section(
                "Lucento Shared",
                listOf(
                    "Members of a Shared Space see its records and the name each member chose. An invitation stores the " +
                        "email address it was sent to until it is accepted, declined or expires.",
                ),
            ),
            Section(
                "Lucento Pro purchases",
                listOf(
                    "Google Play handles the payment. Lucento receives from Google Play what you bought, its order number, " +
                        "status and dates, and a purchase token — never your card or payment details.",
                    "Lucento’s server checks each purchase with Google and keeps, with your account, the plan it gives, " +
                        "the order number and a scrambled form of the purchase token. When you buy while signed in, the " +
                        "app gives Google Play a scrambled form of your account id so the purchase can be matched to you.",
                    "Your plan is also kept on your phone, sealed with Android’s keystore, so Pro works offline.",
                    "Google Play’s billing component, built into the app, sends Google its own diagnostics, as in every " +
                        "app that sells through Google Play. Google Play keeps its own purchase records under Google’s " +
                        "privacy policy.",
                ),
            ),
            Section(
                "Exchange rates",
                listOf(
                    "For wallets in other currencies, Lucento downloads public exchange rates (jsDelivr’s currency data, " +
                        "with Frankfurter as a fallback). Only currency codes and dates are requested — nothing about you " +
                        "or your money. This is on by default; turn it off in Settings › Money.",
                ),
            ),
            Section(
                "AI features (off until you turn them on)",
                listOf(
                    "Read messages with Gemini sends the message you type or speak, today’s date, your main currency and " +
                        "your category and account names to Lucento’s server, which asks Google’s Gemini to read it.",
                    "Ask Lucento’s AI answers send your question with a summary of your finances, including recent " +
                        "transactions, as its consent screen explains before you agree.",
                    "Lucento’s server doesn’t keep these messages; it counts requests per account per day for the daily " +
                        "allowance. You can turn either feature off at any time.",
                ),
            ),
            Section(
                "Crash reports and usage counts (off until you turn them on)",
                listOf(
                    "Crash reports send what went wrong in the app’s code — never amounts, names or notes — to Google " +
                        "Firebase Crashlytics.",
                    "Usage counts send event names and timings (such as which setup screen was opened), with a random " +
                        "number for this install and the app version — never amounts or what you type. They’re kept for " +
                        "90 days, and turning them off deletes what was sent.",
                ),
            ),
            Section(
                "Deleting your account and data",
                listOf(
                    "In the app: Settings › Cloud sync › Delete account. It deletes your account and everything stored " +
                        "with it on Lucento’s servers straight away — your synced records, plan records, Shared Space and " +
                        "AI request counts — and erases this phone’s records.",
                    "Without the app: follow the steps at $DELETE_ACCOUNT_URL, or email $CONTACT_EMAIL from your " +
                        "account’s address. We delete the account within 30 days and reply to confirm.",
                    "Usage counts aren’t linked to your account: they’re deleted after 90 days, or as soon as you turn " +
                        "them off. Short-lived technical logs at our hosting providers expire on their own. Records on a " +
                        "phone without an account stay on it until you delete them or the app.",
                ),
            ),
            Section(
                "Children",
                listOf(
                    "Lucento is made for adults managing their own money and isn’t directed at children.",
                ),
            ),
            Section(
                "Sharing",
                listOf(
                    "We don’t sell your data or use it for advertising. The services named here handle it only to run " +
                        "the features you use.",
                ),
            ),
            Section(
                "Changes and contact",
                listOf(
                    "When this policy changes in a way that matters, we’ll tell you in the app.",
                    "Privacy questions and data requests: email $CONTACT_EMAIL.",
                ),
            ),
        ),
    )
}
