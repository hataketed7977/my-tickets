import { afterEach, describe, expect, it, vi } from 'vitest';
import { rmSync } from 'node:fs';
import {
  clearCredentials,
  loadConfig,
  resolveApiBaseUrl,
  saveConfig,
} from './config.js';

const { tmpDir } = vi.hoisted(() => ({
  tmpDir: '/tmp/my-tickets-cli-test',
}));

vi.mock('node:os', () => ({
  homedir: () => tmpDir,
}));

describe('resolveApiBaseUrl', () => {
  it('uses flag value when provided', () => {
    expect(resolveApiBaseUrl('http://flag:9000')).toBe('http://flag:9000');
  });

  it('falls back to env value when flag is absent', () => {
    expect(resolveApiBaseUrl(undefined, 'http://env:8000')).toBe(
      'http://env:8000',
    );
  });

  it('falls back to config value when flag and env are absent', () => {
    expect(
      resolveApiBaseUrl(undefined, undefined, 'http://config:7000'),
    ).toBe('http://config:7000');
  });

  it('uses default when nothing is configured', () => {
    expect(resolveApiBaseUrl()).toBe('http://localhost:55888');
  });

  it('strips trailing slash from all sources', () => {
    expect(resolveApiBaseUrl('http://a:1/')).toBe('http://a:1');
    expect(resolveApiBaseUrl(undefined, 'http://b:2/')).toBe('http://b:2');
    expect(resolveApiBaseUrl(undefined, undefined, 'http://c:3/')).toBe(
      'http://c:3',
    );
  });
});

describe('config persistence', () => {
  afterEach(() => {
    rmSync(tmpDir, { recursive: true, force: true });
  });

  it('returns empty object when config file does not exist', () => {
    expect(loadConfig()).toEqual({});
  });

  it('saves and loads config correctly', () => {
    saveConfig({
      apiBaseUrl: 'http://localhost:55888',
      token: 'test-token',
      user: { id: 'u1', name: 'Test User' },
    });

    const loaded = loadConfig();
    expect(loaded.apiBaseUrl).toBe('http://localhost:55888');
    expect(loaded.token).toBe('test-token');
    expect(loaded.user).toEqual({ id: 'u1', name: 'Test User' });
  });

  it('clearCredentials removes token and user but preserves apiBaseUrl', () => {
    saveConfig({
      apiBaseUrl: 'http://localhost:55888',
      token: 'test-token',
      user: { id: 'u1', name: 'Test User' },
    });

    clearCredentials();

    const loaded = loadConfig();
    expect(loaded.apiBaseUrl).toBe('http://localhost:55888');
    expect(loaded.token).toBeUndefined();
    expect(loaded.user).toBeUndefined();
  });
});
