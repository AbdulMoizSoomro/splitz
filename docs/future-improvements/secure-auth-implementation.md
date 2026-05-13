# Future Improvement: Secure Cookie-Based Authentication

## Objective
Transition from storing JWT tokens in `localStorage` to using `HttpOnly` cookies to significantly enhance the security posture of the application against Cross-Site Scripting (XSS) attacks.

## Proposed Architectural Changes

### 1. Backend: Secure Cookie Issuance
Modify `AuthController.java` and the security configuration in the `user-service` to:
- Stop returning the JWT in the JSON response body.
- Instead, set a `ResponseCookie` in the HTTP response header.
- The cookie should have the following attributes:
  - `HttpOnly`: Prevents client-side scripts from accessing the token.
  - `Secure`: Ensures the cookie is only sent over HTTPS.
  - `SameSite=Strict` (or `Lax`): Protects against Cross-Site Request Forgery (CSRF).
  - `Path=/`: Ensures the cookie is sent to all API endpoints.

### 2. Frontend: Cookie-Aware Requests
Update the `frontend-user` to:
- Configure the `axios` instance (`src/lib/axios.ts`) with `withCredentials: true`. This allows the browser to automatically include the secure cookie in all cross-origin requests.
- Update `authStore.ts` to remove `localStorage` persistence logic. The "logged in" state should be determined by attempting to fetch the user profile (`/users/me`) on app initialization.

### 3. Implementation of "Remember Me"
- If a "Remember Me" feature is desired in the future, it should be handled by the backend by adjusting the `maxAge` attribute of the issued cookie based on the user's choice during login.

## Benefits
- **XSS Mitigation:** Even if a malicious script runs on the frontend, it cannot extract the JWT from the `HttpOnly` cookie.
- **Simpler Frontend State:** The frontend no longer needs to manually manage token storage, expiration, or synchronization with `localStorage`.
- **Standard Compliance:** Aligns with modern security best practices for Single Page Applications (SPAs).
