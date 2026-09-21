import { useCallback, useEffect, useRef, useState } from 'react';

import { ticketsApi } from '@/api';
import { getErrorMessage } from '@/lib/errors';
import type { TicketListQuery, TicketListResponse } from '@/types/api';

interface TicketsState {
  tickets: TicketListResponse | null;
  error: string | null;
  loading: boolean;
  refresh: () => Promise<void>;
}

export function useTickets(query: TicketListQuery): TicketsState {
  const [tickets, setTickets] = useState<TicketListResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const requestId = useRef<number>(0);
  const { page, pageSize, search, status, priority, categoryId } = query;

  const refresh = useCallback(async (): Promise<void> => {
    const currentRequestId = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await ticketsApi.listTickets({
        page,
        pageSize,
        search,
        status,
        priority,
        categoryId,
      });
      if (currentRequestId === requestId.current) setTickets(result);
    } catch (caughtError: unknown) {
      if (currentRequestId === requestId.current) {
        setError(getErrorMessage(caughtError));
      }
    } finally {
      if (currentRequestId === requestId.current) setLoading(false);
    }
  }, [categoryId, page, pageSize, priority, search, status]);

  useEffect(() => {
    void refresh();
    return () => {
      requestId.current += 1;
    };
  }, [refresh]);

  return { tickets, error, loading, refresh };
}
