import { InboxIcon } from 'lucide-react';
import type { KeyboardEvent } from 'react';

import { UserDisplay } from '@/components/users/UserDisplay';
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import type { TicketItem } from '@/types/api';

import { TicketPriorityBadge, TicketStatusBadge } from './TicketBadges';

interface TicketTableProps {
  tickets: TicketItem[];
  onSelectTicket: (ticket: TicketItem) => void;
}

const DATE_FORMATTER: Intl.DateTimeFormat = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function TicketTable({
  tickets,
  onSelectTicket,
}: TicketTableProps) {
  if (tickets.length === 0) {
    return (
      <Empty>
        <EmptyHeader>
          <EmptyMedia variant="icon">
            <InboxIcon />
          </EmptyMedia>
          <EmptyTitle>暂无工单</EmptyTitle>
          <EmptyDescription>当前筛选条件下没有可显示的工单。</EmptyDescription>
        </EmptyHeader>
      </Empty>
    );
  }

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>工单</TableHead>
          <TableHead>状态</TableHead>
          <TableHead className="hidden sm:table-cell">优先级</TableHead>
          <TableHead className="hidden md:table-cell">分类</TableHead>
          <TableHead className="hidden lg:table-cell">责任人</TableHead>
          <TableHead className="hidden xl:table-cell">更新时间</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {tickets.map((ticket: TicketItem) => (
          <TableRow
            key={ticket.id}
            tabIndex={0}
            aria-label={`查看工单 ${ticket.ticketNo}：${ticket.title}`}
            className="cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset"
            onClick={() => onSelectTicket(ticket)}
            onKeyDown={(event: KeyboardEvent<HTMLTableRowElement>) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onSelectTicket(ticket);
              }
            }}
          >
            <TableCell className="max-w-72">
              <span className="block min-w-0 text-left">
                <span className="block truncate">{ticket.title}</span>
                <span className="mt-0.5 block text-xs text-muted-foreground">
                  {ticket.ticketNo}
                </span>
              </span>
            </TableCell>
            <TableCell>
              <TicketStatusBadge status={ticket.status} />
            </TableCell>
            <TableCell className="hidden sm:table-cell">
              <TicketPriorityBadge priority={ticket.priority} />
            </TableCell>
            <TableCell className="hidden md:table-cell">
              {ticket.categoryName}
            </TableCell>
            <TableCell className="hidden lg:table-cell">
              {ticket.assigneeUserId ? (
                <UserDisplay value={[ticket.assigneeUserId]} size="small" />
              ) : (
                <span className="text-muted-foreground">未指定</span>
              )}
            </TableCell>
            <TableCell className="hidden text-muted-foreground xl:table-cell">
              {DATE_FORMATTER.format(new Date(ticket.updatedAt))}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
