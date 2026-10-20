// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// Cloudflare Web Analytics (cookieless) for the developer docs too; see src/layouts/Landing.astro.
// Accepts the bare token or the whole snippet Cloudflare shows; the token is the 32-hex-digit value.
const beaconToken = process.env.PUBLIC_CF_BEACON_TOKEN?.match(/[0-9a-f]{32}/i)?.[0];

// https://astro.build/config
export default defineConfig({
	integrations: [
		starlight({
			title: 'Folio',
			description: 'Handwritten notes and eReader for Android.',
			logo: { src: './src/assets/brand/folio-icon-primary.svg' },
			customCss: ['./src/styles/brand.css'],
			head: beaconToken
				? [{ tag: 'script', attrs: { defer: true, src: 'https://static.cloudflareinsights.com/beacon.min.js', 'data-cf-beacon': JSON.stringify({ token: beaconToken }) } }]
				: [],
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
