import { AlertCircleIcon, RefreshCwIcon } from 'lucide-react';

import {
  Alert,
  AlertDescription,
  AlertTitle,
} from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';

export function PageLoading() {
  return (
    <div className="flex flex-col gap-4 p-4 sm:p-6" aria-label="正在加载">
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}

export function PageError({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="p-4 sm:p-6">
      <Alert variant="destructive">
        <AlertCircleIcon />
        <AlertTitle>内容加载失败</AlertTitle>
        <AlertDescription className="flex flex-col items-start gap-3">
          <span>{message}</span>
          <Button type="button" variant="outline" size="sm" onClick={onRetry}>
            <RefreshCwIcon data-icon="inline-start" />
            重试
          </Button>
        </AlertDescription>
      </Alert>
    </div>
  );
}
