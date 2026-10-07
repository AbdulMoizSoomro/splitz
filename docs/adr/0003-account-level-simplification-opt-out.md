# ADR 0003: Account-Level Debt Simplification Opt-Out Is a Hard Override

## Status
Accepted

## Context
Issue #67 decided that users can opt out of transitive debt transfers "globally or with specific target users", but only the group-scoped opt-out was ever built (`GroupSimplificationSettings.optOutUserIds`). There was no account-level preference anywhere: no entity in either service, and no account settings surface in the UI.

That left a consent gap. A user could not express "never net my debts", only "don't net my debts in this one group", and they had to visit every group to do it.

Two designs were possible for the account-level preference:

1. **Inherited default** — the account preference seeds new groups and existing groups keep whatever they already had.
2. **Hard override** — an account-level opt-out excludes the user from netting in every group, including groups configured before the opt-out existed.

## Decision
An account-level opt-out is a **hard override**, not an inherited default.

Opt-out is a *consent* guarantee: the user is asserting "my debts must not be transferred or simplified with third parties". A default that only affected future groups would silently violate consent the user has already given, leaving them netted in groups they believed they had left. Under-approximating consent is the unsafe failure mode, so the conservative reading wins.

To opt back into netting in a specific group, a user first turns the account-wide opt-out off and then uses the existing per-group control. The two levels stay independently meaningful.

### Placement
The preference lives in `expense-service`, not `user-service`. It is simplification configuration, `expense-service` is the only owner of that configuration, and `DebtSimplificationPlanService.computePlan` is a hot read that runs on every group balance view. Storing it in `user-service` would add a network hop to that path and split a single invariant across two services.

### Effective opt-out set
`UserSimplificationPreferenceService.effectiveOptOutUserIds(groupOptOutUserIds, memberIds)` is the single home for the union rule. Account overrides are resolved against the group's members only, so an unrelated user's preference never leaks into another group's plan, and the whole group resolves in one query rather than one per member.

The plan's `optedOutUserIds` reports the **effective** set — group opt-outs unioned with account overrides — and does so even when the group has netting switched off, so the field means the same thing on every plan.

## Consequences
- **Consent cannot be weakened by configuration**: once a user opts out, no group's pre-existing settings can put them back into a netting plan.
- **No cross-service hop**: the plan read path stays within `expense-service`.
- **A user's own preference is private**: the endpoints are scoped to the authenticated user with no group in the path and no admin gate, so no other user can read or change it.
- **Opting back in is two steps**: account-wide off, then per-group on. Slightly more friction, accepted because it keeps the two levels independently meaningful.
- **Membership churn**: leaving a group removes the member from its effective set by construction, since overrides are resolved against current members only.