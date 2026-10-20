// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// Cloudflare Web Analytics (cookieless) for the developer docs too; see src/layouts/Landing.astro.
// Accepts the bare token or the whole snippet Cloudflare shows; the token is the 32-hex-digit value.
const beaconToken = process.env.PUBLIC_CF_BEACON_TOKEN?.match(/[0-9a-f]{32}/i)?.[0];

const SITE = 'https://folio.allient.io';
// Tags every Starlight page gets: social image, agent discovery links and the WebMCP tools (public/webmcp.js).
const head = [
	{ tag: 'meta', attrs: { property: 'og:image', content: `${SITE}/og.png` } },
	{ tag: 'meta', attrs: { name: 'twitter:card', content: 'summary_large_image' } },
	{ tag: 'link', attrs: { rel: 'alternate', type: 'text/plain', href: '/llms.txt', title: 'llms.txt' } },
	{ tag: 'link', attrs: { rel: 'ai-catalog', href: '/.well-known/ai-catalog.json' } },
	{ tag: 'script', attrs: { src: '/webmcp.js', defer: true } },
];

// https://astro.build/config
export default defineConfig({
	// Canonical URLs, Open Graph URLs and Starlight's /sitemap-index.xml all use this origin.
	site: SITE,
	integrations: [
		starlight({
			title: 'Folio',
			description: 'Developer docs for Folio, the open source handwriting notes, PDF annotation and eReader app for Android tablets.',
			logo: { src: './src/assets/brand/folio-icon-primary.svg' },
			customCss: ['./src/styles/brand.css'],
			head: beaconToken
				? [...head, { tag: 'script', attrs: { defer: true, src: 'https://static.cloudflareinsights.com/beacon.min.js', 'data-cf-beacon': JSON.stringify({ token: beaconToken }) } }]
				: head,
			social: [{ icon: 'github', label: 'GitHub', href: 'https://github.com/allient/folio' }],
			editLink: { baseUrl: 'https://github.com/allient/folio/edit/main/apps/docs/' },
			sidebar: [
				{ label: 'Architecture', items: [{ autogenerate: { directory: 'architecture' } }] },
				{ label: 'Features', items: [{ autogenerate: { directory: 'features' } }] },
				{ label: 'Investigations', items: [{ autogenerate: { directory: 'investigations' } }] },
				{ label: 'Roadmap', items: [{ autogenerate: { directory: 'roadmap' } }] },
				{ label: 'Testing', items: [{ autogenerate: { directory: 'testing' } }] },
				{ label: 'Publishing', items: [{ autogenerate: { directory: 'publishing' } }] },
				{ label: 'Brand', slug: 'brand' },
			],
		}),
	],
});
