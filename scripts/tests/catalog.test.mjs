import test from 'node:test';
import assert from 'node:assert/strict';
import { cleanCatalog, matchingAsset, dependencyText } from '../../site/catalog.mjs';

function release() {
  return { version: '1.0.0', tag: 'v1.0.0', prerelease: false, publishedAt: '2026-11-01T00:00:00Z',
    notesUrl: 'https://github.com/Truwty/nocturne/releases/tag/v1.0.0',
    assets: ['Fabric', 'Forge', 'NeoForge'].map(loader => ({ loader, minecraft: ['1.21.11'], java: 21,
      name: `${loader}.jar`, url: `https://github.com/Truwty/nocturne/releases/download/v1.0.0/${loader}.jar`, sha256: 'a'.repeat(64) })) };
}

test('each loader resolves to its own artifact for the exact game version', () => {
  const item = cleanCatalog({ schema: 1, releases: [release()] })[0];
  for (const loader of ['fabric', 'forge', 'neoforge']) {
    const asset = matchingAsset(item, '1.21.11', loader);
    assert.equal(asset.loader.toLowerCase(), loader);
    assert.ok(asset.url.endsWith(`/${asset.loader}.jar`));
  }
  assert.equal(matchingAsset(item, '1.21.10', 'fabric'), undefined);
  assert.equal(matchingAsset(item, '1.21.11', 'unknown'), undefined);
});

test('unknown loaders and source archives are excluded', () => {
  const item = release();
  item.assets[0].loader = 'untrusted'; item.assets[1].name = 'mod-sources.jar';
  assert.deepEqual(cleanCatalog({ schema: 1, releases: [item] })[0].assets.map(a => a.loader), ['NeoForge']);
});

test('foreign download links cannot become selectable artifacts', () => {
  const item = release();
  item.assets.forEach(asset => { asset.url = 'https://evil.example/mod.jar'; });
  assert.deepEqual(cleanCatalog({ schema: 1, releases: [item] }), []);
});

test('dependencies are described for the selected loader without inventing versions', () => {
  assert.match(dependencyText({ loader: 'Fabric', dependencies: { fabricApi: '0.141.6+1.21.11' } }), /0\.141\.6/);
  assert.match(dependencyText({ loader: 'Fabric' }), /release notes/);
  assert.match(dependencyText({ loader: 'NeoForge' }), /not required/);
});
