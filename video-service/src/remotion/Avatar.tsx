import React from 'react';
import {Img, interpolate, staticFile, useCurrentFrame, useVideoConfig, Video} from 'remotion';
import {mouthMorphAtFrame, type MouthMorph} from './lipSync';

interface AvatarProps {
  audioSrc?: string | null;
  avatarVideoUrl?: string | null;
  narrationText?: string;
  lipSyncEnvelope?: number[];
  teacherAction?: string | null;
  style?: React.CSSProperties;
  frameless?: boolean;
}

type Pose = 'explain' | 'point' | 'question';

const poseForAction = (teacherAction?: string | null): Pose => {
  const action = (teacherAction ?? 'EXPLAIN').toUpperCase();
  if (action === 'QUESTION' || action === 'WELCOME') return 'question';
  if (action === 'POINT' || action === 'EMPHASIZE') return 'point';
  return 'explain';
};

// Calibrated exact anatomical centers on 1254x1254 square teacher images
const mouthPosition: Record<Pose, {left: number; top: number; rotate: number; widthBase: number}> = {
  explain:  {left: 48.68, top: 33.69, rotate: -3.0, widthBase: 7.34},
  point:    {left: 66.99, top: 32.46, rotate:  6.0, widthBase: 7.18},
  question: {left: 50.64, top: 35.25, rotate: -6.5, widthBase: 7.50},
};

const eyePositions: Record<Pose, {
  leftEye: {left: number; top: number; width: number; height: number; rotate: number};
  rightEye: {left: number; top: number; width: number; height: number; rotate: number};
}> = {
  explain: {
    leftEye:  {left: 43.54, top: 24.72, width: 5.42, height: 2.87, rotate:  2},
    rightEye: {left: 53.95, top: 23.76, width: 5.02, height: 3.19, rotate: -5},
  },
  point: {
    leftEye:  {left: 63.96, top: 23.88, width: 4.78, height: 2.79, rotate:  6},
    rightEye: {left: 72.57, top: 24.88, width: 5.10, height: 2.55, rotate:  4},
  },
  question: {
    leftEye:  {left: 44.34, top: 26.79, width: 5.42, height: 2.87, rotate: -4},
    rightEye: {left: 55.02, top: 24.48, width: 5.10, height: 3.35, rotate: -8},
  },
};

const getBlinkAmount = (frame: number, fps: number): number => {
  const cycle = Math.round(fps * 3.5);
  const pos = (frame + 19) % Math.max(1, cycle);
  const blinkLen = Math.max(4, Math.round(fps * 0.16));
  if (pos < blinkLen) {
    const phase = pos / blinkLen;
    return Math.pow(Math.sin(phase * Math.PI), 1.2);
  }
  if (Math.floor((frame + 19) / cycle) % 3 === 1) {
    const pos2 = pos - Math.round(fps * 0.34);
    if (pos2 >= 0 && pos2 < blinkLen) {
      const phase = pos2 / blinkLen;
      return 0.88 * Math.pow(Math.sin(phase * Math.PI), 1.2);
    }
  }
  return 0;
};

const AnimatedEyes: React.FC<{pose: Pose; blink: number}> = ({pose, blink}) => {
  if (blink <= 0.02) return null;
  const eyes = eyePositions[pose];
  const renderLid = (spec: typeof eyes.leftEye) => (
    <div
      style={{
        position: 'absolute',
        left: `${spec.left}%`,
        top: `${spec.top}%`,
        width: `${spec.width}%`,
        height: `${spec.height * blink * 1.05}%`,
        transform: `translate(-50%, -50%) rotate(${spec.rotate}deg)`,
        borderRadius: '50% 50% 40% 40%',
        background: 'linear-gradient(180deg, #f5c4b4 0%, #ebb2a2 72%, #2c1218 100%)',
        boxShadow: '0 1px 2px rgba(44,18,24,0.6)',
        pointerEvents: 'none',
      }}
    />
  );

  return (
    <div style={{position: 'absolute', inset: 0, pointerEvents: 'none'}}>
      {renderLid(eyes.leftEye)}
      {renderLid(eyes.rightEye)}
    </div>
  );
};

