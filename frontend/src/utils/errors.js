/**
 * Turns any Axios error into something the UI can show. The backend always answers with
 * the same ApiError shape: { status, code, message, fieldErrors: [{ field, message }] }.
 */
export function getErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (!error) return fallback;
  if (error.response?.data?.message) return error.response.data.message;
  if (error.code === 'ECONNABORTED') return 'The server took too long to respond. Please try again.';
  if (error.request && !error.response) return 'Cannot reach the server. Check your connection.';
  return fallback;
}

export function getErrorCode(error) {
  return error?.response?.data?.code ?? null;
}

/** [{field:'email', message:'must be a well-formed email address'}] -> { email: '...' } */
export function getFieldErrors(error) {
  const list = error?.response?.data?.fieldErrors ?? [];
  return Object.fromEntries(list.map((fe) => [fe.field, fe.message]));
}
