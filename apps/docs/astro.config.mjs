// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	integrations: [
		starlight({
			title: 'Folio',
			description: 'Handwritten notes and eReader for Android.',
			logo: { src: './src/assets/brand/folio-icon-primary.svg' },
			customCss: ['./src/styles/brand.css'],
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
