import type { APIRoute } from 'astro';
import { AI_SEARCH_BOTS, AI_TRAINING_BOTS, CONTENT_SIGNAL, SITE } from '../site';

// RFC 9309 robots.txt with Content Signals (https://contentsignals.org) and explicit AI crawler groups.
// Everything here is public; only the search index files are kept out of search results.
const group = (agents: string[], comment: string) =>
	[`# ${comment}`, ...agents.map((agent) => `User-agent: ${agent}`), `Content-Signal: ${CONTENT_SIGNAL}`, 'Allow: /', ''].join('\n');

export const GET: APIRoute = () =>
	new Response(
		[
			'# Folio: handwritten notes and eReader for Android. Public site and developer docs.',
			'# Markdown versions of every page: send "Accept: text/markdown", or see /llms.txt.',
			'',
			'User-agent: *',
			`Content-Signal: ${CONTENT_SIGNAL}`,
			'Allow: /',
			'Disallow: /pagefind/',
			'',
			group(AI_SEARCH_BOTS, 'AI search and user-requested fetchers: pages may appear in AI answers.'),
			group(AI_TRAINING_BOTS, 'AI training crawlers: allowed; the app and these docs are open source (Apache 2.0).'),
			`Sitemap: ${SITE}/sitemap.xml`,
			`Agentmap: ${SITE}/.well-known/ai-catalog.json`,
			'',
		].join('\n'),
		{ headers: { 'Content-Type': 'text/plain; charset=utf-8' } },
	);
