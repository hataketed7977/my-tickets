import { useAuth } from '@/auth/AuthContext';
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from '@/components/ui/avatar';
import type { AppUser } from '@/types/api';

interface UserDisplayProps {
  value?: string[];
  size?: 'small' | 'medium' | 'large';
  showLabel?: boolean;
}

const SIZE_CLASSES: Record<NonNullable<UserDisplayProps['size']>, string> = {
  small: 'size-6',
  medium: 'size-8',
  large: 'size-10',
};

export function UserDisplay({
  value = [],
  size = 'medium',
  showLabel = true,
}: UserDisplayProps) {
  const { users } = useAuth();
  const selectedUsers: AppUser[] = value
    .map((id: string) => users.find((user: AppUser) => user.id === id))
    .filter((user: AppUser | undefined): user is AppUser => Boolean(user));

  if (selectedUsers.length === 0) {
    return <span className="text-muted-foreground">未知用户</span>;
  }

  return (
    <div className="flex min-w-0 flex-wrap items-center gap-2">
      {selectedUsers.map((user: AppUser) => (
        <div key={user.id} className="flex min-w-0 items-center gap-2">
          <Avatar className={SIZE_CLASSES[size]}>
            {user.avatarUrl ? (
              <AvatarImage src={user.avatarUrl} alt={user.name} />
            ) : null}
            <AvatarFallback>{user.name.slice(0, 1)}</AvatarFallback>
          </Avatar>
          {showLabel ? (
            <span className="truncate text-sm">{user.name}</span>
          ) : null}
        </div>
      ))}
    </div>
  );
}
