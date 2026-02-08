# Automation Pipeline (AI Agents + Outreach)

This is tool-agnostic. You can implement with Clay + Instantly/Lemlist + Airtable, or with Make/Zapier + a lightweight LLM step.

## Goal
Turn "public freelancer profiles" into "qualified leads" and send a short, personalized sequence that gets replies and installs.

## Pipeline Overview
1. **Lead Discovery (Human or Agent-assisted)**
   - Input: marketplace search URLs, categories, public directories, LinkedIn search.
   - Output: `profile_url`, `portfolio_url` (if present), role, country, keywords.

2. **Contact Acquisition (Strict)**
   - Acceptable:
     - Email explicitly published on the freelancer's own site.
     - A public "Contact" form on their site (treat as manual outreach, not bulk).
   - Avoid:
     - Scraping emails hidden behind marketplace auth flows.
     - Buying sketchy lists.

3. **Enrichment + Fit Scoring (Agent)**
   - Extract only facts you can cite from the lead record:
     - services offered, client types, "hourly/retainer" language, invoicing mentions.
   - Score (0-100):
     - +30: hourly/retainer signal
     - +20: multi-client/agency signal
     - +20: invoicing/reporting signal
     - +10: mobile-first vibe (mentions iPhone/iOS/tools)
     - -50: no clear fit / student / "not taking clients"
   - Gate:
     - Send only if score >= 50.

4. **Personalization (Agent)**
   - Produce:
     - `icebreaker`: 1 sentence based on real info (no flattery).
     - `pain_hint`: choose one.
   - Hard rule: if there is no grounding data, write a generic opener.

5. **Sequence Selection (Rules)**
   - Segment A: general hourly freelancer -> Sequence A.
   - Segment B: Israel/GreenInvoice -> Sequence B.
   - Segment C: agency/multi-project -> Sequence C.

6. **Send + Throttle (Outreach Tool)**
   - Daily cap + random send windows.
   - Include opt-out and address footer.

7. **Reply Triage (Agent-assisted, Human-confirmed)**
   - Labels:
     - Interested, Not now, Not a fit, Unsubscribe, Wrong person
   - Update sheet status and schedule next action.

## Suggested Automation Implementation (No-Code)
- Trigger: new row in Airtable/Sheet with `status=new`
- Step: enrichment + scoring (Clay / Make with LLM)
- Step: write `icebreaker`, choose `sequence`
- Step: push to Instantly/Lemlist via CSV/API and set `status=queued`
- Step: when Instantly marks `sent`, update `status=sent_1` etc
- Step: when reply received, label + update `status=replied_*`

## Suggested Implementation (Code-Light)
If you prefer a local workflow:
- Keep the sheet as CSV.
- Run a small script that:
  - validates leads,
  - generates email drafts,
  - outputs an Instantly-ready CSV.

I did not include marketplace scrapers here on purpose; it's usually a ToS and deliverability trap.

