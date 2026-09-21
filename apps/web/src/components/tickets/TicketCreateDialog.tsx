import { SendIcon } from 'lucide-react';
import { FormEvent, useEffect, useState } from 'react';
import { toast } from 'sonner';

import { categoriesApi, ticketsApi } from '@/api';
import {
  Alert,
  AlertDescription,
  AlertTitle,
} from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Field,
  FieldDescription,
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
import { Skeleton } from '@/components/ui/skeleton';
import { Spinner } from '@/components/ui/spinner';
import { Textarea } from '@/components/ui/textarea';
import { getErrorMessage } from '@/lib/errors';
import type {
  IssueCategoryItem,
  TicketItem,
  TicketPriority,
} from '@/types/api';

interface TicketFormErrors {
  title?: string;
  description?: string;
  categoryId?: string;
}

interface TicketCreateDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (ticket: TicketItem) => void | Promise<void>;
}

export function TicketCreateDialog({
  open,
  onOpenChange,
  onCreated,
}: TicketCreateDialogProps) {
  const [title, setTitle] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [categoryId, setCategoryId] = useState<string>('');
  const [priority, setPriority] = useState<TicketPriority>('medium');
  const [categories, setCategories] = useState<IssueCategoryItem[] | null>(
    null,
  );
  const [categoriesError, setCategoriesError] = useState<string | null>(null);
  const [loadingCategories, setLoadingCategories] = useState<boolean>(false);
  const [errors, setErrors] = useState<TicketFormErrors>({});
  const [submitting, setSubmitting] = useState<boolean>(false);

  useEffect(() => {
    if (!open) return;
    let active = true;
    setLoadingCategories(true);
    setCategoriesError(null);
    categoriesApi
      .listCategories()
      .then((result: IssueCategoryItem[]) => {
        if (active) setCategories(result);
      })
      .catch((error: unknown) => {
        if (active) setCategoriesError(getErrorMessage(error));
      })
      .finally(() => {
        if (active) setLoadingCategories(false);
      });
    return () => {
      active = false;
    };
  }, [open]);

  const activeCategories: IssueCategoryItem[] = (categories ?? []).filter(
    (category: IssueCategoryItem) => category.isActive,
  );

  const clearFieldError = (field: keyof TicketFormErrors): void => {
    setErrors((current: TicketFormErrors) =>
      current[field] ? { ...current, [field]: undefined } : current,
    );
  };

  const resetForm = (): void => {
    setTitle('');
    setDescription('');
    setCategoryId('');
    setPriority('medium');
    setErrors({});
  };

  const validate = (): TicketFormErrors => {
    const nextErrors: TicketFormErrors = {};
    if (title.trim().length < 2) nextErrors.title = '标题至少输入 2 个字符';
    if (description.trim().length < 5) {
      nextErrors.description = '请补充至少 5 个字符的问题描述';
    }
    if (!categoryId) nextErrors.categoryId = '请选择问题分类';
    return nextErrors;
  };

  const handleSubmit = async (
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> => {
    event.preventDefault();
    const nextErrors: TicketFormErrors = validate();
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    setSubmitting(true);
    try {
      const ticket: TicketItem = await ticketsApi.createTicket({
        title: title.trim(),
        description: description.trim(),
        categoryId,
        priority,
      });
      toast.success(`工单 ${ticket.ticketNo} 已创建`);
      resetForm();
      onOpenChange(false);
      await onCreated(ticket);
    } catch (error: unknown) {
      toast.error(getErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen: boolean) => {
        if (!submitting) onOpenChange(nextOpen);
      }}
    >
      <DialogContent className="max-h-[calc(100svh-2rem)] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>新建工单</DialogTitle>
          <DialogDescription>
            补充问题信息并选择对应分类。
          </DialogDescription>
        </DialogHeader>

        <form id="ticket-create-form" onSubmit={handleSubmit}>
          <FieldGroup>
            <Field data-invalid={Boolean(errors.title)}>
              <FieldLabel htmlFor="ticket-create-title">工单标题</FieldLabel>
              <Input
                id="ticket-create-title"
                value={title}
                onChange={(event) => {
                  const value: string = event.target.value;
                  setTitle(value);
                  if (value.trim().length >= 2) clearFieldError('title');
                }}
                placeholder="简要说明遇到的问题"
                maxLength={160}
                aria-invalid={Boolean(errors.title)}
                autoFocus
              />
              {errors.title ? <FieldError>{errors.title}</FieldError> : null}
            </Field>

            <Field data-invalid={Boolean(errors.description)}>
              <FieldLabel htmlFor="ticket-create-description">
                问题描述
              </FieldLabel>
              <Textarea
                id="ticket-create-description"
                value={description}
                onChange={(event) => {
                  const value: string = event.target.value;
                  setDescription(value);
                  if (value.trim().length >= 5) {
                    clearFieldError('description');
                  }
                }}
                placeholder="说明问题表现、发生时间、影响范围及已尝试的处理方式"
                maxLength={5000}
                rows={6}
                aria-invalid={Boolean(errors.description)}
              />
              <FieldDescription>
                建议包含复现路径与影响范围，便于快速处理。
              </FieldDescription>
              {errors.description ? (
                <FieldError>{errors.description}</FieldError>
              ) : null}
            </Field>

            <div className="grid gap-4 sm:grid-cols-2">
              <Field data-invalid={Boolean(errors.categoryId)}>
                <FieldLabel>问题分类</FieldLabel>
                {loadingCategories && !categories ? (
                  <Skeleton className="h-9 w-full" />
                ) : (
                  <Select
                    value={categoryId}
                    onValueChange={(value: string) => {
                      setCategoryId(value);
                      clearFieldError('categoryId');
                    }}
                    disabled={Boolean(categoriesError)}
                  >
                    <SelectTrigger
                      className="w-full"
                      aria-invalid={Boolean(errors.categoryId)}
                    >
                      <SelectValue placeholder="选择问题分类" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectGroup>
                        {activeCategories.map(
                          (category: IssueCategoryItem) => (
                            <SelectItem
                              key={category.id}
                              value={category.id}
                            >
                              {category.name}
                            </SelectItem>
                          ),
                        )}
                      </SelectGroup>
                    </SelectContent>
                  </Select>
                )}
                {errors.categoryId ? (
                  <FieldError>{errors.categoryId}</FieldError>
                ) : null}
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
                      <SelectItem value="low">低</SelectItem>
                      <SelectItem value="medium">普通</SelectItem>
                      <SelectItem value="high">高</SelectItem>
                      <SelectItem value="urgent">紧急</SelectItem>
                    </SelectGroup>
                  </SelectContent>
                </Select>
              </Field>
            </div>

            {categoriesError ? (
              <Alert variant="destructive">
                <AlertTitle>无法加载问题分类</AlertTitle>
                <AlertDescription>{categoriesError}</AlertDescription>
              </Alert>
            ) : null}

          </FieldGroup>
        </form>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            disabled={submitting}
            onClick={() => onOpenChange(false)}
          >
            取消
          </Button>
          <Button
            type="submit"
            form="ticket-create-form"
            disabled={submitting || loadingCategories || Boolean(categoriesError)}
          >
            {submitting ? (
              <Spinner data-icon="inline-start" />
            ) : (
              <SendIcon data-icon="inline-start" />
            )}
            {submitting ? '正在提交' : '提交工单'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
