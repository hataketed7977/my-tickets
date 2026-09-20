import {
  FolderTreeIcon,
  LogOutIcon,
  TicketCheckIcon,
} from 'lucide-react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';

import { useAuth } from '@/auth/AuthContext';
import { UserDisplay } from '@/components/users/UserDisplay';
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from '@/components/ui/sidebar';
import { Separator } from '@/components/ui/separator';

interface NavigationItem {
  label: string;
  path: string;
  icon: React.ComponentType;
}

const NAVIGATION: NavigationItem[] = [
  { label: '工单管理', path: '/tickets', icon: TicketCheckIcon },
  { label: '问题分类', path: '/categories', icon: FolderTreeIcon },
];

const PAGE_TITLES: Record<string, string> = {
  '/tickets': '工单管理',
  '/tickets/new': '新建工单',
  '/categories': '问题分类',
};

const Layout = () => {
  const location = useLocation();
  const { currentUser, logout } = useAuth();
  const pageTitle: string = location.pathname.startsWith('/tickets/')
    ? PAGE_TITLES[location.pathname] ?? '工单详情'
    : PAGE_TITLES[location.pathname] ?? '工单中心';

  const renderNavigation = (items: NavigationItem[]) =>
    items.map((item: NavigationItem) => {
      const Icon = item.icon;
      const active: boolean = location.pathname.startsWith(item.path);
      return (
        <SidebarMenuItem key={item.path}>
          <SidebarMenuButton
            asChild
            isActive={active}
            tooltip={item.label}
            className="transition-[color,background-color,width,height,padding] duration-100 ease-out"
          >
            <NavLink to={item.path}>
              <Icon />
              <span>{item.label}</span>
            </NavLink>
          </SidebarMenuButton>
        </SidebarMenuItem>
      );
    });

  return (
    <SidebarProvider>
      <Sidebar collapsible="icon">
        <SidebarHeader className="p-3">
          <NavLink
            to="/"
            className="flex min-w-0 items-center gap-3 rounded-md px-1 py-1 outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
          >
            <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-sidebar-primary text-sidebar-primary-foreground">
              <TicketCheckIcon className="size-4" />
            </span>
            <span className="min-w-0 group-data-[collapsible=icon]:hidden">
              <span className="block truncate text-sm font-semibold">
                工单中心
              </span>
              <span className="block truncate text-xs text-sidebar-foreground/60">
                Service Desk
              </span>
            </span>
          </NavLink>
        </SidebarHeader>
        <SidebarContent>
          <SidebarGroup>
            <SidebarGroupLabel>功能</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu>{renderNavigation(NAVIGATION)}</SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        </SidebarContent>
        <SidebarFooter className="p-3">
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                size="lg"
                tooltip={currentUser?.name ?? '当前用户'}
              >
                {currentUser ? (
                  <UserDisplay
                    value={[currentUser.id]}
                    size="small"
                    showLabel={false}
                  />
                ) : (
                  <span className="size-6 rounded-full bg-muted" />
                )}
                <span className="min-w-0">
                  <span className="block truncate font-medium">
                    {currentUser?.name ?? '当前用户'}
                  </span>
                  <span className="block truncate text-xs text-sidebar-foreground/60">
                    当前登录用户
                  </span>
                </span>
              </SidebarMenuButton>
            </SidebarMenuItem>
            <SidebarMenuItem>
              <SidebarMenuButton
                tooltip="退出登录"
                onClick={() => void logout()}
              >
                <LogOutIcon />
                <span>退出登录</span>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarFooter>
        <SidebarRail />
      </Sidebar>
      <SidebarInset className="min-w-0 bg-muted/20">
        <div className="sticky top-0 flex h-12 items-center gap-3 border-b bg-background/95 px-4 backdrop-blur sm:px-6">
          <SidebarTrigger />
          <Separator orientation="vertical" className="h-4" />
          <span className="truncate text-sm font-medium">{pageTitle}</span>
        </div>
        <div className="relative min-h-[calc(100svh-3rem)] min-w-0 flex-1 overflow-x-hidden">
          <div
            key={location.pathname}
            className="route-enter min-h-[calc(100svh-3rem)]"
          >
            <Outlet />
          </div>
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
};

export default Layout;
