import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
} from 'axios';

interface ApiErrorBody {
  error?: {
    message?: string;
  };
}

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || undefined,
  withCredentials: true,
  timeout: 15000,
});

export function apiUrl(path: string): string {
  const baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? '';
  return `${baseUrl.replace(/\/$/, '')}${path}`;
}

export async function request<T>(
  config: AxiosRequestConfig,
  errorMessage: string,
): Promise<T> {
  try {
    const response: AxiosResponse<T> = await apiClient.request<T>(config);
    return response.data;
  } catch (error: unknown) {
    if (error instanceof AxiosError) {
      if (error.response?.status === 401) {
        window.dispatchEvent(new Event('auth:unauthorized'));
      }
      const body: ApiErrorBody | undefined = error.response?.data as
        | ApiErrorBody
        | undefined;
      throw new Error(body?.error?.message ?? errorMessage);
    }
    throw error instanceof Error ? error : new Error(errorMessage);
  }
}
