/**
 * Resolves the authenticated user ID strictly from the auth store state.
 * Returns a positive integer user ID, or null if unauthenticated or invalid.
 * Invariant: Never falls back to localStorage or mock IDs (remediates DEF-02).
 *
 * @param {object} authStore
 * @returns {number|null}
 */
export const resolveCurrentUserId = (authStore) => {
  if (!authStore || !authStore.user) return null
  const rawId = authStore.user.id
  if (rawId === null || rawId === undefined || rawId === '') return null
  const numId = Number(rawId)
  return Number.isInteger(numId) && numId > 0 ? numId : null
}

/**
 * Composable returning a function to resolve the current user ID for a given auth store.
 *
 * @param {object} authStore
 * @returns {() => number|null}
 */
export const useCurrentUserId = (authStore) => {
  return () => resolveCurrentUserId(authStore)
}
