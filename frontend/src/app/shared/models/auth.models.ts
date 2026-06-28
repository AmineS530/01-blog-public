export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  refreshToken?: string;
  username: string;
  role: string;
}

/**
 * Mirrors the backend's GET /api/auth/username-cooldown response.
 *
 * - {@link cooldownSeconds}: the configured minimum elapsed time between
 *   two username changes, in seconds. Drives the countdown text on the
 *   settings page ("You can change your username again in N days").
 * - {@link lastChangedAt}: ISO-8601 timestamp of the user's most recent
 *   username change, or null if they have never changed their username.
 * - {@link nextAllowedAt}: ISO-8601 timestamp at which the user becomes
 *   eligible to change their username again, or null if they have never
 *   changed their username.
 */
export interface CooldownResponse {
  cooldownSeconds: number;
  lastChangedAt: string | null;
  nextAllowedAt: string | null;
}