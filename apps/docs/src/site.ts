// Facts about the site that several pages, endpoints and the agent files must agree on.
export const SITE = 'https://folio.allient.io';
export const GITHUB = 'https://github.com/allient/folio';
export const NAME = 'Folio';
export const TAGLINE = 'Handwritten notes, PDF annotation and an eReader for Android tablets';
export const DESCRIPTION =
	'Folio is a free, open source note-taking app for Android tablets and pens: handwriting with pressure-sensitive brushes, an infinite canvas, PDF and EPUB annotation, handwriting to text and live whiteboards. Offline, no account, your notes stay on your device.';
export const OG_IMAGE = `${SITE}/og.png`;

// Searches people type when they look for an app like Folio. Used in page copy, the FAQ and meta tags.
export const KEYWORDS = [
	'handwriting notes app for Android',
	'note taking app for Android tablet',
	'stylus notes app',
	'PDF annotation app Android',
	'EPUB reader with notes',
	'infinite canvas app',
	'digital notebook',
	'handwriting to text',
	'pen tablet app',
	'open source note taking app',
	'offline notes app',
	'whiteboard app for video calls',
];

// Answers for the landing page FAQ and its FAQPage structured data (Google and AI assistants read both).
export const FAQ = [
	{
		q: 'What is Folio?',
		a: 'Folio is a free, open source note-taking app for Android tablets and pens. You write by hand with pressure-sensitive brushes, sketch on an infinite canvas, and read and annotate PDFs and EPUBs, all in one library.',
	},
	{
		q: 'Does Folio work on Android tablets with a stylus?',
		a: 'Yes. Folio is made for Android tablets and pen displays, with pressure, tilt and a pressure threshold that ignores a worn pen tip. It also works on Android phones.',
	},
	{
		q: 'Can I annotate PDFs in Folio?',
		a: 'Yes. Open a PDF or EPUB, then highlight, underline, write in the margins and add bookmarks. Search works on the text, scanned pages are read with on-device text recognition (OCR), and you can export the annotated PDF.',
	},
	{
		q: 'Can Folio convert handwriting to text?',
		a: 'Yes. Select your handwriting with the lasso and choose Convert to text. Recognition runs on the device and supports more than 300 languages. You can also write with the pen straight into text fields on Android 14 and later.',
	},
	{
		q: 'Is Folio free? Does it need an account?',
		a: 'Folio is free and open source under the Apache 2.0 license. There is no account, no subscription and no ads.',
	},
	{
		q: 'Where are my notes stored?',
		a: 'On your device. Folio works offline and has no cloud. Smart backups go to a folder you choose, and notebooks export as PDF, PNG or editable .folio files with a documented format.',
	},
	{
		q: 'Can I use Folio as a whiteboard in a video call?',
		a: 'Yes. Live boards are whiteboards built on Excalidraw. Write with your pen using Folio’s pressure settings, start a live session and share the link; people in your call watch and draw from any browser.',
	},
	{
		q: 'Is Folio on Google Play?',
		a: 'A Google Play release for Android phones and tablets is on the way. Until then you can build Folio from the source code on GitHub.',
	},
];

// Crawlers named in robots.txt. Folio's site is public marketing and developer docs, so all of them are welcome.
export const AI_SEARCH_BOTS = ['OAI-SearchBot', 'Claude-SearchBot', 'PerplexityBot', 'ChatGPT-User', 'Claude-User', 'Perplexity-User'];
export const AI_TRAINING_BOTS = ['GPTBot', 'ClaudeBot', 'Google-Extended', 'Applebot-Extended', 'CCBot', 'Amazonbot', 'Bytespider', 'meta-externalagent'];
// search: search results; ai-input: grounding AI answers; ai-train: model training (https://contentsignals.org).
export const CONTENT_SIGNAL = 'search=yes, ai-input=yes, ai-train=yes';
