import { SaveIcon, Trash2Icon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { toast } from 'sonner';

import { categoriesApi, ticketsApi } from '@/api';
import { UserDisplay } from '@/components/users/UserDisplay';
import { UserSelect } from '@/components/users/UserSelect';
import {
  Alert,
  AlertDescription,
  AlertTitle,
} from '@/components/ui/alert';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { Skeleton } from '@/components/ui/skeleton';
import { Spinner } from '@/components/ui/spinner';
import { Textarea } from '@/components/ui/textarea';
import { getErrorMessage } from '@/hooks/useAsyncData';
import type {
  IssueCategoryItem,
  TicketItem,
  TicketPriority,
  TicketStatus,
} from '@/types/api';

import {
  PRIORITY_LABELS,
  STATUS_LABELS,
  TicketPriorityBadge,
  TicketStatusBadge,
} from './TicketBadges';

interface TicketDetailsData {
  ticket: TicketItem;
  categories: IssueCategoryItem[];
}

interface TicketFormErrors {
  title?: string;
  description?: string;
}

interface TicketDetailsSheetProps {
  open: boolean;
  ticketId: string | null;
  initialTicket?: TicketItem | null;
  onOpenChange: (open: boolean) => void;
  onUpdated: (ticket: TicketItem) => void | Promise<void>;
  onDeleted: (ticketId: string) => void | Promise<void>;
}

const DATE_FORMATTER: Intl.DateTimeFormat = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'short',
});
export function TicketDetailsSheet({
  open,
  ticketId,
  initialTicket = null,
  onOpenChange,
  onUpdated,
  onDeleted,
}: TicketDetailsSheetProps) {
  const [data, setData] = useState<TicketDetailsData | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [title, setTitle] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [status, setStatus] = useState<TicketStatus>('open');
  const [priority, setPriority] = useState<TicketPriority>('medium');
  const [categoryId, setCategoryId] = useState<string>('');
  const [assigneeUserId, setAssigneeUserId] = useState<string | null>(null);
  const [errors, setErrors] = useState<TicketFormErrors>({});
  const [saving, setSaving] = useState<boolean>(false);
  const [deleting, setDeleting] = useState<boolean>(false);

  useEffect(() => {
    if (!open || !ticketId) return;
    let active = true;
    setLoading(true);
    setError(null);
    Promise.all([
      initialTicket?.id === ticketId
        ? Promise.resolve(initialTicket)
        : ticketsApi.getTicket(ticketId),
      categoriesApi.listCategories(),
    ])
      .then(
        ([ticket, categories]: [
          TicketItem,
          IssueCategoryItem[],
        ]) => {
          if (active) setData({ ticket, categories });
        },
      )
      .catch((caughtError: unknown) => {
        if (active) setError(getErrorMessage(caughtError));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [initialTicket, open, ticketId]);

  useEffect(() => {
    if (!data || data.ticket.id !== ticketId) return;
    setTitle(data.ticket.title);
    setDescription(data.ticket.description);
    setStatus(data.ticket.status);
    setPriority(data.ticket.priority);
    setCategoryId(data.ticket.categoryId);
    setAssigneeUserId(data.ticket.assigneeUserId);
    setErrors({});
  }, [data, ticketId]);

  const clearFieldError = (field: keyof TicketFormErrors): void => {
    setErrors((current: TicketFormErrors) =>
      current[field] ? { ...current, [field]: undefined } : current,
    );
  };

  const handleSave = async (): Promise<void> => {
    if (!ticketId) return;
    const nextErrors: TicketFormErrors = {};
    if (title.trim().length < 2) nextErrors.title = '标题至少输入 2 个字符';
    if (description.trim().length < 5) {
      nextErrors.description = '请补充至少 5 个字符的问题描述';
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setSaving(true);
    try {
      const updated: TicketItem = await ticketsApi.updateTicket(ticketId, {
        title: title.trim(),
        description: description.trim(),
        status,
        priority,
        categoryId,
        assigneeUserId,
      });
      toast.success(`工单 ${updated.ticketNo} 已更新`);
      await onUpdated(updated);
      onOpenChange(false);
    } catch (caughtError: unknown) {
      toast.error(getErrorMessage(caughtError));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (): Promise<void> => {
    if (!ticketId) return;
    setDeleting(true);
    try {
      await ticketsApi.deleteTicket(ticketId);
      toast.success(`工单 ${ticket?.ticketNo ?? ''} 已删除`);
      await onDeleted(ticketId);
      onOpenChange(false);
    } catch (caughtError: unknown) {
      toast.error(getErrorMessage(caughtError));
    } finally {
      setDeleting(false);
    }
  };

  const ticket: TicketItem | null =
    data?.ticket.id === ticketId ? data.ticket : null;

  return (
    <Sheet
      open={open}
      onOpenChange={(nextOpen: boolean) => {
        if (!saving && !deleting) onOpenChange(nextOpen);
      }}
    >
      <SheetContent className="w-full gap-0 p-0 data-[state=closed]:duration-100 data-[state=open]:duration-150 sm:max-w-2xl">
        <SheetHeader className="border-b p-5 pr-12">
          <div className="flex flex-wrap items-center gap-2">
            <SheetTitle>{ticket?.ticketNo ?? '工单详情'}</SheetTitle>
            {ticket ? (
              <>
                <TicketStatusBadge status={ticket.status} />
                <TicketPriorityBadge priority={ticket.priority} />
              </>
            ) : null}
          </div>
          <SheetDescription>
            查看问题上下文，并在同一面板内更新处理信息。
          </SheetDescription>
        </SheetHeader>

        <div className="min-h-0 flex-1 overflow-y-auto p-5">
          {loading && !ticket ? (
            <div className="flex flex-col gap-4" aria-label="正在加载工单详情">
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-52 w-full" />
              <Skeleton className="h-40 w-full" />
            </div>
          ) : null}

          {error ? (
            <Alert variant="destructive">
              <AlertTitle>无法加载工单</AlertTitle>
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          ) : null}

          {ticket && data ? (
            <div className="flex flex-col gap-6">
              <section aria-labelledby="ticket-content-title">
                <h3
                  id="ticket-content-title"
                  className="mb-4 text-sm font-medium"
                >
                  工单内容
                </h3>
                <FieldGroup>
                  <Field data-invalid={Boolean(errors.title)}>
                    <FieldLabel htmlFor="ticket-edit-title">
                      工单标题
                    </FieldLabel>
                    <Input
                      id="ticket-edit-title"
                      value={title}
                      onChange={(event) => {
                        const value: string = event.target.value;
                        setTitle(value);
                        if (value.trim().length >= 2) {
                          clearFieldError('title');
                        }
                      }}
                      maxLength={160}
                      aria-invalid={Boolean(errors.title)}
                    />
                    {errors.title ? (
                      <FieldError>{errors.title}</FieldError>
                    ) : null}
                  </Field>
                  <Field data-invalid={Boolean(errors.description)}>
                    <FieldLabel htmlFor="ticket-edit-description">
                      问题描述
                    </FieldLabel>
                    <Textarea
                      id="ticket-edit-description"
                      value={description}
                      onChange={(event) => {
                        const value: string = event.target.value;
                        setDescription(value);
                        if (value.trim().length >= 5) {
                          clearFieldError('description');
                        }
                      }}
                      maxLength={5000}
                      rows={6}
                      aria-invalid={Boolean(errors.description)}
                    />
                    {errors.description ? (
                      <FieldError>{errors.description}</FieldError>
                    ) : null}
                  </Field>
                </FieldGroup>
              </section>

              <Separator />

              <section aria-labelledby="ticket-metadata-title">
                <h3
                  id="ticket-metadata-title"
                  className="mb-4 text-sm font-medium"
                >
                  基本信息
                </h3>
                <dl className="grid gap-4 text-sm sm:grid-cols-2">
                  <div>
                    <dt className="text-muted-foreground">提交人</dt>
                    <dd className="mt-1.5">
                      <UserDisplay
                        value={[ticket.reporterUserId]}
                        size="small"
                      />
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">创建时间</dt>
                    <dd className="mt-1.5">
                      {DATE_FORMATTER.format(new Date(ticket.createdAt))}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">最后更新</dt>
                    <dd className="mt-1.5">
                      {DATE_FORMATTER.format(new Date(ticket.updatedAt))}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">解决时间</dt>
                    <dd className="mt-1.5">
                      {ticket.resolvedAt
                        ? DATE_FORMATTER.format(
                            new Date(ticket.resolvedAt),
                          )
                        : '尚未解决'}
                    </dd>
                  </div>
                </dl>
              </section>

              <Separator />

              <section aria-labelledby="ticket-processing-title">
                <h3
                  id="ticket-processing-title"
                  className="mb-4 text-sm font-medium"
                >
                  处理信息
                </h3>
                <FieldGroup className="grid gap-4 sm:grid-cols-2">
                  <Field>
                    <FieldLabel>状态</FieldLabel>
                    <Select
                      value={status}
                      onValueChange={(value: string) =>
                        setStatus(value as TicketStatus)
                      }
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {Object.entries(STATUS_LABELS).map(
                            ([value, label]: [string, string]) => (
                              <SelectItem key={value} value={value}>
                                {label}
                              </SelectItem>
                            ),
                          )}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>

                  <Field>
                    <FieldLabel>优先级</FieldLabel>
                    <Select
                      value={priority}
                      onValueChange={(value: string) =>
                        setPriority(value as TicketPriority)
                      }
                    >
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {Object.entries(PRIORITY_LABELS).map(
                            ([value, label]: [string, string]) => (
                              <SelectItem key={value} value={value}>
                                {label}
                              </SelectItem>
                            ),
                          )}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>

                  <Field>
                    <FieldLabel>问题分类</FieldLabel>
                    <Select value={categoryId} onValueChange={setCategoryId}>
                      <SelectTrigger className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectGroup>
                          {data.categories
                            .filter(
                              (category: IssueCategoryItem) =>
                                category.isActive,
                            )
                            .map((category: IssueCategoryItem) => (
                              <SelectItem
                                key={category.id}
                                value={category.id}
                              >
                                {category.name}
                              </SelectItem>
                            ))}
                        </SelectGroup>
                      </SelectContent>
                    </Select>
                  </Field>

                  <Field>
                    <FieldLabel>责任人</FieldLabel>
                    <UserSelect
                      value={assigneeUserId}
                      onChange={setAssigneeUserId}
                      placeholder="选择责任人"
                    />
                  </Field>
                </FieldGroup>
              </section>
            </div>
          ) : null}
        </div>

        <SheetFooter className="border-t bg-background sm:flex-row sm:justify-between">
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button
                type="button"
                variant="destructive"
                disabled={!ticket || saving || deleting}
              >
                <Trash2Icon data-icon="inline-start" />
                删除工单
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>确认删除该工单？</AlertDialogTitle>
                <AlertDialogDescription>
                  删除后无法恢复，工单记录将被永久移除。
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel disabled={deleting}>
                  取消
                </AlertDialogCancel>
                <AlertDialogAction asChild>
                  <Button
                    type="button"
                    variant="destructive"
                    disabled={deleting}
                    onClick={() => void handleDelete()}
                  >
                    {deleting ? (
                      <Spinner data-icon="inline-start" />
                    ) : (
                      <Trash2Icon data-icon="inline-start" />
                    )}
                    {deleting ? '正在删除' : '确认删除'}
                  </Button>
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              disabled={saving || deleting}
              onClick={() => onOpenChange(false)}
            >
              取消
            </Button>
            <Button
              type="button"
              disabled={!ticket || saving || deleting}
              onClick={() => void handleSave()}
            >
              {saving ? (
                <Spinner data-icon="inline-start" />
              ) : (
                <SaveIcon data-icon="inline-start" />
              )}
              {saving ? '正在保存' : '保存变更'}
            </Button>
          </div>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
