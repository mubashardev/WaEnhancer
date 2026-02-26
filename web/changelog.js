/**
 * changelog.js
 * Fetches WaEnhancer releases from GitHub API and renders them.
 */

(function () {
    const API_URL = 'https://api.github.com/repos/mubashardev/WaEnhancer/releases';
    const REPO_RELEASES = 'https://github.com/mubashardev/WaEnhancer/releases';

    // ── DOM refs ──────────────────────────────────────────────────────────────
    const loadingEl   = document.getElementById('changelog-loading');
    const errorEl     = document.getElementById('changelog-error');
    const listEl      = document.getElementById('changelog-list');
    const bannerEl    = document.getElementById('latest-banner');
    const footerCtaEl = document.getElementById('changelog-footer-cta');

    const navDownloadBtn   = document.getElementById('nav-download-btn');
    const latestDownloadBtn = document.getElementById('latest-download-btn');
    const latestVersionEl  = document.getElementById('latest-version');
    const latestDateEl     = document.getElementById('latest-date');

    // ── Tiny Markdown → HTML parser ───────────────────────────────────────────
    function parseMd(text) {
        if (!text) return '';

        // Escape HTML entities first
        let html = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');

        // Inline: bold (**text** or __text__)
        html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        html = html.replace(/__(.+?)__/g, '<strong>$1</strong>');

        // Inline: italic (*text* or _text_)
        html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
        html = html.replace(/_(.+?)_/g, '<em>$1</em>');

        // Inline: code (`code`)
        html = html.replace(/`(.+?)`/g, '<code>$1</code>');

        // Inline: @mention → GitHub profile link
        html = html.replace(/@([A-Za-z0-9][A-Za-z0-9\-]*)/g,
            '<a href="https://github.com/$1" target="_blank" rel="nofollow noopener" style="color:var(--primary);text-decoration:none;font-weight:600;">@$1</a>');

        // Split into lines for block processing
        const lines = html.split('\n');
        const result = [];
        let inList = false;

        for (let i = 0; i < lines.length; i++) {
            let line = lines[i];

            // Section headings like [Fixed], [NEW FEATURES], etc.
            if (/^\[([^\]]+)\]\s*$/.test(line.trim())) {
                if (inList) { result.push('</ul>'); inList = false; }
                const label = line.match(/^\[([^\]]+)\]/)[1];
                result.push(`<div class="release-section-heading">${label}</div>`);
                continue;
            }

            // H1-H3 headings
            const h3 = line.match(/^###\s+(.+)/);
            const h2 = line.match(/^##\s+(.+)/);
            const h1 = line.match(/^#\s+(.+)/);
            if (h3) { if (inList) { result.push('</ul>'); inList = false; } result.push(`<h3>${h3[1]}</h3>`); continue; }
            if (h2) { if (inList) { result.push('</ul>'); inList = false; } result.push(`<h2>${h2[1]}</h2>`); continue; }
            if (h1) { if (inList) { result.push('</ul>'); inList = false; } result.push(`<h1>${h1[1]}</h1>`); continue; }

            // Unordered list items (* - •)
            const li = line.match(/^[\*\-\•]\s+(.+)/);
            if (li) {
                if (!inList) { result.push('<ul>'); inList = true; }
                result.push(`<li>${li[1]}</li>`);
                continue;
            }

            // Empty line → close list / paragraph break
            if (line.trim() === '') {
                if (inList) { result.push('</ul>'); inList = false; }
                continue; // skip blank lines (they just close lists)
            }

            // Regular paragraph line
            if (inList) { result.push('</ul>'); inList = false; }
            result.push(`<p>${line}</p>`);
        }

        if (inList) result.push('</ul>');
        return result.join('\n');
    }

    // ── Date formatter ─────────────────────────────────────────────────────────
    function formatDate(iso) {
        const d = new Date(iso);
        return d.toLocaleDateString('en-US', {
            year: 'numeric', month: 'long', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    }

    // ── Return the GitHub release page URL ────────────────────────────────────
    function getDownloadUrl(release) {
        return release.html_url;
    }

    // ── Build a release card element ───────────────────────────────────────────
    function buildCard(release, isLatest) {
        const card = document.createElement('article');
        card.className = 'release-card' + (isLatest ? ' release-card--latest' : '');

        const downloadUrl = getDownloadUrl(release);
        const dateStr = formatDate(release.published_at);
        const version = release.name || release.tag_name;

        card.innerHTML = `
            <div class="release-header">
                <div class="release-meta">
                    <div class="release-version">
                        ${escHtml(version)}
                        ${isLatest ? '<span class="release-label">Latest</span>' : ''}
                    </div>
                    <div class="release-date">${escHtml(dateStr)}</div>
                </div>
                <a href="${escHtml(downloadUrl)}" class="release-download-btn" target="_blank" rel="nofollow noopener">
                    ↓ View Release
                </a>
            </div>
            <div class="release-divider"></div>
            <div class="release-body">${parseMd(release.body || '*No changelog provided.*')}</div>
        `;

        return card;
    }

    function escHtml(str) {
        return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    // ── Main fetch & render ────────────────────────────────────────────────────
    async function loadReleases() {
        try {
            const res = await fetch(API_URL, {
                headers: { 'Accept': 'application/vnd.github+json' }
            });
            if (!res.ok) throw new Error('API error: ' + res.status);

            const releases = await res.json();
            if (!Array.isArray(releases) || releases.length === 0) {
                throw new Error('No releases found');
            }

            // Hide loader
            loadingEl.style.display = 'none';

            // Latest release (first non-prerelease, or just first)
            const latest = releases.find(r => !r.prerelease) || releases[0];
            const latestDl = getDownloadUrl(latest);

            // Update nav + banner download buttons → release page
            const latestPageUrl = latest.html_url;
            if (navDownloadBtn) navDownloadBtn.href = latestPageUrl;
            if (latestDownloadBtn) latestDownloadBtn.href = latestPageUrl;
            if (latestVersionEl) latestVersionEl.textContent = latest.name || latest.tag_name;
            if (latestDateEl) latestDateEl.textContent = formatDate(latest.published_at);
            if (bannerEl) bannerEl.style.display = '';

            // Render cards
            releases.forEach((r, idx) => {
                const isLatest = r.id === latest.id;
                const card = buildCard(r, isLatest);
                listEl.appendChild(card);

                // Animate in sequentially
                card.style.opacity = '0';
                card.style.transform = 'translateY(24px)';
                setTimeout(() => {
                    card.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
                    card.style.opacity = '1';
                    card.style.transform = 'translateY(0)';
                }, idx * 80);
            });

            // Show footer CTA
            if (footerCtaEl) footerCtaEl.style.display = '';

        } catch (e) {
            console.error('Changelog fetch error:', e);
            loadingEl.style.display = 'none';
            errorEl.style.display = '';
        }
    }

    loadReleases();
})();
