export type TicketStatus = 'open' | 'in_progress' | 'resolved' | 'closed';
export type TicketPriority = 'low' | 'medium' | 'high' | 'urgent';

export interface AppUser {
  id: string;
  name: string;
  avatarUrl: string | null;
}

export interface AuthConfig {
  feishuConfigured: boolean;
}

export interface TicketItem {
  id: string;
  ticketNo: string;
  title: string;
  description: string;
  status: TicketStatus;
  priority: TicketPriority;
  categoryId: string;
  categoryName: string;
  reporterUserId: string;
  assigneeUserId: string | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
}

export interface TicketListQuery {
  page?: number;
  pageSize?: number;
  search?: string;
  status?: TicketStatus;
  priority?: TicketPriority;
  categoryId?: string;
}

export interface TicketListResponse {
  items: TicketItem[];
  total: number;
  page: number;
  pageSize: number;
}

export interface CreateTicketRequest {
  title: string;
  description: string;
  categoryId: string;
  priority: TicketPriority;
}

export interface UpdateTicketRequest {
  title?: string;
  description?: string;
  categoryId?: string;
  status?: TicketStatus;
  priority?: TicketPriority;
  assigneeUserId?: string | null;
}

export interface IssueCategoryItem {
  id: string;
  name: string;
  description: string;
  isActive: boolean;
}

export interface CreateIssueCategoryRequest {
  name: string;
  description?: string;
}

export interface UpdateIssueCategoryRequest {
  name?: string;
  description?: string;
  isActive?: boolean;
}
