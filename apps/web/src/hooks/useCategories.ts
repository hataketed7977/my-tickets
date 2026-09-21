import { useCallback, useEffect, useRef, useState } from 'react';

import { categoriesApi } from '@/api';
import { getErrorMessage } from '@/lib/errors';
import type { IssueCategoryItem } from '@/types/api';

interface CategoriesState {
  categories: IssueCategoryItem[] | null;
  error: string | null;
  loading: boolean;
  refresh: () => Promise<void>;
}

export function useCategories(): CategoriesState {
  const [categories, setCategories] = useState<IssueCategoryItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const requestId = useRef<number>(0);

  const refresh = useCallback(async (): Promise<void> => {
    const currentRequestId = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await categoriesApi.listCategories();
      if (currentRequestId === requestId.current) setCategories(result);
    } catch (caughtError: unknown) {
      if (currentRequestId === requestId.current) {
        setError(getErrorMessage(caughtError));
      }
    } finally {
      if (currentRequestId === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    return () => {
      requestId.current += 1;
    };
  }, [refresh]);

  return { categories, error, loading, refresh };
}
