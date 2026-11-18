# AI Journal

A manual record of AI conversations about the insurance claims project. Add one entry per conversation, or append updates to an existing entry. Do not include credentials or real claimant data.

## Conversation log

### 2026-11-18 — Creating an AI journal

- **Purpose:** Create a journal to log our conversations.
- **Request:** “create an AI Journal to log our conversations”
- **Format chosen:** Markdown journal.
- **Outcome:** Created this journal with a reusable entry template.

### 2026-11-18 — Planning a Spring Boot insurance claims application

- **Purpose:** Plan an application for submitting and processing insurance claims.
- **Confirmed:** Java 17, Spring Boot, and Maven; three actors: claimants, claims staff, and managers.
- **Proposed MVP:** Claimants submit and track their own claims; claims staff review assigned claims and recommend a decision; managers assign claims and make final decisions.
- **Open decisions:** Which insurance type to support first, and whether manager approval is required for every claim or only for escalations.

### 2026-11-18 — Creating the insurance claims backend

- **Implemented:** Java 17 and Spring Boot REST API with a Maven Wrapper, local H2 persistence, and one development account for each role.
- **Workflow:** Claimant submits; manager assigns; staff recommend approval or denial; manager records the final decision. Managers can see who made each status change and when.
- **Verification:** Four integration tests passed on Java 17, covering both final outcomes, role and claim access, and request validation.

### 2026-11-18 — Adding the journal to the project

- **Outcome:** Added the journal and its existing conversation entries to the project root and linked it from the README.

## New entry template

### YYYY-MM-DD — Topic

- **Purpose:**
- **Key points:**
- **Decisions:**
- **Actions / follow-up:**
