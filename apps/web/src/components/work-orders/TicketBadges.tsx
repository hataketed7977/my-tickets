import { Badge } from '@/components/ui/badge';
import type { TicketPriority, TicketStatus } from '@/types/api';

const STATUS_LABELS: Record<TicketStatus, string> = {
  open: '待处理',
  in_progress: '处理中',
  resolved: '已解决',
  closed: '已关闭',
};

const PRIORITY_LABELS: Record<TicketPriority, string> = {
  low: '低',
  medium: '普通',
  high: '高',
  urgent: '紧急',
};

const PRIORITY_VARIANTS: Record<
  TicketPriority,
  'success' | 'info' | 'warning' | 'destructive'
> = {
  low: 'success',
  medium: 'info',
  high: 'warning',
  urgent: 'destructive',
};

export function TicketStatusBadge({ status }: { status: TicketStatus }) {
  const variant: 'default' | 'outline' | 'secondary' =
    status === 'in_progress'
      ? 'default'
      : status === 'resolved' || status === 'closed'
        ? 'secondary'
        : 'outline';
  return <Badge variant={variant}>{STATUS_LABELS[status]}</Badge>;
}

export function TicketPriorityBadge({
  priority,
}: {
  priority: TicketPriority;
}) {
  return (
    <Badge variant={PRIORITY_VARIANTS[priority]}>
      {PRIORITY_LABELS[priority]}
    </Badge>
  );
}

export { PRIORITY_LABELS, STATUS_LABELS };
