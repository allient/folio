import type { APIRoute } from 'astro';
import { getCollection } from 'astro:content';
import { SITE } from '../site';

// /sitemap.xml with every canonical page; rebuilt on each deploy, so new and removed docs follow automatically.
// Starlight also writes /sitemap-index.xml; this flat list is the one robots.txt points to.
export const GET: APIRoute = async () => {
	const docs = await getCollection('docs');
	const pages = [
		{ path: '/', priority: '1.0', changefreq: 'weekly' },
		{ path: '/privacy/', priority: '0.6', changefreq: 'monthly' },
		...docs
			.map((entry) => ({ path: `/${entry.id.replace(/\/index$/, '')}/`, priority: '0.5', changefreq: 'monthly' }))
			.sort((a, b) => a.path.localeCompare(b.path)),
	];
	const urls = pages
		.map(({ path, priority, changefreq }) => `  <url><loc>${SITE}${path}</loc><changefreq>${changefreq}</changefreq><priority>${priority}</priority></url>`)
		.join('\n');
	return new Response(`<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${urls}\n</urlset>\n`, {
		headers: { 'Content-Type': 'application/xml; charset=utf-8' },
	});
};
