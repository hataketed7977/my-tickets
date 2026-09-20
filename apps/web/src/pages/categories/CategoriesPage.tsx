import {
  FolderPlusIcon,
  MoreHorizontalIcon,
  PencilIcon,
  PlusIcon,
  Trash2Icon,
} from 'lucide-react';
import { FormEvent, useState } from 'react';
import { toast } from 'sonner';

import { categoriesApi } from '@/api';
import { PageError, PageLoading } from '@/components/work-orders/AsyncState';
import { PageHeader } from '@/components/work-orders/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Empty,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from '@/components/ui/empty';
import {
  Field,
  FieldGroup,
  FieldLabel,
} from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Spinner } from '@/components/ui/spinner';
import { Switch } from '@/components/ui/switch';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Textarea } from '@/components/ui/textarea';
import { getErrorMessage, useAsyncData } from '@/hooks/useAsyncData';
import type { IssueCategoryItem } from '@/types/api';

export default function CategoriesPage() {
  const [dialogOpen, setDialogOpen] = useState<boolean>(false);
  const [editing, setEditing] = useState<IssueCategoryItem | null>(null);
  const [name, setName] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [categoryToDelete, setCategoryToDelete] =
    useState<IssueCategoryItem | null>(null);
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [deleting, setDeleting] = useState<boolean>(false);
  const {
    data: categories,
    error,
    loading,
    refresh,
  } = useAsyncData<IssueCategoryItem[]>(categoriesApi.listCategories, []);

  const openCreateDialog = (): void => {
    setEditing(null);
    setName('');
    setDescription('');
    setDialogOpen(true);
  };

  const openEditDialog = (category: IssueCategoryItem): void => {
    setEditing(category);
    setName(category.name);
    setDescription(category.description);
    setDialogOpen(true);
  };

  const handleSubmit = async (
    event: FormEvent<HTMLFormElement>,
  ): Promise<void> => {
    event.preventDefault();
    if (name.trim().length < 2) {
      toast.error('分类名称至少输入 2 个字符');
      return;
    }
    setSubmitting(true);
    try {
      if (editing) {
        await categoriesApi.updateCategory(editing.id, {
          name: name.trim(),
          description: description.trim(),
        });
        toast.success('问题分类已更新');
      } else {
        await categoriesApi.createCategory({
          name: name.trim(),
          description: description.trim(),
        });
        toast.success('问题分类已创建');
      }
      setDialogOpen(false);
      await refresh();
    } catch (caughtError: unknown) {
      toast.error(getErrorMessage(caughtError));
    } finally {
      setSubmitting(false);
    }
  };

  const handleActiveChange = async (
    category: IssueCategoryItem,
    isActive: boolean,
  ): Promise<void> => {
    try {
      await categoriesApi.updateCategory(category.id, { isActive });
      toast.success(isActive ? '分类已启用' : '分类已停用');
      await refresh();
    } catch (caughtError: unknown) {
      toast.error(getErrorMessage(caughtError));
    }
  };

  const handleDelete = async (): Promise<void> => {
    if (!categoryToDelete) return;
    setDeleting(true);
    try {
      await categoriesApi.deleteCategory(categoryToDelete.id);
      toast.success('问题分类已删除');
      setCategoryToDelete(null);
      await refresh();
    } catch (caughtError: unknown) {
      toast.error(getErrorMessage(caughtError));
    } finally {
      setDeleting(false);
    }
  };

  if (loading && !categories) return <PageLoading />;
  if (error || !categories) {
    return <PageError message={error ?? '无法加载分类'} onRetry={refresh} />;
  }

  return (
    <div className="min-h-full">
      <PageHeader
        title="问题分类"
        description="维护工单创建和筛选时使用的问题分类。"
        actions={
          <Button type="button" onClick={openCreateDialog}>
            <PlusIcon data-icon="inline-start" />
            新建分类
          </Button>
        }
      />
      <div className="p-4 sm:p-6">
        <section className="overflow-hidden rounded-lg border bg-background">
          {categories.length === 0 ? (
            <Empty>
              <EmptyHeader>
                <EmptyMedia variant="icon">
                  <FolderPlusIcon />
                </EmptyMedia>
                <EmptyTitle>尚未配置问题分类</EmptyTitle>
                <EmptyDescription>
                  创建分类后即可用于工单登记。
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>分类</TableHead>
                  <TableHead>状态</TableHead>
                  <TableHead className="w-16 text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {categories.map((category: IssueCategoryItem) => (
                  <TableRow key={category.id}>
                    <TableCell className="max-w-md">
                      <p className="truncate font-medium">{category.name}</p>
                      <p className="mt-1 line-clamp-2 whitespace-normal text-xs text-muted-foreground">
                        {category.description || '暂无说明'}
                      </p>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Switch
                          checked={category.isActive}
                          onCheckedChange={(checked: boolean) =>
                            void handleActiveChange(category, checked)
                          }
                          aria-label={`${category.name}启用状态`}
                        />
                        <Badge
                          variant={
                            category.isActive ? 'secondary' : 'outline'
                          }
                        >
                          {category.isActive ? '启用' : '停用'}
                        </Badge>
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            aria-label={`${category.name}操作`}
                          >
                            <MoreHorizontalIcon />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuGroup>
                            <DropdownMenuItem
                              onSelect={() => openEditDialog(category)}
                            >
                              <PencilIcon />
                              编辑
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              variant="destructive"
                              onSelect={() => setCategoryToDelete(category)}
                            >
                              <Trash2Icon />
                              删除
                            </DropdownMenuItem>
                          </DropdownMenuGroup>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </section>
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? '编辑问题分类' : '新建问题分类'}</DialogTitle>
            <DialogDescription>
              分类用于组织和筛选工单。
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit}>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="category-name">分类名称</FieldLabel>
                <Input
                  id="category-name"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  maxLength={100}
                  required
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="category-description">说明</FieldLabel>
                <Textarea
                  id="category-description"
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                  maxLength={500}
                  rows={3}
                />
              </Field>
              <DialogFooter>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setDialogOpen(false)}
                >
                  取消
                </Button>
                <Button type="submit" disabled={submitting}>
                  {submitting ? '正在保存' : '保存'}
                </Button>
              </DialogFooter>
            </FieldGroup>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={Boolean(categoryToDelete)}
        onOpenChange={(open: boolean) => {
          if (!open && !deleting) setCategoryToDelete(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除该问题分类？</AlertDialogTitle>
            <AlertDialogDescription>
              “{categoryToDelete?.name}”删除后无法恢复。已被工单使用的分类不能删除。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
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
    </div>
  );
}
