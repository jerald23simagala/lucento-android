// svgo pass before svg2vectordrawable (copied from lucento-wallet-design/tools/svg, plus defaultFill).
// inlineStyles + convertStyleToAttrs turn CSS into attributes; inheritFill pushes a <g> fill down to its paths;
// defaultFill makes the SVG default (black) explicit, because the converter leaves an unfilled path invisible.
module.exports = {
  plugins: [
    { name: 'preset-default', params: { overrides: { inlineStyles: { onlyMatchedOnce: false } } } },
    'convertStyleToAttrs',
    {
      name: 'inheritFill',
      fn: () => {
        const st = [];
        return {
          element: {
            enter(n) {
              const f = n.attributes.fill ?? (st.length ? st[st.length - 1] : undefined);
              st.push(f);
              if (n.name === 'path' && n.attributes.fill === undefined && f !== undefined) n.attributes.fill = f;
            },
            exit() { st.pop(); },
          },
        };
      },
    },
    {
      name: 'defaultFill',
      fn: () => {
        let clipDepth = 0; // paths inside <clipPath> are geometry, not paint
        return {
          element: {
            enter(n) {
              if (n.name === 'clipPath') clipDepth++;
              if (n.name !== 'path' || clipDepth > 0) return;
              if (n.attributes.fill === undefined || n.attributes.fill === 'currentColor') n.attributes.fill = '#000';
            },
            exit(n) {
              if (n.name === 'clipPath') clipDepth--;
            },
          },
        };
      },
    },
  ],
};
