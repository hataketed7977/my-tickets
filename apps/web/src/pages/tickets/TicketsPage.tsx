import { ChevronLeftIcon, ChevronRightIcon, PlusIcon, SearchIcon } from 'lucide-react';
import { FormEvent, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { PageError, PageLoading } from '@/components/tickets/AsyncState';
import { PageHeader } from '@/components/tickets/PageHeader';
import { TicketCreateDialog } from '@/components/tickets/TicketCreateDialog';
import { TicketDetailsSheet } from '@/components/tickets/TicketDetailsSheet';
import { TicketTable } from '@/components/tickets/TicketTable';
import { Button } from '@/components/ui/button';
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field';
import {
  InputGroup,
  InputGroupAddon,
  InputGroupButton,
  InputGroupInput,
} from '@/components/ui/input-group';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useCategories } from '@/hooks/useCategories';
import { useTickets } from '@/hooks/useTickets';
import type {
  IssueCategoryItem,
  TicketItem,
  TicketListResponse,
  TicketPriority,
  TicketStatus,
} from '@/types/api';

export default function TicketsPage({
  initialCreateOpen = false,
}: {
  initialCreateOpen?: boolean;
}) {
  const navigate = useNavigate();
  const { ticketId: routeTicketId } = useParams<{ ticketId: string }>();
  const [createDialogOpen, setCreateDialogOpen] =
    useState<boolean>(initialCreateOpen);
  const [selectedTicket, setSelectedTicket] = useState<TicketItem | null>(null);
  const [searchInput, setSearchInput] = useState<string>('');
  const [search, setSearch] = useState<string>('');
  const [status, setStatus] = useState<string>('all');
  const [priority, setPriority] = useState<string>('all');
  const [categoryId, setCategoryId] = useState<string>('all');
  const [page, setPage] = useState<number>(1);

  const {
    tickets: ticketData,
    error: ticketsError,
    loading: ticketsLoading,
    refresh: refreshTickets,
  } = useTickets({
    page,
    pageSize: 15,
    search: search || undefined,
    status: status === 'all' ? undefined : (status as TicketStatus),
    priority:
      priority === 'all' ? undefined : (priority as TicketPriority),
    categoryId: categoryId === 'all' ? undefined : categoryId,
  });
  const {
    categories,
    error: categoriesError,
    loading: categoriesLoading,
    refresh: refreshCategories,
  } = useCategories();

  const loading = ticketsLoading || categoriesLoading;
  const error = ticketsError ?? categoriesError;
  const refreshPage = async (): Promise<void> => {
    await Promise.all([refreshTickets(), refreshCategories()]);
  };

  const handleSearch = (event: FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    setPage(1);
    setSearch(searchInput.trim());
  };

  if (loading && (!ticketData || !categories)) return <PageLoading />;
  if (error && (!ticketData || !categories)) {
    return <PageError message={error} onRetry={refreshPage} />;
  }

  const tickets: TicketListResponse = ticketData ?? {
    items: [],
    total: 0,
    page: 1,
    pageSize: 15,
  };
  const totalPages: number = Math.max(
    1,
    Math.ceil(tickets.total / tickets.pageSize),
  );
  const routedTicket: TicketItem | null =
    tickets.items.find(
      (ticket: TicketItem) => ticket.id === routeTicketId,
    ) ?? null;
  const activeTicket: TicketItem | null = selectedTicket ?? routedTicket;
  const activeTicketId: string | null =
    selectedTicket?.id ?? routeTicketId ?? null;

  const handleCreateDialogChange = (open: boolean): void => {
    setCreateDialogOpen(open);
    if (!open && initialCreateOpen) {
      navigate('/tickets', { replace: true });
    }
  };

  const handleDetailsSheetChange = (open: boolean): void => {
    if (open) return;
    setSelectedTicket(null);
    if (routeTicketId) {
      navigate('/tickets', { replace: true });
    }
  };

  const handleDataChanged = async (): Promise<void> => {
    setPage(1);
    await refreshTickets();
  };

  return (
    <div className="min-h-full">
      <PageHeader
        title="工单管理"
        description="创建、筛选并持续跟进所有服务请求。"
        actions={
          <Button
            type="button"
            onClick={() => setCreateDialogOpen(true)}
          >
            <PlusIcon data-icon="inline-start" />
            新建工单
          </Button>
        }
      />
      <div className="flex flex-col gap-4 p-4 sm:p-6">
        <form
          onSubmit={handleSearch}
          className="rounded-lg border bg-background p-4"
        >
          <FieldGroup className="grid gap-3 md:grid-cols-2 xl:grid-cols-[minmax(220px,1.4fr)_repeat(3,minmax(130px,0.7fr))]">
            <Field>
              <FieldLabel htmlFor="ticket-search" className="sr-only">
                搜索工单
              </FieldLabel>
              <InputGroup>
                <InputGroupInput
                  id="ticket-search"
                  value={searchInput}
                  onChange={(event) => setSearchInput(event.target.value)}
                  placeholder="搜索标题或工单编号"
                  maxLength={160}
                />
                <InputGroupAddon align="inline-end">
                  <InputGroupButton
                    type="submit"
                    size="icon-xs"
                    aria-label="搜索工单"
                  >
                    <SearchIcon />
                  </InputGroupButton>
                </InputGroupAddon>
              </InputGroup>
            </Field>
            <FilterSelect
              label="状态"
              value={status}
              onChange={(value: string) => {
                setPage(1);
                setStatus(value);
              }}
              options={[
                ['all', '全部状态'],
                ['open', '待处理'],
                ['in_progress', '处理中'],
                ['resolved', '已解决'],
                ['closed', '已关闭'],
              ]}
            />
            <FilterSelect
              label="优先级"
              value={priority}
              onChange={(value: string) => {
                setPage(1);
                setPriority(value);
              }}
              options={[
                ['all', '全部优先级'],
                ['low', '低'],
                ['medium', '普通'],
                ['high', '高'],
                ['urgent', '紧急'],
              ]}
            />
            <FilterSelect
              label="问题分类"
              value={categoryId}
              onChange={(value: string) => {
                setPage(1);
                setCategoryId(value);
              }}
              options={[
                ['all', '全部分类'],
                ...(categories ?? []).map(
                  (category: IssueCategoryItem): [string, string] => [
                    category.id,
                    category.name,
                  ],
                ),
              ]}
            />
          </FieldGroup>
        </form>

        <section className="overflow-hidden rounded-lg border bg-background">
          <div className="flex items-center justify-between gap-3 border-b px-4 py-3">
            <p className="text-sm text-muted-foreground">
              共 {tickets.total} 个工单
            </p>
            {loading ? (
              <span className="text-xs text-muted-foreground">正在刷新</span>
            ) : null}
          </div>
          <TicketTable
            tickets={tickets.items}
            onSelectTicket={setSelectedTicket}
          />
          <div className="flex items-center justify-between gap-3 border-t px-4 py-3">
            <span className="text-sm text-muted-foreground">
              第 {page} / {totalPages} 页
            </span>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label="上一页"
                disabled={page <= 1 || loading}
                onClick={() => setPage((current: number) => current - 1)}
              >
                <ChevronLeftIcon />
              </Button>
              <Button
                type="button"
                variant="outline"
                size="icon"
                aria-label="下一页"
                disabled={page >= totalPages || loading}
                onClick={() => setPage((current: number) => current + 1)}
              >
                <ChevronRightIcon />
              </Button>
            </div>
          </div>
        </section>
      </div>
      <TicketCreateDialog
        open={createDialogOpen}
        onOpenChange={handleCreateDialogChange}
        onCreated={handleDataChanged}
      />
      <TicketDetailsSheet
        open={Boolean(activeTicketId)}
        ticketId={activeTicketId}
        initialTicket={activeTicket}
        onOpenChange={handleDetailsSheetChange}
        onUpdated={handleDataChanged}
        onDeleted={handleDataChanged}
      />
    </div>
  );
}

function FilterSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: [string, string][];
}) {
  return (
    <Field>
      <FieldLabel className="sr-only">{label}</FieldLabel>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="w-full">
          <SelectValue placeholder={label} />
        </SelectTrigger>
        <SelectContent>
          <SelectGroup>
            {options.map(([optionValue, optionLabel]: [string, string]) => (
              <SelectItem key={optionValue} value={optionValue}>
                {optionLabel}
              </SelectItem>
            ))}
          </SelectGroup>
        </SelectContent>
      </Select>
    </Field>
  );
}
