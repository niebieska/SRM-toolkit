# Wpłaty — confirmed MVP requirement

Updated 2026-11-06. Wpłaty are confirmed as required for MVP and are a release
gate. Implementation is in progress on the local
[`feature/manual-payments-ui`](https://github.com/niebieska/SRM-toolkit/tree/feature/manual-payments-ui)
branch, which has no remote tracking branch. Turnus fees are persisted by Biuro
API. The working tree now adds the Registration API ledger and connects the Biuro
payment screen to it. Backend compilation and frontend production build passed;
database-backed tests, full test suites and operator acceptance remain pending.
Registration status history also remains a confirmed MVP requirement.

## Agreed scope

- Add **Wpłaty** as a top-level Biuro tab alongside **Zgłoszenia**. It is one
  register for all Turnusy, with a Turnus filter; links from a Turnus may open the
  same register with that Turnus preselected.
- Add a fixed participant fee and fees for selected seasonal staff roles/subroles
  to the Turnus definition. Use the existing Summer/Winter role catalogue.
  Staff roles without an assigned fee pay 0 PLN. Staff fees depend on role, not age.
  A role and its subrole will not have conflicting fee amounts; applicable amounts
  are not added together.
- The base fee for a participant or staff role/subrole is fixed in the Turnus
  definition and is not later edited. No per-registration fee snapshot is needed
  under this rule. If the amount due should be reduced later, grant an individual
  discount in PLN rather than changing the base fee. A discount may equal the full
  fee; percentage discounts may be considered later.
- Show accepted registrations with an applicable positive fee: participants and
  staff in roles assigned a fee. Do not show unpaid staff roles with no fee.
- OPERATOR finds a registration by registration code or surname and records its
  payments. The description choices are **Zaliczka**, **Dopłata**,
  **Wpłata całej kwoty**, or a custom description. Initial methods are
  **Przelew** and **Gotówka**.
- Record amount, payment date and optional note, with acting account and timestamp.
  Payment names classify entries; valid payments contribute to one paid total.
- Calculate the applicable fee, discount, total paid and balance. Balance is the
  fee minus discount minus valid payments; use decimal monetary calculations. The
  register shows discount, total paid, balance and status, without a separate fee
  column or summary tiles. Details show the status amount and individual payments.
- Payment status is **Brak wpłaty**, **Do dopłaty**, **Opłacone**, **Nadpłata** or
  **Rozliczono rabatem**. Use **Rozliczono rabatem** when a full discount leaves
  zero due and no payment has been recorded. Show the due amount for the first two
  and the excess amount for Nadpłata. Do not add a separate fee column or summary
  tiles to the main register/detail header. Lists may show payment status later,
  but do not allow payment edits there.
- In registration details show only the current discount amount, its description,
  the granting operator and date. Do not show a timeline of previous discount
  versions in this view; retain an audit trail of changes for financial traceability.
- Retain financial records after a registration status changes. Accepted-only
  eligibility governs adding payments and discounts. Existing payments remain
  visible and correctable, with an audit record, after a status change.
- Make payment creation idempotent so retrying an uncertain request with the
  same key cannot create a duplicate financial entry.
- Correct a payment by editing the entry and saving an audit record with previous
  and new values, operator, timestamp and correction reason. Do not silently lose
  prior values.

## Expected implementation areas

- **Biuro DB/API:** Turnus fee configuration and role/subrole validation are
  implemented on this branch. The payment register now joins fee definitions to
  the ledger in batched reads and enforces positive-fee eligibility for writes.
  Existing Turnus records can be configured once; later fee changes are rejected.
  The Turnus base fee belongs with the Biuro-owned Turnus definition.
- **registration-api/DB:** payment/discount records linked to registrations,
  migrations, protected endpoints and account attribution are implemented in
  the working tree. The ledger uses `registration_db` and the stable registration
  ID; Biuro API remains the operator-facing orchestrator. No Person register is
  required.
- **Frontend:** Turnus fee controls and the Wpłaty tab are connected to persisted
  entries, corrections, discounts and balances in the working tree.
- **Configuration:** predefined payment names and methods.
- **Tests:** role/subrole matching, monetary arithmetic, discounts/corrections,
  accepted-only payment/discount creation, corrections after status changes,
  retained history, permissions and idempotent retries.

## Confirmed implementation boundary

Keep the first implementation synchronous and use the existing Biuro API to
Registration API connection. A separate payments service or synchronized payment
read model is not needed at the current scale. Batch reads to avoid one network
request per registration. This can be extended later if usage or performance
requirements change.

## Other exclusions and deferred decisions
- No correction of a staff role after payment is expected in the current workflow.
  Fees use the saved role/subrole; if role correction is later introduced, its
  effect on an existing fee/payment needs a separate decision.
- Remaining balances in Listy or a dedicated payment export are deferred.
- Percentage discounts, online payments, payment-provider integration, automatic
  reconciliation and reminders are outside the initial scope.
- Full automated test coverage, database migration verification and operator
  acceptance are still required before calling the feature complete. Do not use
  the feature in a deployed environment until those checks pass.
