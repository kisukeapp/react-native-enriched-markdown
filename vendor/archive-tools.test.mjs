import assert from 'node:assert/strict';
import test from 'node:test';

import { archiveCommand, extractArchive } from './archive-tools.mjs';

test('uses System32 bsdtar directly for Windows tarballs', () => {
  const calls = [];
  extractArchive('C:\\Temp\\runtime.tar.gz', 'C:\\Temp\\out', 'tgz', {
    platform: 'win32',
    env: { SystemRoot: 'C:\\Windows' },
    existsSync: () => true,
    spawn: (executable, args, options) => {
      calls.push({ executable, args, options });
      return { status: 0, signal: null };
    },
  });

  assert.deepEqual(calls, [{
    executable: 'C:\\Windows\\System32\\tar.exe',
    args: ['-xzf', 'C:\\Temp\\runtime.tar.gz', '-C', 'C:\\Temp\\out'],
    options: { stdio: 'inherit' },
  }]);
});

test('uses System32 bsdtar directly for Windows zip files', () => {
  const calls = [];
  extractArchive('D:\\Temp\\ratex.zip', 'D:\\Temp\\out', 'zip', {
    platform: 'win32',
    env: { WINDIR: 'D:\\Win' },
    existsSync: () => true,
    spawn: (executable, args) => {
      calls.push({ executable, args });
      return { status: 0, signal: null };
    },
  });

  assert.deepEqual(calls, [{
    executable: 'D:\\Win\\System32\\tar.exe',
    args: ['-xf', 'D:\\Temp\\ratex.zip', '-C', 'D:\\Temp\\out'],
  }]);
});

test('keeps native tar and unzip commands on non-Windows platforms', () => {
  assert.deepEqual(archiveCommand('tgz', { platform: 'linux' }), {
    executable: 'tar',
    kind: 'tar',
  });
  assert.deepEqual(archiveCommand('zip', { platform: 'darwin' }), {
    executable: 'unzip',
    kind: 'unzip',
  });
});

test('reports a missing Windows bsdtar before extraction', () => {
  assert.throws(
    () => archiveCommand('tgz', {
      platform: 'win32',
      env: { SystemRoot: 'C:\\Windows' },
      existsSync: () => false,
    }),
    /Windows bsdtar not found at C:\\Windows\\System32\\tar\.exe/
  );
});

test('reports extraction process failures', () => {
  assert.throws(
    () => extractArchive('/tmp/archive.tgz', '/tmp/out', 'tgz', {
      platform: 'linux',
      spawn: () => ({ status: 2, signal: null }),
    }),
    /tar failed to extract \/tmp\/archive\.tgz \(status 2\)/
  );
});
