import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { homedir } from 'node:os';

export interface CliConfig {
  apiBaseUrl?: string;
  token?: string;
  user?: {
    id: string;
    name: string;
  };
}

const CONFIG_DIR = join(homedir(), '.my-tickets');
const CONFIG_FILE = join(CONFIG_DIR, 'config.json');

export function resolveApiBaseUrl(
  flagValue?: string,
  envValue?: string,
  configValue?: string,
): string {
  if (flagValue) return flagValue.replace(/\/$/, '');
  if (envValue) return envValue.replace(/\/$/, '');
  if (configValue) return configValue.replace(/\/$/, '');
  return 'http://localhost:55888';
}

export function loadConfig(): CliConfig {
  if (!existsSync(CONFIG_FILE)) {
    return {};
  }
  try {
    const raw = readFileSync(CONFIG_FILE, 'utf-8');
    return JSON.parse(raw) as CliConfig;
  } catch {
    return {};
  }
}

export function saveConfig(config: CliConfig): void {
  mkdirSync(CONFIG_DIR, { recursive: true });
  writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2) + '\n', 'utf-8');
}

export function clearCredentials(): void {
  const config = loadConfig();
  const next: CliConfig = { apiBaseUrl: config.apiBaseUrl };
  saveConfig(next);
}
