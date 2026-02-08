# Concrete Automation Recipes (Pick One Stack)

These are practical "glue" setups to implement the pipeline in `lead_pipeline.md` without writing scrapers.

## Stack 1 (Recommended): Airtable + Make.com + Instantly/Lemlist

### Tables
- `Leads` (fields from `crm_fields.md`)
- `Events` (optional: sent/replied/install events)

### Scenario: Qualify + Draft + Queue
Trigger:
- Airtable: new record where `status = new`

Steps:
1. Make.com: validate required fields (`email`, `first_name` or fallback, `portfolio_url` if available).
2. LLM step (use prompts in `agent_prompts.md`):
   - Fit + scoring -> write `score`, `pain_hint`, `disqualify`
   - If disqualify: set `status = not_a_fit`
3. LLM step:
   - Personalization -> write `icebreaker` (or empty string)
4. Rule step:
   - Choose `sequence` A/B/C and set `segment`
5. Instantly/Lemlist:
   - Create lead with custom variables: `first_name`, `icebreaker`, `pain_hint`, `portfolio_url`
6. Airtable:
   - Set `status = queued`, set `next_send_at`

### Scenario: Reply -> Triage -> Update
Trigger:
- Instantly/Lemlist webhook (reply received)

Steps:
1. LLM step:
   - Reply triage -> label + suggested reply
2. Airtable:
   - Set `status = replied_pos|replied_neg|unsub|question`
3. (Human-in-the-loop recommended)
   - Send suggested reply manually for `interested` and `question`.
   - Auto-confirm only for `unsubscribe` ("You're removed, sorry about that.").

## Stack 2: Google Sheets + Manual Review + CSV Import (Fastest Setup)
1. Keep leads in a Google Sheet with the columns in `crm_fields.md`.
2. Run enrichment/personalization inside your LLM tool and fill `icebreaker`, `segment`, `sequence`.
3. Export a CSV and import into Instantly/Lemlist.
4. Use the sequences in `email_sequences.md`.

This is less "agentic", but it is safer and gets you to revenue faster.

## Stack 3: n8n (Self-hosted) + SMTP (Advanced)
Only do this if you're already good at deliverability and suppression lists.
- Build a workflow that:
  - reads new leads,
  - runs the prompt steps,
  - sends via SMTP with strict throttles,
  - writes to a suppression store.

Most teams are better off using Instantly/Lemlist at the start.

