'use strict';
(() => {
  const tabs = [...document.querySelectorAll('[data-capture]')];
  const image = document.querySelector('#capture-image');
  if (!tabs.length || !image) return;
  const captures = {
    world: { file: 'world.png', width: 854, height: 478, alt: 'Nocturne’s built-in Neon Rain demo showing synchronized lyrics in a rainy Minecraft world', caption: 'The words belong here.', note: 'Actual gameplay capture · Built-in demo track' },
    studio: { file: 'studio.png', width: 854, height: 480, alt: 'Nocturne Studio inside Minecraft, with music, lyric and appearance controls', caption: 'Your session. Your Studio.', note: 'Actual in-game interface · The Nocturne Studio' },
    timing: { file: 'lyric-workspace.png', width: 1280, height: 958, alt: 'Nocturne lyric workspace with line and word timestamps and a playback timeline', caption: 'Make every word land.', note: 'Actual in-game interface · Interactive timing editor' },
  };
  function selectTab(tab, focus = false) {
    const capture = captures[tab.dataset.capture];
    if (!capture) return;
    tabs.forEach(item => { item.setAttribute('aria-selected', String(item === tab)); item.tabIndex = item === tab ? 0 : -1; });
    image.src = `assets/${capture.file}`;
    image.alt = capture.alt;
    image.width = capture.width;
    image.height = capture.height;
    document.querySelector('#capture-caption').textContent = capture.caption;
    document.querySelector('#capture-note').textContent = capture.note;
    document.querySelector('#capture-number').textContent = `0${tabs.indexOf(tab) + 1} / 03`;
    document.querySelector('#capture-panel').setAttribute('aria-labelledby', tab.id);
    if (focus) tab.focus();
  }
  tabs.forEach((tab, index) => {
    tab.addEventListener('click', () => selectTab(tab));
    tab.addEventListener('keydown', event => {
      let next;
      if (event.key === 'ArrowRight') next = (index + 1) % tabs.length;
      if (event.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
      if (event.key === 'Home') next = 0;
      if (event.key === 'End') next = tabs.length - 1;
      if (next === undefined) return;
      event.preventDefault();
      selectTab(tabs[next], true);
    });
  });
})();
