// WebMCP (https://webmachinelearning.github.io/webmcp/): tools that browser AI agents can call on this site.
// Loaded on every page; does nothing in browsers without the API.
(() => {
	const context = document.modelContext ?? navigator.modelContext;
	if (!context?.registerTool) return;
	const controller = new AbortController();
	addEventListener('pagehide', () => controller.abort(), { once: true });
	const text = (value) => ({ content: [{ type: 'text', text: value }] });
	const sitePath = (path) => {
		const url = new URL(String(path || '/'), location.origin);
		if (url.origin !== location.origin) throw new Error('Only pages on this site are available.');
		return url.pathname;
	};
	const tools = [
		{
			name: 'search_folio_docs',
			description: 'Search the Folio website and developer docs (features, privacy, file format, architecture, publishing). Returns matching pages with excerpts.',
			inputSchema: { type: 'object', properties: { query: { type: 'string', description: 'Words to search for, e.g. "pdf annotation" or ".folio format"' } }, required: ['query'] },
			async execute({ query }) {
				const pagefind = await import('/pagefind/pagefind.js');
				const { results } = await pagefind.search(String(query));
				const pages = await Promise.all(results.slice(0, 6).map((result) => result.data()));
				if (!pages.length) return text(`No pages match "${query}".`);
				return text(pages.map((page) => `${page.meta?.title ?? page.url}\n${location.origin}${page.url}\n${page.excerpt.replace(/<[^>]+>/g, '')}`).join('\n\n'));
			},
		},
		{
			name: 'get_folio_page_markdown',
			description: 'Get any page of this site as Markdown, for reading or quoting. Use "/" for the product overview and "/privacy/" for the privacy policy.',
			inputSchema: { type: 'object', properties: { path: { type: 'string', description: 'Page path on this site, e.g. "/" or "/architecture/portable-format/"' } }, required: ['path'] },
			async execute({ path }) {
				const response = await fetch(sitePath(path), { headers: { Accept: 'text/markdown' } });
				if (!response.ok) return text(`No page at ${path} (HTTP ${response.status}).`);
				return text(await response.text());
			},
		},
		{
			name: 'list_folio_pages',
			description: 'List every page on this site with a one-line description (llms.txt).',
			inputSchema: { type: 'object', properties: {} },
			async execute() {
				return text(await (await fetch('/llms.txt')).text());
			},
		},
		{
			name: 'open_folio_page',
			description: 'Navigate the browser to a page on this site.',
			inputSchema: { type: 'object', properties: { path: { type: 'string', description: 'Page path on this site, e.g. "/privacy/"' } }, required: ['path'] },
			async execute({ path }) {
				location.assign(sitePath(path));
				return text(`Opening ${path}`);
			},
		},
	];
	for (const tool of tools) {
		try {
			Promise.resolve(context.registerTool(tool, { signal: controller.signal })).catch(() => {});
		} catch {
			// An older API shape; the page works the same without tools.
		}
	}
})();
