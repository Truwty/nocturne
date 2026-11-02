const loaders = { fabric: 'Fabric', forge: 'Forge', neoforge: 'NeoForge' };

export function releaseUrl(value, asset = false) {
  try {
    const url = new URL(value);
    const prefix = asset ? '/Truwty/nocturne/releases/download/' : '/Truwty/nocturne/releases/tag/';
    return url.protocol === 'https:' && url.hostname === 'github.com' && !url.port &&
      !url.username && !url.password && url.pathname.startsWith(prefix) &&
      url.pathname.length > prefix.length && !url.search && !url.hash ? url.href : null;
  } catch {
    return null;
  }
}

export function cleanCatalog(data) {
  if (data?.schema !== 1 || !Array.isArray(data.releases)) throw new Error('Invalid release catalog');
  return data.releases.filter(item => typeof item.version === 'string' && item.version.length < 100 &&
    typeof item.tag === 'string' && typeof item.prerelease === 'boolean' &&
    Number.isFinite(Date.parse(item.publishedAt)) && releaseUrl(item.notesUrl) && Array.isArray(item.assets))
    .map(item => ({ ...item, assets: item.assets.filter(asset =>
      Array.isArray(asset.minecraft) && asset.minecraft.length > 0 && asset.minecraft.every(value => typeof value === 'string' && /^[0-9]+(?:\.[0-9]+){1,2}$/.test(value)) &&
      typeof asset.loader === 'string' && Object.hasOwn(loaders, asset.loader.toLowerCase()) &&
      Number.isInteger(asset.java) && asset.java >= 8 && asset.java <= 100 &&
      typeof asset.name === 'string' && asset.name.endsWith('.jar') && !/(?:sources|javadoc)/i.test(asset.name) &&
      releaseUrl(asset.url, true) && (asset.sha256 == null || /^[a-f0-9]{64}$/i.test(asset.sha256)))
      .map(asset => ({ ...asset, loader: loaders[asset.loader.toLowerCase()] })) }))
    .filter(item => item.assets.length > 0).sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt));
}

export function matchingAsset(release, minecraft, loader) {
  return release?.assets.find(asset => asset.minecraft.includes(minecraft) && asset.loader.toLowerCase() === loader.toLowerCase());
}

export function dependencyText(asset) {
  const value = asset.dependencies?.fabricApi;
  if (asset.loader !== 'Fabric') return 'Fabric API is not required for this loader.';
  return typeof value === 'string' && /^[0-9][0-9A-Za-z.+_-]{0,79}$/.test(value)
    ? `Fabric API ${value} or newer for this exact Minecraft version is required.`
    : 'Install matching Fabric API; check the release notes for its required version.';
}
