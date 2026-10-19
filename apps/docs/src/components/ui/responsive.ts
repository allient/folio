/**
 * Responsive props for the layout primitives, MUI style:
 * `gap={3}` or `gap={{ base: 2, md: 4 }}`. Values become CSS custom properties
 * (`--ui-gap-base`, `--ui-gap-md`, ...) that the primitives' media queries read,
 * each breakpoint falling back to the one below it.
 */
export const breakpoints = { base: 0, sm: 600, md: 900, lg: 1200 } as const;
export type Breakpoint = keyof typeof breakpoints;
export type Responsive<T> = T | Partial<Record<Breakpoint, T>>;

/** Spacing unit: 1 = 8px, like MUI's theme.spacing. Strings pass through ("2rem", "auto"). */
export const space = (value: number | string) => (typeof value === 'number' ? `${value * 0.5}rem` : value);

export function responsiveVars<T>(
	name: string,
	value: Responsive<T> | undefined,
	format: (value: T) => string = String,
): Record<string, string> {
	if (value === undefined) return {};
	const entries =
		typeof value === 'object' && value !== null && !Array.isArray(value)
			? (Object.entries(value) as [Breakpoint, T][])
			: ([['base', value as T]] as [Breakpoint, T][]);
	return Object.fromEntries(entries.map(([bp, v]) => [`--ui-${name}-${bp}`, format(v)]));
}

export const styleString = (vars: Record<string, string | undefined>) =>
	Object.entries(vars)
		.filter(([, v]) => v !== undefined && v !== '')
		.map(([k, v]) => `${k}:${v}`)
		.join(';');
