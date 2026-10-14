#!/usr/bin/env node
// prepack/postpack hooks for the library package. Plain Node so packing works
// wherever Node does: pnpm and other package managers run `prepack` when they
// install this package from git, including on Windows machines without bash.

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const LOG = '[react-native-enriched-markdown]';

const SHIPPED_VENDOR_FILES = [
  'ratex-version.json',
  'archive-tools.mjs',
  'vendor-grammars.mjs',
  'vendor-ratex.mjs',
];

export function publishPaths(repoRoot) {
  return {
    repoRoot,
    rnPkg: path.join(repoRoot, 'packages', 'react-native-enriched-markdown'),
    coreCpp: path.join(repoRoot, 'packages', 'core', 'cpp'),
    vendorDir: path.join(repoRoot, 'vendor'),
  };
}

function remove(target) {
  // lstat-based: a `cpp` symlink is unlinked, never followed into core/cpp.
  fs.rmSync(target, { recursive: true, force: true });
}

function copyTree(from, to) {
  fs.cpSync(from, to, { recursive: true, verbatimSymlinks: true });
}

function runVendorScript(paths, script) {
  const result = spawnSync(process.execPath, [path.join(paths.vendorDir, script)], {
    cwd: paths.repoRoot,
    stdio: 'inherit',
  });
  if (result.error) throw new Error(`${script} failed to start: ${result.error.message}`);
  if (result.status !== 0) {
    const detail = result.signal ? `signal ${result.signal}` : `status ${result.status}`;
    throw new Error(`${script} failed (${detail})`);
  }
}

export function prepack(paths, { vendor = (script) => runVendorScript(paths, script) } = {}) {
  const { repoRoot, rnPkg, coreCpp, vendorDir } = paths;
  if (!fs.existsSync(coreCpp) || !fs.statSync(coreCpp).isDirectory()) {
    throw new Error(`core cpp directory not found at ${coreCpp}`);
  }

  // Restore the full vendor tree so the generated registry (which inlines
  // highlight queries) is up to date. The registry is small and ships in the
  // tarball; the heavy grammar sources and runtime are excluded (downloaded
  // by postinstall.mjs on the consumer's machine).
  vendor('vendor-grammars.mjs');

  // Restore RaTeX so we can verify the vendor tree is healthy, but its
  // contents are excluded from the tarball (downloaded by postinstall.mjs).
  vendor('vendor-ratex.mjs');

  // Replace the `cpp` symlink with a real copy of packages/core/cpp.
  const cpp = path.join(rnPkg, 'cpp');
  remove(cpp);
  copyTree(coreCpp, cpp);

  // Ship the registry codegen + manifest alongside the vendored grammars so a
  // published consumer can compile a custom language set (the default set uses
  // the restored cpp/highlight/vendor/generated registry and needs neither).
  const highlight = path.join(cpp, 'highlight');
  fs.copyFileSync(path.join(vendorDir, 'gen-registry.mjs'), path.join(highlight, 'gen-registry.mjs'));
  fs.copyFileSync(path.join(vendorDir, 'grammar-versions.json'), path.join(highlight, 'grammar-versions.json'));

  // Remove the heavy vendored content from the copy — these are downloaded by
  // the postinstall script on the consumer's machine from the npm registry.
  // Keep vendor/generated/ and vendor/generated-custom/ (small, pre-built registries).
  remove(path.join(highlight, 'vendor', 'grammars'));
  remove(path.join(highlight, 'vendor', 'tree-sitter'));

  // RaTeX vendor tree (ios/vendor) is excluded from the tarball by the
  // "!ios/vendor" entry in package.json's "files" array — no need to delete it.

  // Ship the RaTeX manifest and the vendor scripts so postinstall.mjs can
  // fetch the native assets in consumer mode.
  for (const file of SHIPPED_VENDOR_FILES) {
    fs.copyFileSync(path.join(vendorDir, file), path.join(rnPkg, file));
  }

  fs.copyFileSync(path.join(repoRoot, 'LICENSE'), path.join(rnPkg, 'LICENSE'));
  remove(path.join(rnPkg, 'docs'));
  copyTree(path.join(repoRoot, 'docs'), path.join(rnPkg, 'docs'));
}

export function postpack(paths, { platform = process.platform } = {}) {
  const { rnPkg, coreCpp } = paths;
  const cpp = path.join(rnPkg, 'cpp');
  remove(cpp);
  if (platform === 'win32') {
    // Directory symlinks need Developer Mode or elevation on Windows; a
    // junction needs neither and takes an absolute target.
    fs.symlinkSync(coreCpp, cpp, 'junction');
  } else {
    fs.symlinkSync(path.join('..', 'core', 'cpp'), cpp);
  }

  for (const file of ['LICENSE', ...SHIPPED_VENDOR_FILES]) remove(path.join(rnPkg, file));
  remove(path.join(rnPkg, 'docs'));
}

function main() {
  const mode = process.argv[2];
  const paths = publishPaths(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'));
  try {
    if (mode === 'prepack') prepack(paths);
    else if (mode === 'postpack') postpack(paths);
    else {
      console.error(`${LOG} usage: prepare-npm-publish.mjs prepack|postpack`);
      process.exit(1);
    }
  } catch (error) {
    console.error(`${LOG} error: ${error.message}`);
    process.exit(1);
  }
}

function isEntryPoint(argvPath, moduleUrl, platform = process.platform) {
  if (!argvPath) return false;
  // The loader reports the module's real path; argv keeps any symlinked cwd.
  let a;
  try {
    a = fs.realpathSync(argvPath);
  } catch {
    return false;
  }
  const b = fileURLToPath(moduleUrl);
  // Windows paths are case-insensitive and drive letters vary in case.
  return platform === 'win32' ? a.toLowerCase() === b.toLowerCase() : a === b;
}

if (isEntryPoint(process.argv[1], import.meta.url)) {
  main();
}
