import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';
import { docsSchema } from '@astrojs/starlight/schema';

// Developer docs are plain Markdown in the repository's root `docs/` folder, readable on GitHub
// and built into this site. README.md files there are GitHub-only indexes.
export const collections = {
	docs: defineCollection({
		loader: glob({ pattern: ['**/*.{md,mdx}', '!**/README.md'], base: '../../docs' }),
		schema: docsSchema(),
	}),
};
