<#macro display>
  <@template_cmd name="pathToRoot">
    <#assign root = (pathToRoot!'') />
    <header class="pc-header">
      <div class="pc-container">
        <nav class="pc-nav">
          <a class="pc-nav__link" href="${root}index.html#readme">The Potato Cannon</a>
          <a class="pc-nav__link" href="${root}index.html#changelog">Changelog</a>
          <a class="pc-nav__link" href="${root}index.html#packages">Packages</a>
        </nav>
      </div>
    </header>
  </@template_cmd>

<script>
  // Wrap "Packages" heading + its table into a named container for styling
  document.addEventListener('DOMContentLoaded', function () {
    var root = document.querySelector('#main #content');
    if (!root) return;

    // Find the H2 whose text is exactly "Packages" (ignoring whitespace)
    var heading = Array.prototype.find.call(
      root.querySelectorAll('h2'),
      function (h) { return h.textContent.trim() === 'Packages'; }
    );
    if (!heading) return;
    if (heading.closest('.pc-packages-index-wrap')) return; // already wrapped

    // Find the first .table that comes after the heading
    var tableEl = null;
    for (var n = heading.nextElementSibling; n; n = n.nextElementSibling) {
      if (n.classList && n.classList.contains('table')) { tableEl = n; break; }
      // stop if we hit another section header before a table
      if (/^H[12]$/.test(n.tagName)) break;
    }

    // Create wrapper and insert before heading
    var wrap = document.createElement('div');
    wrap.className = 'pc-section pc-packages-index-wrap';
    wrap.id = 'packages';
    heading.parentNode.insertBefore(wrap, heading);
    wrap.appendChild(heading);
    if (tableEl) wrap.appendChild(tableEl);
  });
</script>
</#macro>