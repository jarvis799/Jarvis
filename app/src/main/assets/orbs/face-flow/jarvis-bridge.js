/* Jarvis bridge - the face reacts only to answers coming from the app. */
(function () {
  const params = new URLSearchParams(location.search);
  if (params.get('embedded') === '1') document.body.classList.add('embedded');

  const caption = document.getElementById('caption');
  const theme = params.get('theme') || '';
  if (caption && (theme === 'light' || theme === 'sunset')) caption.style.color = '#16241c';

  let activeId = null;
  function flow() {
    return new Promise((resolve) => {
      const wait = () => {
        if (window.faceFlow) resolve(window.faceFlow);
        else if (window.faceError) resolve(null);
        else setTimeout(wait, 60);
      };
      wait();
    });
  }

  function say(text) {
    const clean = String(text || '').replace(/\s+/g, ' ').trim();
    if (!clean) return;
    flow().then((instance) => {
      if (!instance) return;
      if (activeId) instance.endResponse(activeId);
      activeId = 'answer-' + Date.now();
      instance.beginResponse(activeId);
      if (caption) {
        caption.textContent = clean;
        caption.style.opacity = '1';
      }
    });
  }

  function end() {
    flow().then((instance) => {
      if (instance && activeId) instance.endResponse(activeId);
      activeId = null;
      if (caption) caption.style.opacity = '0';
    });
  }

  window.addEventListener('message', (event) => {
    const data = event.data || {};
    if (data.type === 'jarvis:say') say(data.text);
    else if (data.type === 'jarvis:say-end') end();
  });

  window.jarvisFace = { say: say, end: end };
})();
