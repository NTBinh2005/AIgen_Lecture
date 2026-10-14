import React from 'react';
import {Img, interpolate, staticFile, useCurrentFrame, useVideoConfig, Video} from 'remotion';
import {visemeAtFrame, type Viseme} from './lipSync';

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

const mouthPosition: Record<Pose, {left: number; top: number; rotate: number}> = {
  explain: {left: 49.0, top: 33.6, rotate: -1},
  point: {left: 66.3, top: 31.9, rotate: 2},
  question: {left: 49.8, top: 35.4, rotate: -2},
};

const mouthShape: Record<Viseme, {w: number; h: number; radius: string; teeth?: boolean}> = {
  REST: {w: 5.2, h: 0.85, radius: '50%'},
  A: {w: 5.5, h: 3.05, radius: '45%'},
  E: {w: 6.3, h: 2.05, radius: '45%', teeth: true},
  I: {w: 6.5, h: 1.45, radius: '45%', teeth: true},
  O: {w: 3.45, h: 3.35, radius: '50%'},
  U: {w: 3.15, h: 2.2, radius: '50%'},
  MBP: {w: 5.35, h: 0.72, radius: '50%'},
  FV: {w: 5.7, h: 1.55, radius: '45%', teeth: true},
  LNTD: {w: 5.3, h: 2.15, radius: '45%', teeth: true},
  SH: {w: 4.65, h: 2.05, radius: '48%'},
  KG: {w: 5.2, h: 2.65, radius: '45%'},
};

const AnimatedMouth: React.FC<{pose: Pose; viseme: Viseme}> = ({pose, viseme}) => {
  const position = mouthPosition[pose];
  const shape = mouthShape[viseme];
  return (
    <div style={{position: 'absolute', inset: 0, pointerEvents: 'none'}}>
      <div style={{position: 'absolute', left: `${position.left}%`, top: `${position.top}%`, width: `${shape.w}%`, height: `${shape.h}%`, transform: `translate(-50%,-50%) rotate(${position.rotate}deg)`, borderRadius: shape.radius, overflow: 'hidden', background: viseme === 'REST' || viseme === 'MBP' ? 'linear-gradient(#b94455,#ed7180 48%,#a93449 54%,#d95f70)' : 'linear-gradient(#8b2638 0%,#3f101d 52%,#a92e47 100%)', boxShadow: '0 0 0 .35px rgba(104,25,41,.55),inset 0 -1px 1px rgba(255,123,142,.5)'}}>
        {shape.teeth ? <div style={{height: '34%', margin: '0 8%', borderRadius: '0 0 50% 50%', background: '#fff8ef'}} /> : null}
        {!shape.teeth && !['REST', 'MBP'].includes(viseme) ? <div style={{position: 'absolute', left: '22%', right: '22%', bottom: '-4%', height: '30%', borderRadius: '50% 50% 20% 20%', background: '#d9576d'}} /> : null}
      </div>
    </div>
  );
};

export const Avatar: React.FC<AvatarProps> = ({audioSrc, avatarVideoUrl, narrationText = '', lipSyncEnvelope, teacherAction, style, frameless = false}) => {
  const frame = useCurrentFrame();
  const {durationInFrames} = useVideoConfig();
  const pose = poseForAction(teacherAction);

  if (avatarVideoUrl) {
    return <div style={{width: 330, height: 350, borderRadius: frameless ? 0 : 24, overflow: 'hidden', position: 'relative', ...style}}><Video src={avatarVideoUrl} muted volume={0} style={{width: '100%', height: '100%', objectFit: 'contain'}} />{!frameless ? <AvatarBadge label="AI TEACHER" /> : null}</div>;
  }

  const viseme = audioSrc ? visemeAtFrame(narrationText, frame, durationInFrames, lipSyncEnvelope) : 'REST';
  const breath = Math.sin(frame / 18) * 0.006;
  const gesture = Math.sin(frame / 34) * (pose === 'point' ? 1.1 : 0.65);
  const entrance = interpolate(frame, [0, 12], [0.96, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});

  return (
    <div style={{width: 330, height: 350, position: 'relative', overflow: 'hidden', borderRadius: frameless ? 0 : 24, transformOrigin: '50% 100%', border: frameless ? undefined : '2px solid rgba(56,189,248,.4)', boxShadow: frameless ? undefined : '0 20px 50px rgba(2,6,23,.6),0 0 30px rgba(56,189,248,.25)', background: frameless ? 'transparent' : 'linear-gradient(135deg,rgba(15,23,42,.8),rgba(30,27,75,.8))', ...style}}>
      <div style={{position: 'absolute', inset: 0, transform: `translateY(${gesture}px) scale(${entrance + breath})`, transformOrigin: '50% 100%'}}>
        {/*
          The source artwork is square. Keep the image and mouth overlay in the
          same square coordinate space so object-fit / container aspect ratios
          can never move them independently.
        */}
        <div style={{position: 'absolute', left: 0, right: 0, bottom: 0, aspectRatio: '1 / 1'}}>
          <Img src={staticFile(`teacher/avatar_teacher_${pose}_mouthless.png`)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', filter: 'drop-shadow(0 18px 18px rgba(2,6,23,.45))'}} />
          <AnimatedMouth pose={pose} viseme={viseme} />
        </div>
      </div>
      {!frameless ? <AvatarBadge label={audioSrc ? '2D LIP-SYNC' : '2D TEACHER'} /> : null}
    </div>
  );
};

const AvatarBadge: React.FC<{label: string}> = ({label}) => <div style={{position: 'absolute', top: 12, right: 12, padding: '4px 10px', borderRadius: 999, background: 'rgba(15,23,42,.75)', border: '1px solid rgba(56,189,248,.5)', color: '#38bdf8', fontSize: 10, fontWeight: 800, letterSpacing: 1}}>● {label}</div>;
