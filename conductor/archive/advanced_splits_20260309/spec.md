# Specification: Implement Advanced Split Logic and Enhanced Category Management

## Overview
This track focuses on expanding the core expense tracking capabilities of Splitz. It introduces advanced splitting methods (Percentage, Shares, Adjustment) and provides a more robust category management system to support future analytical visualizations.

## Objectives
- Implement `PERCENTAGE` split type in `ExpenseService`.
- Implement `SHARES` split type in `ExpenseService`.
- Add `ADJUSTMENT` split type to handle small balance offsets.
- Expand `CategoryService` to include full CRUD (Create, Read, Update, Delete) for expense categories.
- Enhance balance reports by integrating with `user-service` to provide user profile details.

## Functional Requirements
- **Percentage Split**: Users specify the percentage share for each group member. The sum of all percentages must be exactly 100%.
- **Shares Split**: Users specify the number of 'shares' for each group member (e.g., User A has 2 shares, User B has 1 share, so A pays 2/3 and B pays 1/3).
- **Adjustment Split**: Allow users to add a fixed amount (adjustment) to an existing split.
- **Category CRUD**: Admins can create and manage categories. Each category can have an icon (identifier) and a color.
- **User Detail Integration**: The `BalanceController` must use the `UserClient` to fetch names and profile details for users mentioned in balance reports.

## Non-Functional Requirements
- **Accuracy**: Split calculations must be precise to two decimal places, correctly handling remainders.
- **Validation**: Strict validation for split inputs (e.g., percentages summing to 100%).
- **Performance**: Cross-service calls (via `UserClient`) should be efficient.

## Acceptance Criteria
- Unit tests verify all new split logic with edge cases (e.g., rounding).
- Integration tests confirm that `Category` CRUD operations are fully functional.
- Manual verification confirms that balance reports correctly display user profile information.
