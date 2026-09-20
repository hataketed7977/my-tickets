import { useAuth } from '@/auth/AuthContext';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { AppUser } from '@/types/api';

interface UserSelectProps {
  value: string | null;
  onChange: (value: string | null) => void;
  disabled?: boolean;
  placeholder?: string;
}

const EMPTY_USER_VALUE = '__none__';

export function UserSelect({
  value,
  onChange,
  disabled = false,
  placeholder = '请选择用户',
}: UserSelectProps) {
  const { users } = useAuth();
  return (
    <Select
      value={value ?? EMPTY_USER_VALUE}
      onValueChange={(nextValue: string) =>
        onChange(nextValue === EMPTY_USER_VALUE ? null : nextValue)
      }
      disabled={disabled}
    >
      <SelectTrigger className="w-full">
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        <SelectGroup>
          <SelectItem value={EMPTY_USER_VALUE}>暂不指定</SelectItem>
          {users.map((user: AppUser) => (
            <SelectItem key={user.id} value={user.id}>
              {user.name}
            </SelectItem>
          ))}
        </SelectGroup>
      </SelectContent>
    </Select>
  );
}
