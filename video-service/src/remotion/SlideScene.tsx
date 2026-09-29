import React from 'react';
import {
  AbsoluteFill,
  Audio,
  Img,
  interpolate,
  spring,
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
            NỘI DUNG BÀI GIẢNG
          </div>
          <h1 style={{fontSize: 48, lineHeight: 1.12, letterSpacing: -1.2, margin: 0, fontWeight: 850, textShadow: '0 10px 30px rgba(2,6,23,.38)'}}>
            {slide.title.slice(0, titleCharacters)}
          </h1>
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
                style={{
                  display: 'flex',
                  gap: 15,
                  alignItems: 'flex-start',
                  opacity: appear * (active ? 1 : 0.55),
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
      </main>

      <aside
        style={{
          position: 'absolute',
          right: 42,
          top: 106,
          width: 350,
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

        <div style={{position: 'absolute', left: 4, right: 4, bottom: slide.imagePrompt ? -48 : 5, height: 410, transform: `scale(${presenterScale})`, transformOrigin: '50% 100%'}}>
          <Avatar audioSrc={slide.audioUrl} style={{position: 'absolute', left: 6, bottom: 0}} />
        </div>
        <div style={{position: 'absolute', right: 16, bottom: 24, width: 92, height: 46, opacity: 0.82}}>
          <Lottie animationData={heartbeatData} loop />
        </div>
        <div style={{position: 'absolute', left: 18, bottom: 18, padding: '7px 12px', borderRadius: 999, background: slide.audioUrl ? 'rgba(16,185,129,.2)' : 'rgba(245,158,11,.2)', border: `1px solid ${slide.audioUrl ? 'rgba(52,211,153,.45)' : 'rgba(251,191,36,.45)'}`, color: slide.audioUrl ? '#a7f3d0' : '#fde68a', fontSize: 12, fontWeight: 800, letterSpacing: 0.7}}>
          {slide.audioUrl ? '● ĐANG THUYẾT TRÌNH' : '● CHẾ ĐỘ KHÔNG GIỌNG ĐỌC'}
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
