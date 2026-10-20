import type { APIRoute } from 'astro';
import { DESCRIPTION, GITHUB, NAME, SITE } from '../../site';

// ARD capability manifest (https://agenticresourcediscovery.org, ai-catalog data model).
// Folio has no API or MCP server; what agents can use is the docs as Markdown, the agent skill and the file format schemas.
const host = new URL(SITE).host;
const urn = (namespace: string, name: string) => `urn:air:${host}:${namespace}:${name}`;

export const GET: APIRoute = () =>
	Response.json({
		specVersion: '1.0',
		host: { displayName: NAME, identifier: `did:web:${host}`, description: DESCRIPTION, url: SITE },
		entries: [
			{
				identifier: urn('docs', 'llms-txt'),
				displayName: 'Folio documentation for language models',
				description: 'Index of every page on this site as Markdown (llms.txt). /llms-full.txt has all of it in one file.',
				type: 'text/markdown',
				url: `${SITE}/llms.txt`,
				representativeQueries: [
					'what is the Folio notes app for Android',
					'does Folio annotate PDFs and EPUBs',
					'how does Folio store notebooks',
					'Folio privacy policy',
				],
			},
			{
				identifier: urn('skill', 'folio-archive'),
				displayName: 'Read and write .folio notebook archives',
				description: 'Agent skill (SKILL.md) for opening, inspecting and creating Folio .folio archives.',
				type: 'text/markdown',
				url: `${SITE}/.well-known/agent-skills/folio-archive/SKILL.md`,
				representativeQueries: [
					'extract handwritten notes from a .folio file',
					'convert a Folio notebook to JSON',
					'what is inside a .folio archive',
				],
			},
			{
				identifier: urn('schema', 'folio-notebook-v2'),
				displayName: 'Folio notebook JSON schema (v2)',
				description: 'JSON Schema for notebook.json inside .folio archives: pages, strokes, text, images, layers.',
				type: 'application/schema+json',
				url: `${SITE}/schemas/folio-notebook-v2.schema.json`,
				representativeQueries: ['Folio notebook JSON format', 'schema for Folio strokes and pages'],
			},
			{
				identifier: urn('source', 'github'),
				displayName: 'Folio source code',
				description: 'Kotlin Multiplatform Android app, Apache 2.0.',
				type: 'text/html',
				url: GITHUB,
				representativeQueries: ['Folio source code', 'build Folio from source', 'report a bug in Folio'],
			},
		],
	});
