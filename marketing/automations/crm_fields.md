# Lead/CRM Sheet Schema (Google Sheets / Airtable)

## Recommended Fields
- `lead_id` (UUID or incremental)
- `source` (portfolio / linkedin / marketplace-public / referral)
- `marketplace` (upwork / fiverr / freelancer / other)
- `first_name`
- `last_name`
- `email`
- `country`
- `timezone`
- `role` (dev / design / marketing / consulting / other)
- `keywords` (comma separated)
- `hourly_rate` (optional)
- `portfolio_url`
- `profile_url`
- `icebreaker` (1 sentence, grounded in real data)
- `pain_hint` (multi-client / invoicing / hourly / reporting)
- `segment` (A / B / C)
- `sequence` (A-hourly / B-greeninvoice / C-agency)
- `status` (new / queued / sent_1 / sent_2 / replied_pos / replied_neg / installed / paid / unsub / bounced)
- `last_contacted_at`
- `next_send_at`
- `notes`

## Status Rules
- `unsub` and `bounced` are terminal (never email again).
- `replied_pos` -> manual follow-up within 24 hours.
- `installed` but not paid -> send in-app/email activation nudge (separate lifecycle).

