import type {
  CreateTicketRequest,
  TicketItem,
  TicketListQuery,
  TicketListResponse,
  UpdateTicketRequest,
} from '@/types/api';

import { request } from '@api/request';

export function listTickets(
  query: TicketListQuery,
): Promise<TicketListResponse> {
  return request<TicketListResponse>(
    { url: '/api/tickets', method: 'GET', params: query },
    '加载工单列表失败',
  );
}

export function getTicket(id: string): Promise<TicketItem> {
  return request<TicketItem>(
    { url: `/api/tickets/${id}`, method: 'GET' },
    '加载工单详情失败',
  );
}

export function createTicket(body: CreateTicketRequest): Promise<TicketItem> {
  return request<TicketItem>(
    { url: '/api/tickets', method: 'POST', data: body },
    '创建工单失败',
  );
}

export function updateTicket(
  id: string,
  body: UpdateTicketRequest,
): Promise<TicketItem> {
  return request<TicketItem>(
    { url: `/api/tickets/${id}`, method: 'PATCH', data: body },
    '更新工单失败',
  );
}

export function deleteTicket(id: string): Promise<void> {
  return request<void>(
    { url: `/api/tickets/${id}`, method: 'DELETE' },
    '删除工单失败',
  );
}
