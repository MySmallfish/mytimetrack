# MyTimetrack Marketing Kit (Marketplace Freelancers)

This folder is a ready-to-run outbound + on-platform messaging kit aimed at freelancers who sell on marketplaces (Upwork/Fiverr/etc.) and also do off-platform client work.

It includes:
- A focused go-to-market plan and positioning.
- A compliant "AI agent" lead pipeline (research -> qualify -> personalize -> send -> reply triage).
- Copy: cold email sequences, marketplace messages, landing page, App Store listing, ads/posts.
- Image assets (editable SVG) + the app screenshots already captured in `marketing/assets/screenshots/`.

## Start Here (Fast Path)
1. Fill `marketing/variables.md` (use placeholders if you don't have a value yet).
2. Pick a segment and sequence from `marketing/automations/email_sequences.md`.
3. Build a lead list using the workflow in `marketing/automations/lead_pipeline.md`.
4. Track everything using the sheet schema in `marketing/automations/crm_fields.md`.
5. Use the SVG assets in `marketing/assets/svg/` for emails/social/landing page.

## Marketplace Reality Check (Important)
Most marketplaces do **not** like off-platform solicitation, scraping, or contact harvesting. This kit is written to keep you on the right side of:
- Platform ToS (use on-platform messaging where required; do not scrape gated contact details).
- Anti-spam laws (CAN-SPAM / GDPR / CASL), including opt-out and honoring suppression lists.

Read `marketing/compliance.md` before sending anything.

## Files
- `marketing/plan.md`: the detailed GTM plan and experiments.
- `marketing/compliance.md`: what to do (and not do) for outreach.
- `marketing/variables.md`: one place to customize everything.
- `marketing/automations/lead_pipeline.md`: the agent + automation pipeline (tool-agnostic).
- `marketing/automations/agent_prompts.md`: prompt templates for research/personalization/compliance.
- `marketing/automations/email_sequences.md`: 3 complete sequences + subject lines.
- `marketing/automations/marketplace_messages.md`: compliant on-platform messages + followups.
- `marketing/automations/crm_fields.md`: Airtable/Sheet schema + statuses.
- `marketing/copy/landing_page.md`: landing page copy blocks.
- `marketing/copy/app_store.md`: App Store listing copy + keywords.
- `marketing/copy/ads_posts.md`: short ads, posts, and community outreach copy.
- `marketing/copy/press_kit.md`: one-page blurb + boilerplate.
- `marketing/assets/svg/`: editable SVG banners and headers.
- `marketing/assets/screenshots/`: product screenshots (copied from repo `Screenshots/`).
- `marketing/assets/templates/`: lead magnet templates (CSV).
