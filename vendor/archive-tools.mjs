import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

function windowsTarPath(env, existsSync) {
  const systemRoot = env.SystemRoot ?? env.WINDIR ?? 'C:\\Windows';
  const executable = path.win32.join(systemRoot, 'System32', 'tar.exe');
  if (!existsSync(executable)) {
    throw new Error(`Windows bsdtar not found at ${executable}`);
  }
  return executable;
}

export function archiveCommand(
  format,
  {
    platform = process.platform,
    env = process.env,
    existsSync = fs.existsSync,
  } = {}
) {
  if (format !== 'tgz' && format !== 'zip') {
    throw new Error(`unsupported archive format: ${format}`);
  }

  if (platform === 'win32') {
    return { executable: windowsTarPath(env, existsSync), kind: 'tar' };
  }

  return format === 'zip'
    ? { executable: 'unzip', kind: 'unzip' }
    : { executable: 'tar', kind: 'tar' };
}

export function extractArchive(
  archive,
  destination,
  format,
  {
    platform = process.platform,
    env = process.env,
    existsSync = fs.existsSync,
    spawn = spawnSync,
    stdio = 'inherit',
  } = {}
) {
  const command = archiveCommand(format, { platform, env, existsSync });
  const args = command.kind === 'unzip'
    ? ['-q', '-o', archive, '-d', destination]
    : [format === 'tgz' ? '-xzf' : '-xf', archive, '-C', destination];
  const result = spawn(command.executable, args, { stdio });

  if (result.error) {
    throw new Error(`${command.executable} failed to start: ${result.error.message}`);
  }
  if (result.status !== 0) {
    const detail = result.signal ? `signal ${result.signal}` : `status ${result.status}`;
    throw new Error(`${command.executable} failed to extract ${archive} (${detail})`);
  }
}
