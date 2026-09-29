import React from 'react';
import {useCurrentFrame, useVideoConfig} from 'remotion';
import {useAudioData, visualizeAudio} from '@remotion/media-utils';

interface AvatarProps {
  audioSrc?: string | null;
  style?: React.CSSProperties;
}

interface RobotArtworkProps extends AvatarProps {
  volume: number;
}

/** The presenter stays visible even while TTS audio is unavailable or loading. */
export const Avatar: React.FC<AvatarProps> = ({audioSrc, style}) => {
  if (!audioSrc) {
    return <RobotArtwork volume={0} style={style} />;
  }

  return <AudioDrivenRobot audioSrc={audioSrc} style={style} />;
};

const AudioDrivenRobot: React.FC<{audioSrc: string; style?: React.CSSProperties}> = ({
  audioSrc,
  style,
}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const audioData = useAudioData(audioSrc);

  if (!audioData) {
    return <RobotArtwork volume={0} style={style} />;
  }

  const frequencies = visualizeAudio({fps, frame, audioData, numberOfSamples: 32});
  const volume = frequencies.reduce((sum, value) => sum + value, 0) / frequencies.length;
  return <RobotArtwork volume={volume} style={style} />;
};

const RobotArtwork: React.FC<RobotArtworkProps> = ({volume, style}) => {
  const frame = useCurrentFrame();
  const mouthOpenness = Math.max(0.08, Math.min(1, volume * 8));
  const isBlinking = frame % 126 < 5;
  const hoverY = Math.sin(frame / 18) * 7;
  const headTilt = Math.sin(frame / 42) * 2.2;
  const rightArmRotation = -10 + Math.sin(frame / 11) * 8;
  const leftArmRotation = 8 + Math.sin(frame / 23) * 4;
  const glowOpacity = 0.35 + Math.min(0.6, volume * 2.5);

  return (
    <div
      style={{
        width: 330,
        height: 410,
        transform: `translateY(${hoverY}px)`,
        transformOrigin: '50% 100%',
        zIndex: 100,
        ...style,
      }}
    >
      <svg
        viewBox="0 0 240 300"
        width="100%"
        height="100%"
        role="img"
        aria-label="AI teaching robot"
        style={{filter: 'drop-shadow(0 24px 28px rgba(2, 6, 23, 0.5))'}}
      >
        <defs>
          <linearGradient id="robot-body" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#ffffff" />
            <stop offset="55%" stopColor="#dbeafe" />
            <stop offset="100%" stopColor="#94a3b8" />
          </linearGradient>
          <linearGradient id="robot-visor" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#020617" />
            <stop offset="100%" stopColor="#172554" />
          </linearGradient>
          <radialGradient id="robot-glow" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="#38bdf8" stopOpacity="0.9" />
            <stop offset="100%" stopColor="#6366f1" stopOpacity="0" />
          </radialGradient>
        </defs>

        <ellipse cx="120" cy="278" rx="76" ry="12" fill="rgba(15, 23, 42, 0.38)" />
        <circle cx="120" cy="130" r="112" fill="url(#robot-glow)" opacity={glowOpacity} />

        <g style={{transformOrigin: '120px 184px', transform: `rotate(${leftArmRotation}deg)`}}>
          <rect x="29" y="169" width="36" height="96" rx="18" fill="url(#robot-body)" />
          <circle cx="47" cy="260" r="21" fill="#bfdbfe" />
        </g>
        <g style={{transformOrigin: '120px 184px', transform: `rotate(${rightArmRotation}deg)`}}>
          <rect x="175" y="160" width="36" height="102" rx="18" fill="url(#robot-body)" />
          <circle cx="193" cy="257" r="21" fill="#bfdbfe" />
        </g>

        <path d="M70 177 Q120 151 170 177 L181 268 Q120 289 59 268 Z" fill="url(#robot-body)" stroke="#e0f2fe" strokeWidth="3" />
        <rect x="88" y="191" width="64" height="47" rx="18" fill="#0f172a" />
        <circle cx="120" cy="214" r={13 + volume * 5} fill="#38bdf8" opacity={0.85 + volume * 0.15} />
        <circle cx="120" cy="214" r="7" fill="#e0f2fe" />

        <g style={{transformOrigin: '120px 99px', transform: `rotate(${headTilt}deg)`}}>
          <line x1="120" y1="35" x2="120" y2="17" stroke="#94a3b8" strokeWidth="6" strokeLinecap="round" />
          <circle cx="120" cy="14" r="8" fill={volume > 0.025 ? '#fb7185' : '#64748b'} />
          <rect x="37" y="39" width="166" height="127" rx="49" fill="url(#robot-body)" stroke="#e0f2fe" strokeWidth="3" />
          <rect x="51" y="59" width="138" height="79" rx="31" fill="url(#robot-visor)" stroke="#334155" strokeWidth="3" />
          <path d="M64 70 Q120 55 176 70" fill="none" stroke="rgba(255,255,255,0.2)" strokeWidth="8" strokeLinecap="round" />

          <g style={{transformOrigin: '120px 87px', transform: isBlinking ? 'scaleY(0.12)' : 'scaleY(1)'}}>
            <circle cx="91" cy="88" r="10" fill="#38bdf8" />
            <circle cx="149" cy="88" r="10" fill="#38bdf8" />
            <circle cx="88" cy="85" r="3" fill="#ffffff" />
            <circle cx="146" cy="85" r="3" fill="#ffffff" />
          </g>

          <ellipse cx="120" cy="119" rx="22" ry={3 + mouthOpenness * 10} fill="#38bdf8" opacity={0.9} />
        </g>
      </svg>
    </div>
  );
};
