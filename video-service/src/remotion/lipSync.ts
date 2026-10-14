export type Viseme = 'REST' | 'A' | 'E' | 'I' | 'O' | 'U' | 'MBP' | 'FV' | 'LNTD' | 'SH' | 'KG';

type TimedViseme = {viseme: Viseme; weight: number};

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
