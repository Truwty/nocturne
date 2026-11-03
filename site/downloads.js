import { releaseUrl, cleanCatalog, matchingAsset, dependencyText } from './catalog.mjs';

'use strict';

(() => {
  const minecraft = document.querySelector('#minecraft');
  const release = document.querySelector('#release');
  const loader = document.querySelector('#loader');
  const previews = document.querySelector('#previews');
  const status = document.querySelector('#download-status');
  const detail = document.querySelector('#download-detail');
  const params = new URLSearchParams(location.search);
  let releases = [];
  let initial = true;

  function option(value, label) {
    const element = document.createElement('option');
    element.value = value;
    element.textContent = label;
    return element;
  }

  function message(text) {
    detail.hidden = true;
    status.hidden = false;
    status.textContent = text;
  }

  function visibleReleases() {
    return releases.filter(item => previews.checked || !item.prerelease);
  }

  function channel(item) {
    if (!item.prerelease) return 'Stable';
    if (/-alpha(?:[.+-]|$)/i.test(item.version)) return 'Preview / alpha';
    if (/-beta(?:[.+-]|$)/i.test(item.version)) return 'Preview / beta';
    return 'Preview';
  }

  function populateMinecraft() {
    const previous = initial ? params.get('minecraft') : minecraft.value;
    const values = [...new Set(visibleReleases().flatMap(item => item.assets.flatMap(asset => asset.minecraft)))];
    values.sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
    minecraft.replaceChildren(...values.map(value => option(value, value)));
    minecraft.disabled = values.length === 0;
    if (values.includes(previous)) minecraft.value = previous;
    populateLoaders();
  }

  function populateLoaders() {
    const previous = initial ? params.get('loader')?.toLowerCase() : loader.value;
    const values = [...new Set(visibleReleases().flatMap(item => item.assets.filter(asset => asset.minecraft.includes(minecraft.value)).map(asset => asset.loader)))];
    values.sort((a, b) => a.localeCompare(b));
    loader.replaceChildren(...values.map(value => option(value.toLowerCase(), value)));
    loader.disabled = values.length === 0;
    if (values.some(value => value.toLowerCase() === previous)) loader.value = previous;
    populateReleases();
  }

  function populateReleases() {
    const previous = initial ? params.get('release') : release.value;
    const matches = visibleReleases().filter(item => matchingAsset(item, minecraft.value, loader.value));
    release.replaceChildren(...matches.map(item => option(item.tag, `${item.version} — ${channel(item)}`)));
    release.disabled = matches.length === 0;
    if (matches.some(item => item.tag === previous)) release.value = previous;
    if (!matches.length) {
      release.append(option('', 'No matching releases'));
      message('No releases match these filters. Include preview releases or browse GitHub Releases below.');
    } else render();
    initial = false;
  }

  function render() {
    const item = visibleReleases().find(value => value.tag === release.value);
    const asset = matchingAsset(item, minecraft.value, loader.value);
    if (!item || !asset) return message('No downloadable build matches this selection. Choose another Minecraft version or release.');
    status.hidden = true;
    detail.hidden = false;
    const text = (selector, value) => { document.querySelector(selector).textContent = value; };
    text('#selected-version', item.version);
    text('#release-channel', channel(item).toUpperCase());
    text('#release-date', `Published ${new Date(item.publishedAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })}`);
    text('#selected-minecraft', minecraft.value);
    text('#selected-loader', asset.loader + (typeof asset.dependencies?.loader === 'string' && /^[0-9][0-9A-Za-z.+_-]{0,79}$/.test(asset.dependencies.loader) ? ` ${asset.dependencies.loader}+` : ''));
    text('#dependency-note', dependencyText(asset));
    text('#selected-java', `${asset.java}+`);
    text('#asset-name', asset.name);
    text('#asset-checksum', asset.sha256 || 'No checksum published for this asset.');
    document.querySelector('#download-file').href = releaseUrl(asset.url, true);
    document.querySelector('#release-notes').href = releaseUrl(item.notesUrl);
    const query = new URLSearchParams({ minecraft: minecraft.value, loader: loader.value, release: item.tag });
    if (previews.checked) query.set('previews', '1');
    history.replaceState(null, '', `${location.pathname}?${query}`);
  }

  minecraft.addEventListener('change', populateLoaders);
  loader.addEventListener('change', populateReleases);
  release.addEventListener('change', render);
  previews.addEventListener('change', populateMinecraft);

  fetch('./releases.json', { cache: 'no-store', headers: { Accept: 'application/json' } })
    .then(response => { if (!response.ok) throw new Error('Catalog unavailable'); return response.json(); })
    .then(data => {
      releases = cleanCatalog(data);
      if (!releases.length) {
        minecraft.replaceChildren(option('', 'No published builds'));
        loader.replaceChildren(option('', 'No published builds'));
        release.replaceChildren(option('', 'No published builds'));
        return message('No compatible builds are listed yet. You can check published releases on GitHub below.');
      }
      previews.checked = params.get('previews') === '1' || !releases.some(item => !item.prerelease);
      const requested = releases.find(item => item.tag === params.get('release'));
      if (requested?.prerelease) previews.checked = true;
      if (Number.isFinite(Date.parse(data.generatedAt))) document.querySelector('#catalog-time').textContent = `Catalog updated ${new Date(data.generatedAt).toISOString().slice(0, 10)}`;
      populateMinecraft();
    })
    .catch(() => {
      minecraft.replaceChildren(option('', 'Catalog unavailable'));
      loader.replaceChildren(option('', 'Catalog unavailable'));
      release.replaceChildren(option('', 'Catalog unavailable'));
      message('The release catalog could not be loaded. Download directly from GitHub Releases below, or reload to try again.');
    });
})();