const AnimatedMouth: React.FC<{pose: Pose; morph: MouthMorph}> = ({pose, morph}) => {
  const position = mouthPosition[pose];
  const widthPct = position.widthBase * morph.widthScale;
  const heightPct = Math.max(0.72, 0.72 + 2.75 * morph.openness);
  const isResting = morph.openness <= 0.04;

  return (
    <div style={{position: 'absolute', inset: 0, pointerEvents: 'none'}}>
      <div
        style={{
          position: 'absolute',
          left: `${position.left}%`,
          top: `${position.top}%`,
          width: `${widthPct}%`,
          height: `${heightPct}%`,
          transform: `translate(-50%, -50%) rotate(${position.rotate}deg)`,
          borderRadius: isResting
            ? '48% 48% 50% 50%'
            : `${Math.round(42 - morph.openness * 12)}% ${Math.round(42 - morph.openness * 12)}% 46% 46%`,
          overflow: 'hidden',
          background: isResting
            ? 'linear-gradient(180deg, #c7485d 0%, #ee7889 44%, #ba3b50 56%, #e36a7d 100%)'
            : 'radial-gradient(ellipse at 50% 50%, #461423 0%, #290a13 70%, #17040a 100%)',
          boxShadow: isResting
            ? '0 0.5px 1px rgba(138,32,54,0.55), inset 0 -0.8px 1px rgba(255,160,175,0.65)'
            : '0 0 0 1px rgba(186,59,80,0.85), inset 0 2px 4px rgba(0,0,0,0.7), 0 2px 5px rgba(0,0,0,0.3)',
          transition: 'width 50ms ease-out, height 50ms ease-out, border-radius 50ms ease-out',
        }}
      >
        {/* Upper teeth row */}
        {!isResting && morph.teeth > 0.15 && morph.openness > 0.12 ? (
          <div
            style={{
              position: 'absolute',
              top: 0,
              left: '12%',
              right: '12%',
              height: `${Math.min(42, Math.round(morph.teeth * 36))}%`,
              borderRadius: '0 0 45% 45%',
              background: 'linear-gradient(180deg, #ffffff 0%, #fff6ec 65%, #ebd9c7 100%)',
              boxShadow: '0 1px 2px rgba(41,10,19,0.5)',
            }}
          />
        ) : null}

        {/* Lower tongue */}
        {!isResting && morph.tongue > 0.15 && morph.openness > 0.22 ? (
          <div
            style={{
              position: 'absolute',
              bottom: '-8%',
              left: '18%',
              right: '18%',
              height: `${Math.min(45, Math.round(morph.tongue * 38))}%`,
              borderRadius: '50% 50% 20% 20%',
              background: 'linear-gradient(180deg, #e4687d 0%, #c8485e 100%)',
              boxShadow: 'inset 0 1px 2px rgba(255,180,195,0.4)',
            }}
          />
        ) : null}

        {/* Subtle lower lip glossy highlight */}
        <div
          style={{
            position: 'absolute',
            bottom: 0,
            left: '24%',
            right: '24%',
            height: '18%',
            borderRadius: '50% 50% 0 0',
            background: 'linear-gradient(180deg, rgba(255,185,198,0.55), transparent)',
            pointerEvents: 'none',
          }}
        />
      </div>
    </div>
  );
};

