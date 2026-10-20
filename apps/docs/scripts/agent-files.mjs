// After `astro build`: files for AI agents, generated from the built site so they never drift from it.
// - <page>/index.md      Markdown version of every HTML page (served for "Accept: text/markdown", see worker/index.ts)
// - llms.txt             Index of the site in llms.txt format (https://llmstxt.org)
// - llms-full.txt        Every page's Markdown in one file
// - .well-known/agent-skills/index.json   Agent Skills Discovery index (RFC v0.2.0) with SHA-256 digests
import { createHash } from 'node:crypto';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import { join, relative, sep } from 'node:path';
import TurndownService from 'turndown';

const SITE = 'https://folio.allient.io';
const dist = new URL('../dist/', import.meta.url).pathname;

const turndown = new TurndownService({ headingStyle: 'atx', codeBlockStyle: 'fenced', bulletListMarker: '-' });
// Page chrome and interactive widgets carry no content.
turndown.remove(['script', 'style', 'svg', 'nav', 'button', 'footer', 'aside', 'form', 'noscript', 'template', 'starlight-toc', 'mobile-starlight-toc', 'starlight-menu-button', 'site-search', 'starlight-theme-select', 'starlight-lang-select']);
turndown.addRule('skip-link', { filter: (node) => node.nodeName === 'A' && node.getAttribute('class') === 'skip', replacement: () => '' });
// Starlight puts a "Section titled …" anchor inside each heading.
turndown.addRule('heading-anchors', { filter: (node) => node.nodeName === 'A' && /sl-anchor-link/.test(node.getAttribute('class') ?? ''), replacement: () => '' });

// Links and images point at this site in absolute form, so the Markdown still works when copied elsewhere.
const absolute = (href) => (href && href.startsWith('/') ? `${SITE}${href}` : href);
turndown.addRule('absolute-links', {
	filter: (node) => node.nodeName === 'A' && node.getAttribute('href'),
	replacement: (content, node) => (content.trim() ? `[${content.trim()}](${absolute(node.getAttribute('href'))})` : ''),
});
turndown.addRule('absolute-images', {
	filter: 'img',
	replacement: (_, node) => `![${node.getAttribute('alt') ?? ''}](${absolute(node.getAttribute('src'))})`,
});

const meta = (html, pattern) => html.match(pattern)?.[1]?.replace(/&amp;/g, '&').replace(/&#39;|&#x27;/g, "'").replace(/&quot;/g, '"').trim() ?? '';

async function htmlPages(dir) {
	const found = [];
	for (const entry of await readdir(dir, { withFileTypes: true })) {
		const path = join(dir, entry.name);
		if (entry.isDirectory() && !['_astro', 'pagefind', 'fonts', 'schemas', '.well-known'].includes(entry.name)) found.push(...(await htmlPages(path)));
		else if (entry.name === 'index.html') found.push(path);
	}
	return found;
}

const pages = [];
for (const file of await htmlPages(dist)) {
	const html = await readFile(file, 'utf8');
	const main = html.match(/<main\b[^>]*>([\s\S]*)<\/main>/)?.[1] ?? '';
	const title = meta(html, /<title>([^<]*)<\/title>/).replace(/ \| Folio$/, '');
	const description = meta(html, /<meta name="description" content="([^"]*)"/);
	const path = `/${relative(dist, file).split(sep).slice(0, -1).join('/')}${file === join(dist, 'index.html') ? '' : '/'}`;
	let body = turndown.turndown(main).replace(/\n{3,}/g, '\n\n').trim();
	if (!body.startsWith('# ')) body = `# ${title}\n\n${body}`;
	const markdown = `${body}\n\n---\nSource: ${SITE}${path}\n`;
	await writeFile(file.replace(/index\.html$/, 'index.md'), markdown);
	pages.push({ path, title, description, markdown });
}

const section = (name, list) => [`## ${name}`, '', ...list.map((p) => `- [${p.title}](${SITE}${p.path}index.md)${p.description ? `: ${p.description}` : ''}`), ''];
const home = pages.find((p) => p.path === '/');
const site = pages.filter((p) => !p.path.match(/^\/[^/]+\/.+/) && p.path !== '/');
const docs = pages.filter((p) => p.path.match(/^\/[^/]+\/.+/)).sort((a, b) => a.path.localeCompare(b.path));
await writeFile(
	join(dist, 'llms.txt'),
	[
		'# Folio',
		'',
		`> ${home?.description ?? 'Handwritten notes and eReader for Android tablets.'}`,
		'',
		'Folio is a free, open source (Apache 2.0) Android app for pen tablets and phones: handwriting with pressure-sensitive brushes, page templates and an infinite canvas, a PDF and EPUB reader with annotations and OCR, handwriting to text, and Excalidraw live boards for calls. It works offline, with no account and no ads. Source code: https://github.com/allient/folio. Every page is also available as Markdown by sending `Accept: text/markdown`.',
		'',
		...section('Product', [{ ...home, path: '/' }, ...site]),
		...section('Developer docs', docs),
		'## Optional',
		'',
		`- [All pages in one file](${SITE}/llms-full.txt)`,
		`- [.folio archive agent skill](${SITE}/.well-known/agent-skills/folio-archive/SKILL.md): read and write Folio notebook files`,
		`- [Notebook JSON schema](${SITE}/schemas/folio-notebook-v2.schema.json)`,
		'',
	].join('\n'),
);
await writeFile(join(dist, 'llms-full.txt'), [home, ...site, ...docs].map((p) => p.markdown).join('\n\n'));

// Agent Skills Discovery index: one entry per SKILL.md under .well-known/agent-skills/<name>/.
const skillsDir = join(dist, '.well-known', 'agent-skills');
const skills = [];
for (const entry of await readdir(skillsDir, { withFileTypes: true })) {
	if (!entry.isDirectory()) continue;
	const content = await readFile(join(skillsDir, entry.name, 'SKILL.md'));
	const description = content.toString().match(/^description:\s*(.+)$/m)?.[1]?.trim() ?? '';
	skills.push({
		name: entry.name,
		type: 'skill-md',
		description,
		url: `${SITE}/.well-known/agent-skills/${entry.name}/SKILL.md`,
		digest: `sha256:${createHash('sha256').update(content).digest('hex')}`,
	});
}
await writeFile(join(skillsDir, 'index.json'), JSON.stringify({ $schema: 'https://schemas.agentskills.io/discovery/0.2.0/schema.json', skills }, null, 2));

console.log(`agent files: ${pages.length} Markdown pages, llms.txt, llms-full.txt, ${skills.length} skill(s)`);
