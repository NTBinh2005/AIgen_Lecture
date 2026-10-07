import React from 'react';
import {
  AbsoluteFill,
  Audio,
  Img,
  Loop,
  OffthreadVideo,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';
import {Lottie} from '@remotion/lottie';
import spiralData from '../../public/spiral.json';
import heartbeatData from '../../public/heartbeat.json';
import decorationData from '../../public/decoration.json';
import type {Slide} from '../types';
import {Avatar} from './Avatar';

interface SlideSceneProps {
  slide: Slide;
  isActive: boolean;
  slideIndex: number;
  totalSlides: number;
}

export const SlideScene: React.FC<SlideSceneProps> = ({
  slide,
  isActive,
  slideIndex,
  totalSlides,
}) => {
  const frame = useCurrentFrame();
  const {fps, durationInFrames} = useVideoConfig();
  const fadeOutAt = Math.max(12, durationInFrames - 12);
  const sceneOpacity = interpolate(frame, [0, 10, fadeOutAt, durationInFrames], [0, 1, 1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const titleReadingFrames = Math.min(fps * 2, durationInFrames / 3);
  const pointCount = slide.bulletPoints.length;
  const framesPerPoint = pointCount > 0
    ? Math.max(1, (durationInFrames - titleReadingFrames) / pointCount)
    : durationInFrames;
  const titleY = spring({frame, fps, config: {damping: 16, stiffness: 105}, from: 34, to: 0});
  const titleOpacity = interpolate(frame, [0, 16], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const titleCharacters = Math.floor(interpolate(frame, [0, 38], [0, slide.title.length], {
    extrapolateRight: 'clamp',
  }));
  const bgX = interpolate(Math.sin(frame / 55), [-1, 1], [8, 92]);
  const bgY = interpolate(Math.cos(frame / 65), [-1, 1], [8, 92]);
  const presenterScale = spring({frame, fps, config: {damping: 13, stiffness: 90}, from: 0.78, to: 1});
  const phase = (slide.lessonPhase ?? 'EXPLAIN').toUpperCase();
  const phaseLabels: Record<string, string> = {
    HOOK: 'KHỞI ĐỘNG',
    OBJECTIVE: 'MỤC TIÊU HỌC TẬP',
    EXPLAIN: 'GIẢNG GIẢI',
    EXAMPLE: 'VÍ DỤ VẬN DỤNG',
    CHECK: 'CÙNG SUY NGHĨ',
    SUMMARY: 'TỔNG KẾT',
  };
  const isInteractive = phase === 'CHECK';

  if (slide.sceneVideoUrl || slide.cinematicMode) {
    const subtitleChunks = chunkSubtitle(slide.narrationText);
    const subtitleIndex = Math.min(
      subtitleChunks.length - 1,
      Math.floor((frame / Math.max(1, durationInFrames)) * subtitleChunks.length),
    );
    const headingOpacity = interpolate(frame, [0, 12, fps * 3, fps * 4], [0, 1, 1, 0], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    });

    return (
      <AbsoluteFill style={{opacity: isActive ? sceneOpacity : 0, backgroundColor: '#020617', color: '#fff', fontFamily: 'Inter, Segoe UI, Arial, sans-serif'}}>
        {slide.audioUrl ? <Audio src={slide.audioUrl} volume={1} /> : null}
        {slide.sceneVideoUrl ? (
          <Loop durationInFrames={fps * 8}>
            <OffthreadVideo
              src={slide.sceneVideoUrl}
              muted
              volume={0}
              style={{width: '100%', height: '100%', objectFit: 'cover'}}
            />
          </Loop>
        ) : (
          <LocalCinematicBackground slide={slide} frame={frame} durationInFrames={durationInFrames} />
        )}
        <AbsoluteFill style={{background: 'linear-gradient(180deg, rgba(2,6,23,.42) 0%, transparent 28%, transparent 55%, rgba(2,6,23,.76) 100%)'}} />

        <div style={{position: 'absolute', left: 48, right: 48, top: 34, display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
          <div style={{padding: '8px 14px', borderRadius: 999, background: 'rgba(2,6,23,.58)', border: '1px solid rgba(255,255,255,.28)', fontSize: 14, fontWeight: 850, letterSpacing: 1.1}}>
            EDUMIND • {phaseLabels[phase] ?? 'GIẢNG GIẢI'}
          </div>
          <div style={{padding: '8px 14px', borderRadius: 999, background: 'rgba(2,6,23,.58)', fontSize: 14, fontWeight: 750}}>
            {slideIndex + 1} / {totalSlides}
          </div>
        </div>

        <div style={{position: 'absolute', left: 58, top: 106, maxWidth: 760, opacity: headingOpacity, transform: `translateY(${titleY}px)`}}>
          <h1 style={{fontSize: 48, lineHeight: 1.1, margin: 0, fontWeight: 900, textShadow: '0 4px 24px rgba(0,0,0,.8)'}}>{slide.title}</h1>
        </div>

        <div style={{position: 'absolute', left: 120, right: 120, bottom: 42, display: 'flex', justifyContent: 'center'}}>
          <div style={{maxWidth: 1000, padding: '13px 22px', borderRadius: 14, background: 'rgba(2,6,23,.76)', border: '1px solid rgba(255,255,255,.18)', boxShadow: '0 10px 35px rgba(0,0,0,.38)', fontSize: 25, lineHeight: 1.38, fontWeight: 650, textAlign: 'center', textShadow: '0 2px 8px #000'}}>
            {subtitleChunks[subtitleIndex]}
          </div>
        </div>
      </AbsoluteFill>
    );
  }

  return (
    <AbsoluteFill
      style={{
        opacity: isActive ? sceneOpacity : 0,
        overflow: 'hidden',
        background: 'linear-gradient(135deg, #0f172a, #1e1b4b 42%, #172554 72%, #0c4a6e)',
        backgroundSize: '280% 280%',
        backgroundPosition: `${bgX}% ${bgY}%`,
        color: '#ffffff',
        fontFamily: 'Inter, Segoe UI, Arial, sans-serif',
      }}
    >
      {slide.audioUrl ? <Audio src={slide.audioUrl} volume={1} /> : null}

      <div style={{position: 'absolute', inset: 0, opacity: 0.16, transform: 'scale(1.7)'}}>
        <Lottie animationData={decorationData} loop />
      </div>
      <div style={{position: 'absolute', top: -130, left: -120, width: 430, height: 430, borderRadius: '50%', background: 'rgba(99,102,241,.22)', filter: 'blur(70px)'}} />
      <div style={{position: 'absolute', right: -90, bottom: -120, width: 390, height: 390, borderRadius: '50%', background: 'rgba(14,165,233,.2)', filter: 'blur(65px)'}} />

      <header style={{position: 'absolute', left: 64, right: 64, top: 38, display: 'flex', alignItems: 'center', justifyContent: 'space-between', zIndex: 20}}>
        <div style={{display: 'flex', alignItems: 'center', gap: 12}}>
          <div style={{width: 36, height: 36, borderRadius: 12, display: 'grid', placeItems: 'center', background: 'linear-gradient(135deg,#8b5cf6,#38bdf8)', boxShadow: '0 8px 24px rgba(56,189,248,.3)'}}>
            <span style={{fontSize: 19, fontWeight: 900}}>AI</span>
          </div>
          <div>
            <div style={{fontSize: 17, fontWeight: 800, letterSpacing: 1.6}}>EDUMIND AI TUTOR</div>
            <div style={{fontSize: 12, color: '#bae6fd', marginTop: 2}}>Bài giảng được trình bày bởi trợ giảng số</div>
          </div>
        </div>
        <div style={{padding: '9px 16px', borderRadius: 999, background: 'rgba(15,23,42,.46)', border: '1px solid rgba(255,255,255,.16)', fontSize: 15, fontWeight: 700}}>
          {slideIndex + 1} / {totalSlides}
        </div>
      </header>

      <main style={{position: 'absolute', left: 64, top: 112, width: 790, bottom: 126, zIndex: 10, display: 'flex', flexDirection: 'column', justifyContent: 'center'}}>
        <div style={{opacity: titleOpacity, transform: `translateY(${titleY}px)`, marginBottom: 30}}>
          <div style={{display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 13px', borderRadius: 999, background: 'rgba(129,140,248,.18)', border: '1px solid rgba(165,180,252,.35)', color: '#c7d2fe', fontSize: 13, fontWeight: 800, letterSpacing: 1.2, marginBottom: 16}}>
            {phaseLabels[phase] ?? 'GIẢNG GIẢI'}
          </div>
          <h1 style={{fontSize: 48, lineHeight: 1.12, letterSpacing: -1.2, margin: 0, fontWeight: 850, textShadow: '0 10px 30px rgba(2,6,23,.38)'}}>
            {slide.title.slice(0, titleCharacters)}
          </h1>
          {slide.teachingGoal ? (
            <div style={{marginTop: 14, display: 'flex', alignItems: 'center', gap: 10, color: '#bae6fd', fontSize: 16, fontWeight: 650}}>
              <span style={{width: 8, height: 8, borderRadius: 99, background: '#38bdf8', boxShadow: '0 0 16px #38bdf8'}} />
              Mục tiêu: {slide.teachingGoal}
            </div>
          ) : null}
            </div>
          ) : null}
        </div>

        <div style={{display: 'flex', flexDirection: 'column', gap: 18}}>
          {slide.bulletPoints.slice(0, 5).map((point, index) => {
            const start = titleReadingFrames + index * framesPerPoint;
            const active = frame >= start && frame < start + framesPerPoint;
            const delay = 12 + index * 11;
            const appear = interpolate(frame, [delay, delay + 13], [0, 1], {
              extrapolateLeft: 'clamp',
              extrapolateRight: 'clamp',
            });
            const x = spring({frame: frame - delay, fps, config: {damping: 15, stiffness: 120}, from: -28, to: 0});

            return (
              <div
                key={`${index}-${point}`}
                  display: 'flex',
                  gap: 15,
                  alignItems: 'flex-start',
                  opacity: appear * (active ? 1 : frame >= start ? 0.5 : 0.12),
                  transform: `translateX(${x}px) scale(${active ? 1.025 : 1})`,
                  transformOrigin: 'left center',
                }}
              >
                <div style={{width: 34, height: 34, flex: '0 0 auto', marginTop: 1}}>
                  <Lottie animationData={spiralData} loop playbackRate={active ? 1 : 0.12} />
                </div>
                <p style={{fontSize: 24, lineHeight: 1.42, margin: 0, fontWeight: active ? 650 : 450, color: active ? '#ffffff' : '#dbeafe'}}>
                  {point}
                </p>
              </div>
            );
          })}
        </div>
        {isInteractive && slide.interactionPrompt ? (
          <div style={{marginTop: 28, padding: '18px 22px', borderRadius: 20, background: 'linear-gradient(135deg,rgba(14,165,233,.2),rgba(139,92,246,.2))', border: '1px solid rgba(125,211,252,.4)', boxShadow: '0 18px 45px rgba(2,6,23,.25)'}}>
            <div style={{fontSize: 13, color: '#7dd3fc', fontWeight: 850, letterSpacing: 1.3, marginBottom: 7}}>CÔ MỜI CÁC EM TRẢ LỜI</div>
            <div style={{fontSize: 23, lineHeight: 1.35, fontWeight: 700}}>{slide.interactionPrompt}</div>
          </div>
        ) : null}
      </main>

      <aside
        style={{
          position: 'absolute',
          right: 42,
          top: 106,
          width: 370,
          height: 500,
          zIndex: 15,
          borderRadius: 32,
          overflow: 'hidden',
          background: 'linear-gradient(180deg, rgba(255,255,255,.13), rgba(255,255,255,.05))',
          border: '1px solid rgba(255,255,255,.2)',
          boxShadow: '0 28px 70px rgba(2,6,23,.4)',
          backdropFilter: 'blur(18px)',
        }}
      >
        {slide.imageUrl ? (
          <div style={{position: 'absolute', left: 18, right: 18, top: 18, height: 165, borderRadius: 21, overflow: 'hidden', opacity: 0.88}}>
            <Img
              src={slide.imageUrl}
              style={{width: '100%', height: '100%', objectFit: 'cover', transform: `scale(${interpolate(frame, [0, Math.max(1, durationInFrames)], [1, 1.08])})`}}
            />
            <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(180deg, transparent 45%, rgba(15,23,42,.8))'}} />
          </div>
        ) : slide.imagePrompt ? (
          <div style={{position: 'absolute', left: 18, right: 18, top: 18, height: 165, borderRadius: 21, overflow: 'hidden', background: 'linear-gradient(135deg, rgba(99,102,241,.62), rgba(14,165,233,.38))', border: '1px solid rgba(255,255,255,.14)'}}>
            <div style={{position: 'absolute', width: 150, height: 150, borderRadius: '50%', left: -28, top: -55, background: 'rgba(255,255,255,.12)'}} />
            <div style={{position: 'absolute', width: 115, height: 115, borderRadius: 28, right: -18, bottom: -45, transform: `rotate(${18 + frame / 12}deg)`, background: 'rgba(167,139,250,.23)'}} />
            <div style={{position: 'absolute', inset: 0, display: 'grid', placeItems: 'center'}}>
              <div style={{width: 70, height: 70, borderRadius: 24, display: 'grid', placeItems: 'center', background: 'rgba(15,23,42,.38)', border: '1px solid rgba(255,255,255,.2)', color: '#e0e7ff', fontSize: 27, fontWeight: 900}}>AI</div>
            </div>
          </div>
        ) : null}

        <div style={{position: 'absolute', left: 14, right: 14, bottom: slide.imagePrompt ? -4 : 52, height: 350, transform: `scale(${presenterScale * (isInteractive ? 1.04 : 1)})`, transformOrigin: '50% 100%'}}>
          <Avatar
            audioSrc={slide.audioUrl}
            avatarVideoUrl={slide.avatarVideoUrl}
            teacherAction={slide.teacherAction}
            style={{position: 'absolute', left: 6, bottom: 0}}
          />
        </div>
        <div style={{position: 'absolute', right: 16, bottom: 24, width: 92, height: 46, opacity: 0.82}}>
          <Lottie animationData={heartbeatData} loop />
        </div>
        <div style={{position: 'absolute', left: 18, bottom: 18, padding: '7px 12px', borderRadius: 999, background: slide.audioUrl ? 'rgba(16,185,129,.2)' : 'rgba(245,158,11,.2)', border: `1px solid ${slide.audioUrl ? 'rgba(52,211,153,.45)' : 'rgba(251,191,36,.45)'}`, color: slide.audioUrl ? '#a7f3d0' : '#fde68a', fontSize: 12, fontWeight: 800, letterSpacing: 0.7}}>
          {slide.audioUrl ? `● ${(slide.teacherAction ?? 'EXPLAIN').toUpperCase()}` : '● CHẾ ĐỘ KHÔNG GIỌNG ĐỌC'}
        </div>
      </aside>

      <footer style={{position: 'absolute', left: 64, right: 420, bottom: 30, minHeight: 72, zIndex: 20, display: 'flex', alignItems: 'center', gap: 14, padding: '13px 18px', borderRadius: 18, background: 'rgba(2,6,23,.58)', border: '1px solid rgba(148,163,184,.22)', boxShadow: '0 16px 40px rgba(2,6,23,.3)'}}>
        <div style={{width: 8, alignSelf: 'stretch', borderRadius: 99, background: 'linear-gradient(#a78bfa,#38bdf8)'}} />
        <p style={{margin: 0, fontSize: 18, lineHeight: 1.4, color: '#e2e8f0', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden'}}>
          {slide.narrationText}
        </p>
      </footer>
    </AbsoluteFill>
  );
};

function chunkSubtitle(text: string): string[] {
  const words = text.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return [''];
  const chunks: string[] = [];
  let current: string[] = [];
  for (const word of words) {
    current.push(word);
    if (current.length >= 11 || current.join(' ').length >= 68) {
      chunks.push(current.join(' '));
      current = [];
    }
  }
  if (current.length > 0) chunks.push(current.join(' '));
  return chunks;
}

const teacherArtworkForAction = (action?: string | null): string => {
  const normalized = (action ?? 'EXPLAIN').toUpperCase();
  if (normalized === 'QUESTION' || normalized === 'WELCOME') return 'teacher/avatar_teacher_question.png';
  if (normalized === 'POINT') return 'teacher/avatar_teacher_point.png';
  return 'teacher/avatar_teacher_explain.png';
};

const LocalCinematicBackground: React.FC<{
  slide: Slide;
  frame: number;
  durationInFrames: number;
}> = ({slide, frame, durationInFrames}) => {
  const travel = interpolate(frame, [0, Math.max(1, durationInFrames)], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const cameraX = Math.sin(frame / 38) * 18;
  const cameraY = Math.cos(frame / 47) * 10;
  const glowX = 48 + Math.sin(frame / 42) * 18;
  const concepts = slide.bulletPoints.slice(0, 3);

  return (
    <AbsoluteFill style={{overflow: 'hidden', background: 'linear-gradient(145deg,#07152f 0%,#172554 42%,#312e81 72%,#0e7490 100%)'}}>
      {slide.imageUrl ? (
        <Img
          src={slide.imageUrl}
          style={{
            position: 'absolute',
            inset: -45,
            width: 'calc(100% + 90px)',
            height: 'calc(100% + 90px)',
            objectFit: 'cover',
            opacity: 0.72,
            filter: 'saturate(1.2) contrast(1.08)',
            transform: `scale(${1.06 + travel * 0.08}) translate(${cameraX * -0.18}px, ${cameraY * -0.18}px)`,
          }}
        />
      ) : null}

      <div style={{position: 'absolute', inset: 0, background: `radial-gradient(circle at ${glowX}% 38%,rgba(56,189,248,.42),transparent 34%), radial-gradient(circle at 24% 72%,rgba(168,85,247,.3),transparent 38%)`}} />
      <div style={{position: 'absolute', left: -180, right: -180, bottom: -360, height: 560, opacity: 0.42, transform: `perspective(700px) rotateX(66deg) translateY(${cameraY}px)`, transformOrigin: 'center top', backgroundImage: 'linear-gradient(rgba(125,211,252,.28) 1px,transparent 1px),linear-gradient(90deg,rgba(125,211,252,.28) 1px,transparent 1px)', backgroundSize: '58px 58px'}} />

      {[0, 1, 2].map((ring) => (
        <div
          key={ring}
          style={{
            position: 'absolute',
            left: 400 - ring * 48,
            top: 170 - ring * 48,
            width: 330 + ring * 96,
            height: 330 + ring * 96,
            borderRadius: '50%',
            border: `${3 - ring * 0.5}px solid rgba(${ring === 1 ? '167,139,250' : '56,189,248'},${0.48 - ring * 0.09})`,
            boxShadow: '0 0 34px rgba(56,189,248,.18), inset 0 0 30px rgba(139,92,246,.12)',
            transform: `perspective(900px) rotateX(${64 - ring * 12}deg) rotateZ(${frame * (ring % 2 ? -0.45 : 0.34) + ring * 34}deg) translateZ(${ring * 16}px)`,
          }}
        />
      ))}

      <div style={{position: 'absolute', left: 505 + cameraX, top: 258 + cameraY, width: 126, height: 126, borderRadius: 40, display: 'grid', placeItems: 'center', background: 'linear-gradient(145deg,rgba(96,165,250,.95),rgba(124,58,237,.95))', border: '2px solid rgba(255,255,255,.46)', boxShadow: '0 30px 75px rgba(14,165,233,.42)', transform: `perspective(800px) rotateY(${Math.sin(frame / 28) * 18}deg) rotateX(${Math.cos(frame / 34) * 10}deg) translateZ(70px)`}}>
        <span style={{fontSize: 47, fontWeight: 950, textShadow: '0 8px 22px rgba(0,0,0,.35)'}}>AI</span>
      </div>

      {concepts.map((concept, index) => {
        const angle = frame / (34 + index * 4) + index * 2.1;
        const x = 535 + Math.cos(angle) * (250 + index * 22);
        const y = 320 + Math.sin(angle) * (125 + index * 16);
        return (
          <div key={`${index}-${concept}`} style={{position: 'absolute', left: x, top: y, maxWidth: 260, padding: '11px 16px', borderRadius: 16, background: 'linear-gradient(135deg,rgba(15,23,42,.82),rgba(49,46,129,.72))', border: '1px solid rgba(186,230,253,.42)', boxShadow: '0 18px 42px rgba(2,6,23,.38)', backdropFilter: 'blur(12px)', fontSize: 16, fontWeight: 720, transform: `perspective(700px) translateZ(${55 + index * 18}px) rotateY(${Math.sin(frame / 35 + index) * 10}deg)`}}>
            {concept}
          </div>
        );
      })}

      {slide.avatarVideoUrl ? (
        <div style={{position: 'absolute', right: 22, bottom: 18, width: 390, height: 500, overflow: 'hidden', borderRadius: 34, border: '1px solid rgba(125,211,252,.44)', boxShadow: '-22px 28px 54px rgba(2,6,23,.55),0 0 34px rgba(56,189,248,.18)', background: 'rgba(8,47,73,.62)', transform: `perspective(900px) rotateY(${-5 + Math.sin(frame / 44) * 2}deg) translate(${cameraX * 0.18}px,${cameraY * 0.12}px)`}}>
          <OffthreadVideo
            src={slide.avatarVideoUrl}
            muted
            volume={0}
            style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'center center'}}
          />
          <div style={{position: 'absolute', left: 14, bottom: 14, padding: '6px 11px', borderRadius: 999, background: 'rgba(2,6,23,.72)', border: '1px solid rgba(52,211,153,.46)', color: '#a7f3d0', fontSize: 11, fontWeight: 850, letterSpacing: 0.9}}>● GIÁO VIÊN ĐANG GIẢNG</div>
        </div>
      ) : (
        <Img
          src={staticFile(teacherArtworkForAction(slide.teacherAction))}
          style={{position: 'absolute', right: -15 + cameraX * 0.45, bottom: -34 + Math.sin(frame / 22) * 5, width: 430, height: 590, objectFit: 'contain', objectPosition: 'center bottom', filter: 'drop-shadow(-25px 30px 30px rgba(2,6,23,.58))', transform: `perspective(900px) rotateY(${-8 + Math.sin(frame / 44) * 3}deg) scale(${1 + Math.sin(frame / 31) * 0.008})`, transformOrigin: '50% 100%'}}
        />
      )}

      <div style={{position: 'absolute', inset: 0, background: 'linear-gradient(90deg,rgba(2,6,23,.1),transparent 52%,rgba(2,6,23,.18))', boxShadow: 'inset 0 0 100px rgba(2,6,23,.48)'}} />
    </AbsoluteFill>
  );
};