export const Avatar: React.FC<AvatarProps> = ({
  audioSrc,
  avatarVideoUrl,
  narrationText = '',
  lipSyncEnvelope,
  teacherAction,
  style,
  frameless = false,
}) => {
  const frame = useCurrentFrame();
  const {durationInFrames, fps} = useVideoConfig();
  const pose = poseForAction(teacherAction);

  if (avatarVideoUrl) {
    const entrance = interpolate(frame, [0, 10], [0.94, 1], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    });
    const floatY = Math.sin(frame / 36) * 1.5;

    return (
      <div
        style={{
          width: 330,
          height: 350,
          borderRadius: frameless ? 0 : 24,
          overflow: 'hidden',
          position: 'relative',
          transform: `translateY(${floatY}px) scale(${entrance})`,
          transformOrigin: '50% 100%',
          border: frameless ? undefined : '2px solid rgba(56,189,248,.45)',
          boxShadow: frameless
            ? undefined
            : '0 20px 50px rgba(2,6,23,.6), 0 0 32px rgba(56,189,248,.28)',
          background: frameless ? 'transparent' : 'linear-gradient(135deg,rgba(15,23,42,.85),rgba(30,27,75,.85))',
          ...style,
        }}
      >
        <Video
          src={avatarVideoUrl}
          muted
          volume={0}
          style={{width: '100%', height: '100%', objectFit: 'contain'}}
        />
        {!frameless ? <AvatarBadge label="AI TEACHER" /> : null}
      </div>
    );
  }

  const morph = mouthMorphAtFrame(narrationText, frame, durationInFrames, lipSyncEnvelope);
  const blink = getBlinkAmount(frame, fps);

  // Articulated upper-body motion: breathing, head nod/tilt synced to speech energy
  const speechEnergy = morph.energy;
  const breath = Math.sin(frame / 18) * 0.007;
  const speechNod = Math.sin(frame / 7.5) * speechEnergy * 2.0;
  const headTilt = Math.sin(frame / 15) * (0.35 + speechEnergy * 0.85);
  const gestureY = Math.sin(frame / 30) * (pose === 'point' ? 1.6 : 0.95);
  const entrance = interpolate(frame, [0, 12], [0.95, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <div
      style={{
        width: 330,
        height: 350,
        position: 'relative',
        overflow: 'hidden',
        borderRadius: frameless ? 0 : 24,
        transformOrigin: '50% 100%',
        border: frameless ? undefined : '2px solid rgba(56,189,248,.45)',
        boxShadow: frameless
          ? undefined
          : '0 20px 50px rgba(2,6,23,.6), 0 0 32px rgba(56,189,248,.28)',
        background: frameless
          ? 'transparent'
          : 'linear-gradient(135deg,rgba(15,23,42,.85),rgba(30,27,75,.85))',
        ...style,
      }}
    >
      <div
        style={{
          position: 'absolute',
          inset: 0,
          transform: `translateY(${gestureY + speechNod}px) rotate(${headTilt}deg) scale(${entrance + breath})`,
          transformOrigin: '50% 100%',
        }}
      >
        <div style={{position: 'absolute', left: 0, right: 0, bottom: 0, aspectRatio: '1 / 1'}}>
          <Img
            src={staticFile(`teacher/avatar_teacher_${pose}_mouthless.png`)}
            style={{
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              filter: 'drop-shadow(0 18px 20px rgba(2,6,23,.48))',
            }}
          />
          <AnimatedEyes pose={pose} blink={blink} />
          <AnimatedMouth pose={pose} morph={morph} />
        </div>
      </div>
      {!frameless ? (
        <AvatarBadge label={audioSrc ? 'AI GIẢNG VIÊN' : 'CHẾ ĐỘ TĨNH'} />
      ) : null}
    </div>
  );
};

const AvatarBadge: React.FC<{label: string}> = ({label}) => (
  <div
    style={{
      position: 'absolute',
      top: 12,
      right: 12,
      padding: '4px 10px',
      borderRadius: 999,
      background: 'rgba(15,23,42,.8)',
      border: '1px solid rgba(56,189,248,.55)',
      color: '#38bdf8',
      fontSize: 10,
      fontWeight: 800,
      letterSpacing: 1,
      boxShadow: '0 2px 8px rgba(0,0,0,0.4)',
    }}
  >
    ● {label}
  </div>
);
