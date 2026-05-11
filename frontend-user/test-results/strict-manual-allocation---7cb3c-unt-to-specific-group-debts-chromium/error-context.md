# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: strict-manual-allocation.spec.ts >> [E2E] Strict Manual Debt Allocation >> should allow manual allocation of settlement amount to specific group debts
- Location: src/test/e2e/strict-manual-allocation.spec.ts:79:3

# Error details

```
Test timeout of 120000ms exceeded.
```

# Page snapshot

```yaml
- generic [ref=e3]:
  - navigation [ref=e4]:
    - generic [ref=e6]:
      - link "Splitz" [ref=e8] [cursor=pointer]:
        - /url: /
        - generic [ref=e9]: Splitz
      - generic [ref=e10]:
        - generic [ref=e11]: Hi, alice_51_1778475437998
        - button [ref=e12]:
          - img [ref=e13]
        - button "Logout" [ref=e17]:
          - img [ref=e18]
  - complementary "Sidebar" [ref=e21]:
    - list [ref=e23]:
      - listitem [ref=e24]:
        - link "Dashboard" [ref=e25] [cursor=pointer]:
          - /url: /
          - img [ref=e26]
          - generic [ref=e29]: Dashboard
      - listitem [ref=e30]:
        - link "Friends" [ref=e31] [cursor=pointer]:
          - /url: /friends
          - img [ref=e32]
          - generic [ref=e37]: Friends
      - listitem [ref=e38]:
        - link "Groups" [ref=e39] [cursor=pointer]:
          - /url: /groups
          - img [ref=e40]
          - generic [ref=e42]: Groups
      - listitem [ref=e43]:
        - link "Activity" [ref=e44] [cursor=pointer]:
          - /url: /activity
          - img [ref=e45]
          - generic [ref=e47]: Activity
      - listitem [ref=e48]:
        - link "Settings" [ref=e49] [cursor=pointer]:
          - /url: /settings
          - img [ref=e50]
          - generic [ref=e53]: Settings
  - main [ref=e54]:
    - generic [ref=e56]:
      - generic [ref=e57]:
        - generic [ref=e58]:
          - heading "Your Groups" [level=1] [ref=e59]
          - paragraph [ref=e60]: Manage your expense groups and members.
        - button "Create Group" [ref=e61]:
          - img [ref=e62]
          - generic [ref=e63]: Create Group
      - generic [ref=e65]:
        - generic [ref=e66]:
          - generic [ref=e67] [cursor=pointer]:
            - generic [ref=e68]:
              - img [ref=e70]
              - generic [ref=e72]:
                - img [ref=e73]
                - generic [ref=e78]: "2"
            - heading "Dinner Group 1778475437998" [level=3] [ref=e79]
            - button "Add Expense" [ref=e81]:
              - img [ref=e82]
              - generic [ref=e84]: Add Expense
          - generic [ref=e85] [cursor=pointer]:
            - generic [ref=e86]:
              - img [ref=e88]
              - generic [ref=e90]:
                - img [ref=e91]
                - generic [ref=e96]: "2"
            - heading "Travel Group 1778475437998" [level=3] [ref=e97]
            - button "Add Expense" [ref=e99]:
              - img [ref=e100]
              - generic [ref=e102]: Add Expense
        - dialog "Add New Expense" [ref=e104]:
          - generic [ref=e105]:
            - heading "Add New Expense" [level=2] [ref=e106]
            - button "Close" [ref=e107]:
              - img [ref=e108]
          - generic [ref=e112]:
            - generic [ref=e113]:
              - generic [ref=e114]: Description
              - textbox "Description" [ref=e115]:
                - /placeholder: e.g., Dinner, Groceries
                - text: Dinner
            - generic [ref=e116]:
              - generic [ref=e117]:
                - generic [ref=e118]: Amount
                - spinbutton "Amount" [active] [ref=e119]: "40.00"
              - generic [ref=e120]:
                - generic [ref=e121]: Date
                - textbox "Date" [ref=e122]: 2026-05-11
            - generic [ref=e123]:
              - generic [ref=e124]: Category
              - combobox [ref=e125]:
                - option "Select a category" [selected]
                - option "Food & Dining"
                - option "Transport"
                - option "Entertainment"
                - option "Utilities"
                - option "Shopping"
                - option "Other"
                - option "Rent"
                - option "Travel"
            - generic [ref=e126]:
              - text: "Split Type:"
              - generic [ref=e127]:
                - generic [ref=e128] [cursor=pointer]:
                  - radio "equal" [checked] [ref=e129]
                  - generic [ref=e130]: equal
                - generic [ref=e131] [cursor=pointer]:
                  - radio "exact" [ref=e132]
                  - generic [ref=e133]: exact
                - generic [ref=e134] [cursor=pointer]:
                  - radio "percentage" [ref=e135]
                  - generic [ref=e136]: percentage
                - generic [ref=e137] [cursor=pointer]:
                  - radio "shares" [ref=e138]
                  - generic [ref=e139]: shares
                - generic [ref=e140] [cursor=pointer]:
                  - radio "Fixed Adjustment" [ref=e141]
                  - generic [ref=e142]: Fixed Adjustment
            - generic [ref=e143]:
              - generic [ref=e144]: Split between members
              - generic [ref=e145]:
                - generic [ref=e148]:
                  - checkbox "User 1211" [checked] [ref=e149]
                  - generic [ref=e150]: User 1211
                - generic [ref=e153]:
                  - checkbox "User 1210 (You)" [checked] [ref=e154]
                  - generic [ref=e155]: User 1210 (You)
            - paragraph [ref=e157]:
              - text: "Each person pays:"
              - generic [ref=e158]: $20.00
            - generic [ref=e159]:
              - button "Cancel" [ref=e160]
              - button "Add Expense" [ref=e161]:
                - generic [ref=e162]: Add Expense
```