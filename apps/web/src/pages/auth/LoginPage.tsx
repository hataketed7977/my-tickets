import { LogInIcon, TicketCheckIcon, TriangleAlertIcon } from 'lucide-react';

import { useAuth } from '@/auth/AuthContext';
import {
  Alert,
  AlertDescription,
  AlertTitle,
} from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { apiUrl } from '@/api/request';

export default function LoginPage() {
  const { config } = useAuth();

  return (
    <main className="flex min-h-screen items-center justify-center bg-muted/30 p-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <div className="mb-2 flex size-10 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <TicketCheckIcon className="size-5" />
          </div>
          <CardTitle>登录工单中心</CardTitle>
          <CardDescription>
            使用企业飞书账号完成身份验证。
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {config?.feishuConfigured ? (
            <Button asChild>
              <a href={apiUrl('/api/auth/feishu')}>
                <LogInIcon data-icon="inline-start" />
                使用飞书登录
              </a>
            </Button>
          ) : (
            <Alert variant="destructive">
              <TriangleAlertIcon />
              <AlertTitle>飞书 SSO 尚未配置</AlertTitle>
              <AlertDescription>
                请在服务端环境变量中配置应用 ID、应用密钥和 OAuth 回调地址。
              </AlertDescription>
            </Alert>
          )}
          <p className="text-xs leading-5 text-muted-foreground">
            应用使用 OAuth 2.0 授权码流程，不会保存飞书密码。
          </p>
        </CardContent>
      </Card>
    </main>
  );
}
