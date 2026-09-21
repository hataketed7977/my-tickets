export interface ApiErrorResponse {
  error?: {
    message?: string;
  };
}

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function apiRequest<T>(
  baseUrl: string,
  path: string,
  options: {
    method?: string;
    token?: string;
    body?: unknown;
  } = {},
): Promise<T> {
  const { method = 'GET', token, body } = options;
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) {
    let message = `请求失败 (${response.status})`;
    try {
      const data = (await response.json()) as ApiErrorResponse;
      if (data.error?.message) {
        message = data.error.message;
      }
    } catch {
      // ignore parse error
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}
