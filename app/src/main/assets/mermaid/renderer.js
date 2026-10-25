(() => {
  'use strict';
  // Source arrives as a JSON argument, never as HTML or JavaScript source.
  window.renderMermaid = ({ source, dark = false }) => {
    const diagram = document.getElementById('diagram');
    const staging = document.getElementById('staging');
    try {
      if (typeof source !== 'string' || !source.trim() || source.length > 50000) {
        throw new Error('The diagram is empty or too large.');
      }
      document.documentElement.classList.toggle('dark', Boolean(dark));
      const styles = getComputedStyle(document.documentElement);
      let renderSource = source;
      if (/^\s*stateDiagram(?:-v2)?\b/m.test(source) &&
          !/^\s*direction\s+(?:LR|RL|TB|BT)\b/m.test(source)) {
        renderSource = source.replace(/^(\s*stateDiagram(?:-v2)?\b[^\n]*)/m, '$1\n  direction TB');
      }
      const result = window.intentPele.render(renderSource, {
        fontFamily: styles.getPropertyValue('--font').trim(),
        fontSize: 13, idPrefix: 'diagram', responsive: false, autoDirection: false,
        links: false, images: false, limit: 50000, outputLimit: 4000000, maxEdges: 500,
      });
      if (![result.width, result.height].every(value => Number.isFinite(value) && value > 0)) {
        throw new Error('The diagram has no visible content.');
      }
      staging.innerHTML = result.svg;
      const svg = staging.querySelector('svg');
      if (!svg) throw new Error('The diagram could not be rendered.');
      svg.setAttribute('width', String(result.width));
      svg.setAttribute('height', String(result.height));
      svg.style.width = `${result.width}px`;
      svg.style.height = `${result.height}px`;
      svg.style.maxWidth = 'none';
      svg.setAttribute('role', 'img');
      svg.setAttribute('aria-label', 'Mermaid diagram');
      for (const anchor of svg.querySelectorAll('a')) {
        anchor.removeAttribute('href');
        anchor.removeAttribute('xlink:href');
        anchor.removeAttribute('target');
      }
      diagram.replaceChildren(svg);
      return { height: Math.ceil(diagram.getBoundingClientRect().height) };
    } catch (_) {
      diagram.replaceChildren();
      return { error: true };
    } finally {
      staging.replaceChildren();
    }
  };
  document.addEventListener('click', event => {
    if (event.target.closest?.('a')) event.preventDefault();
  }, true);
})();
