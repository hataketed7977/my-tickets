import { ArrowLeftIcon } from 'lucide-react';
import { Link } from 'react-router-dom';

import { Button } from '@/components/ui/button';
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyTitle,
} from '@/components/ui/empty';

const NotFound = () => {
  return (
    <main className="flex min-h-screen items-center justify-center p-6">
      <Empty>
        <EmptyHeader>
          <EmptyTitle>页面不存在</EmptyTitle>
          <EmptyDescription>链接可能已失效或页面已被移动。</EmptyDescription>
        </EmptyHeader>
        <EmptyContent>
          <Button asChild>
            <Link to="/tickets">
              <ArrowLeftIcon data-icon="inline-start" />
              返回工单管理
            </Link>
          </Button>
        </EmptyContent>
      </Empty>
    </main>
  );
};

export default NotFound;
