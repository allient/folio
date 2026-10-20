// Runs in front of the static site (Workers Static Assets) for page requests only; see run_worker_first in wrangler.jsonc.
// - "Accept: text/markdown" gets the page's Markdown (written by scripts/agent-files.mjs) instead of HTML.
// - HTML pages get RFC 8288 Link headers that point agents to the Markdown, llms.txt, sitemap and catalogs.
// - Agent discovery files are readable cross-origin.

interface Env {
	ASSETS: Fetcher;
}

const DISCOVERY = [
	'</llms.txt>; rel="describedby"; type="text/plain"',
	'</sitemap.xml>; rel="sitemap"; type="application/xml"',
	'</.well-known/ai-catalog.json>; rel="ai-catalog"; type="application/json"',
	'</.well-known/agent-skills/index.json>; rel="describedby"; type="application/json"',
	'</architecture/portable-format/>; rel="service-doc"; type="text/html"',
	'</schemas/folio-notebook-v2.schema.json>; rel="describedby"; type="application/schema+json"',
];

const wantsMarkdown = (request: Request) => {
	const accept = request.headers.get('Accept') ?? '';
	const q = (type: string) => {
		const part = accept.split(',').map((p) => p.trim()).find((p) => p.startsWith(type));
		if (!part) return -1;
		return Number(part.match(/;\s*q=([\d.]+)/)?.[1] ?? 1);
	};
	const md = q('text/markdown');
	return md > 0 && md >= q('text/html');
};

/** The Markdown file for a page URL: "/" → "/index.md", "/privacy/" → "/privacy/index.md". */
const markdownPath = (pathname: string) => (pathname.endsWith('/') ? `${pathname}index.md` : `${pathname}/index.md`);

export default {
	async fetch(request: Request, env: Env): Promise<Response> {
		const url = new URL(request.url);
		const isPage = !/\.[a-z0-9]+$/i.test(url.pathname);

		if (isPage && wantsMarkdown(request)) {
			const md = await env.ASSETS.fetch(new Request(new URL(markdownPath(url.pathname), url), request));
			if (md.ok) {
				const text = await md.text();
				return new Response(request.method === 'HEAD' ? null : text, {
					headers: {
						'Content-Type': 'text/markdown; charset=utf-8',
						'x-markdown-tokens': String(Math.ceil(text.length / 4)),
						'Content-Location': markdownPath(url.pathname),
						Vary: 'Accept',
						'Cache-Control': 'public, max-age=0, must-revalidate',
						'Access-Control-Allow-Origin': '*',
						Link: `<${url.pathname}>; rel="canonical"`,
					},
				});
			}
		}

		const response = await env.ASSETS.fetch(request);
		const type = response.headers.get('Content-Type') ?? '';
		const headers = new Headers(response.headers);
		if (type.startsWith('text/html')) {
			headers.append('Vary', 'Accept');
			headers.append('Link', `<${markdownPath(url.pathname)}>; rel="alternate"; type="text/markdown"`);
			if (url.pathname === '/') DISCOVERY.forEach((link) => headers.append('Link', link));
			headers.set('X-Content-Type-Options', 'nosniff');
			headers.set('Referrer-Policy', 'strict-origin-when-cross-origin');
		} else if (url.pathname.startsWith('/.well-known/') || /^\/(llms(-full)?\.txt|robots\.txt|sitemap\.xml)$/.test(url.pathname) || url.pathname.endsWith('.md')) {
			headers.set('Access-Control-Allow-Origin', '*');
			if (url.pathname.endsWith('.md')) headers.set('Content-Type', 'text/markdown; charset=utf-8');
		}
		return new Response(response.body, { status: response.status, statusText: response.statusText, headers });
	},
} satisfies ExportedHandler<Env>;
