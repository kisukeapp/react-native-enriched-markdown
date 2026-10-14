import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { postpack, prepack, publishPaths } from './prepare-npm-publish.mjs';

function write(file, text) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
}

function fixtureRepo() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'enriched-pack-'));
  const paths = publishPaths(root);
  write(path.join(paths.coreCpp, 'parser', 'md4c.c'), 'md4c');
  write(path.join(paths.coreCpp, 'highlight', 'vendor', 'generated', 'registry.h'), 'registry');
  write(path.join(paths.coreCpp, 'highlight', 'vendor', 'grammars', 'json', 'parser.c'), 'grammar');
  write(path.join(paths.coreCpp, 'highlight', 'vendor', 'tree-sitter', 'lib.c'), 'runtime');
  for (const file of ['gen-registry.mjs', 'grammar-versions.json', 'ratex-version.json', 'archive-tools.mjs', 'vendor-grammars.mjs', 'vendor-ratex.mjs']) {
    write(path.join(paths.vendorDir, file), file);
  }
  write(path.join(root, 'LICENSE'), 'MIT');
  write(path.join(root, 'docs', 'guide.md'), 'guide');
  write(path.join(paths.rnPkg, 'package.json'), '{}');
  fs.symlinkSync(path.join('..', 'core', 'cpp'), path.join(paths.rnPkg, 'cpp'), process.platform === 'win32' ? 'junction' : 'dir');
  return paths;
}

function tree(dir) {
  return fs.readdirSync(dir, { recursive: true }).map((entry) => entry.split(path.sep).join('/')).sort();
}

test('prepack replaces the cpp link with a trimmed copy and stages the shipped files', () => {
  const paths = fixtureRepo();
  const vendored = [];
  prepack(paths, { vendor: (script) => vendored.push(script) });

  assert.deepEqual(vendored, ['vendor-grammars.mjs', 'vendor-ratex.mjs']);
  const cpp = path.join(paths.rnPkg, 'cpp');
  assert.equal(fs.lstatSync(cpp).isSymbolicLink(), false);
  assert.deepEqual(tree(cpp), [
    'highlight',
    'highlight/gen-registry.mjs',
    'highlight/grammar-versions.json',
    'highlight/vendor',
    'highlight/vendor/generated',
    'highlight/vendor/generated/registry.h',
    'parser',
    'parser/md4c.c',
  ]);
  for (const file of ['LICENSE', 'ratex-version.json', 'archive-tools.mjs', 'vendor-grammars.mjs', 'vendor-ratex.mjs', 'docs/guide.md']) {
    assert.ok(fs.existsSync(path.join(paths.rnPkg, file)), file);
  }
  // The source tree keeps its heavy vendored content; only the packed copy is trimmed.
  assert.ok(fs.existsSync(path.join(paths.coreCpp, 'highlight', 'vendor', 'grammars', 'json', 'parser.c')));
});

test('prepack is repeatable without nesting docs', () => {
  const paths = fixtureRepo();
  prepack(paths, { vendor: () => {} });
  prepack(paths, { vendor: () => {} });
  assert.deepEqual(tree(path.join(paths.rnPkg, 'docs')), ['guide.md']);
});

test('prepack fails before touching the package when core cpp is missing', () => {
  const paths = fixtureRepo();
  fs.rmSync(paths.coreCpp, { recursive: true });
  assert.throws(() => prepack(paths, { vendor: () => assert.fail('vendored without core cpp') }), /core cpp directory not found/);
  assert.equal(fs.lstatSync(path.join(paths.rnPkg, 'cpp')).isSymbolicLink(), true);
});

test('postpack restores the cpp link and removes the staged files', () => {
  const paths = fixtureRepo();
  prepack(paths, { vendor: () => {} });
  postpack(paths);

  const cpp = path.join(paths.rnPkg, 'cpp');
  assert.equal(fs.lstatSync(cpp).isSymbolicLink(), true);
  assert.equal(fs.realpathSync(cpp), fs.realpathSync(paths.coreCpp));
  assert.deepEqual(fs.readdirSync(paths.rnPkg).sort(), ['cpp', 'package.json']);
});
