export type Viseme = 'REST' | 'A' | 'E' | 'I' | 'O' | 'U' | 'MBP' | 'FV' | 'LNTD' | 'SH' | 'KG';

export interface MouthMorph {
  viseme: Viseme;
  openness: number;   // 0.0 (closed resting seam) to 1.0 (fully open)
  widthScale: number; // 0.88 (rounded) to 1.14 (spread smile)
  teeth: number;      // 0.0 to 1.0 upper teeth visibility
  tongue: number;     // 0.0 to 1.0 lower tongue visibility
  smile: number;      // 0.0 to 1.0 mouth corner lift
  energy: number;     // acoustic energy at frame
}

type TimedViseme = {viseme: Viseme; weight: number};

const VISEME_TARGETS: Record<Viseme, {open: number; width: number; teeth: number; tongue: number; smile: number}> = {
  REST: {open: 0.0, width: 1.0, teeth: 0.0, tongue: 0.0, smile: 0.3},
  MBP:  {open: 0.0, width: 0.98, teeth: 0.0, tongue: 0.0, smile: 0.25},
  A:    {open: 0.85, width: 1.02, teeth: 0.45, tongue: 0.65, smile: 0.4},
  E:    {open: 0.58, width: 1.10, teeth: 0.92, tongue: 0.35, smile: 0.75},
  I:    {open: 0.42, width: 1.12, teeth: 0.95, tongue: 0.30, smile: 0.80},
  O:    {open: 0.78, width: 0.90, teeth: 0.25, tongue: 0.45, smile: 0.2},
  U:    {open: 0.52, width: 0.88, teeth: 0.20, tongue: 0.30, smile: 0.15},
  FV:   {open: 0.38, width: 1.04, teeth: 0.88, tongue: 0.10, smile: 0.5},
  LNTD: {open: 0.52, width: 1.02, teeth: 0.75, tongue: 0.55, smile: 0.45},
  SH:   {open: 0.48, width: 0.96, teeth: 0.60, tongue: 0.30, smile: 0.35},
  KG:   {open: 0.68, width: 1.00, teeth: 0.50, tongue: 0.40, smile: 0.40},
};

const removeToneMarks = (value: string): string => value
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/\u0111/g, 'd');

const visemeFor = (token: string): Viseme => {
  if (/^[\s,.;:!?\u2026-]+$/.test(token)) return 'REST';
  if (/^(ph|f|v)/.test(token)) return 'FV';
  if (/^(m|b|p)/.test(token)) return 'MBP';
  if (/^(ch|tr|th|nh|s|x|r|gi)/.test(token)) return 'SH';
  if (/^(ngh|ng|kh|gh|qu|c|k|q|g)/.test(token)) return 'KG';
  if (/^(l|n|t|d)/.test(token)) return 'LNTD';
  if (/^a/.test(token)) return 'A';
  if (/^e/.test(token)) return 'E';
  if (/^[iy]/.test(token)) return 'I';
  if (/^o/.test(token)) return 'O';
  if (/^u/.test(token)) return 'U';
  return 'A';
};

export const buildVisemeSequence = (text: string): TimedViseme[] => {
  const normalized = removeToneMarks(text.toLowerCase());
  const parts = normalized.match(/ngh|ch|tr|th|nh|ph|gh|gi|kh|ng|qu|[a-z]|[\s,.;:!?\u2026-]+/g) ?? [];
  const sequence = parts.map((part): TimedViseme => {
    if (/^[\s,.;:!?\u2026-]+$/.test(part)) {
      const sentenceEnd = /[.!?\u2026]/.test(part);
      return {viseme: 'REST', weight: sentenceEnd ? 3.2 : /[,;:]/.test(part) ? 2 : 0.45};
    }
    return {viseme: visemeFor(part), weight: 1};
  });
  return sequence.length > 0 ? sequence : [{viseme: 'REST', weight: 1}];
};

const visemeAtProgress = (text: string, progress: number): Viseme => {
  const sequence = buildVisemeSequence(text);
  const totalWeight = sequence.reduce((sum, item) => sum + item.weight, 0);
  const cursor = Math.max(0, Math.min(0.999999, progress)) * totalWeight;
  let elapsed = 0;
  for (const item of sequence) {
    elapsed += item.weight;
    if (cursor < elapsed) return item.viseme;
  }
  return 'REST';
};

export const visemeAtFrame = (
  text: string,
  frame: number,
  durationInFrames: number,
  speechEnvelope?: number[],
): Viseme => {
  if (speechEnvelope && speechEnvelope.length > 0) {
    const threshold = 0.025;
    const envelopeFrame = Math.min(frame, speechEnvelope.length - 1);
    if (frame >= speechEnvelope.length || speechEnvelope[envelopeFrame] < threshold) return 'REST';
    let spokenFrames = 0;
    let spokenBefore = 0;
    for (let index = 0; index < speechEnvelope.length; index++) {
      if (speechEnvelope[index] >= threshold) {
        spokenFrames++;
        if (index <= envelopeFrame) spokenBefore++;
      }
    }
    return visemeAtProgress(text, spokenBefore / Math.max(1, spokenFrames));
  }

  const leadIn = Math.min(4, Math.floor(durationInFrames * 0.03));
  const leadOut = Math.min(7, Math.floor(durationInFrames * 0.05));
  if (frame < leadIn || frame >= durationInFrames - leadOut) return 'REST';
  const speakingFrames = Math.max(1, durationInFrames - leadIn - leadOut);
  return visemeAtProgress(text, (frame - leadIn) / speakingFrames);
};

export const mouthMorphAtFrame = (
  text: string,
  frame: number,
  durationInFrames: number,
  speechEnvelope?: number[],
): MouthMorph => {
  const targetViseme = visemeAtFrame(text, frame, durationInFrames, speechEnvelope);
  const target = VISEME_TARGETS[targetViseme] ?? VISEME_TARGETS.REST;

  let energy = 0;
  if (speechEnvelope && speechEnvelope.length > 0) {
    const clampedFrame = Math.min(frame, speechEnvelope.length - 1);
    energy = speechEnvelope[clampedFrame] ?? 0;
  } else {
    energy = targetViseme === 'REST' ? 0 : 0.65;
  }

  // Smooth syllable openness scaling
  const effectiveOpenness = targetViseme === 'REST' || targetViseme === 'MBP'
    ? 0.0
    : target.open * Math.max(0.45, Math.min(1.0, energy * 1.35));

  return {
    viseme: targetViseme,
    openness: effectiveOpenness,
    widthScale: target.width,
    teeth: target.teeth,
    tongue: target.tongue,
    smile: target.smile,
    energy,
  };
};
