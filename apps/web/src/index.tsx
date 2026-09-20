import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ErrorBoundary } from 'react-error-boundary';

import { AuthProvider, useAuth } from './auth/AuthContext';
import RoutesComponent from './app';
import LoginPage from './pages/auth/LoginPage';
import './index.css';
import { Button } from '@/components/ui/button';
import { Toaster } from '@/components/ui/sonner';
import { Spinner } from '@/components/ui/spinner';

const AuthenticatedApp = () => {
  const { currentUser, loading } = useAuth();

  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner className="size-6" />
        <span className="sr-only">正在加载</span>
      </div>
    );
  }

  return currentUser ? <RoutesComponent /> : <LoginPage />;
};

const MainApp = () => {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ErrorBoundary
          fallbackRender={({ resetErrorBoundary }) => (
            <main className="flex min-h-screen flex-col items-center justify-center gap-4 p-6 text-center">
              <h1 className="text-xl font-semibold">页面加载失败</h1>
              <p className="text-sm text-muted-foreground">
                请刷新页面或稍后重试。
              </p>
              <Button
                type="button"
                variant="outline"
                onClick={resetErrorBoundary}
              >
                重试
              </Button>
            </main>
          )}
        >
          <AuthenticatedApp />
          <Toaster />
        </ErrorBoundary>
      </AuthProvider>
    </BrowserRouter>
  );
};

createRoot(document.getElementById('root')!).render(<MainApp />);
