import React from 'react';
import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
  Audio,
  Img,
  staticFile,
} from 'remotion';
import { Lottie } from '@remotion/lottie';
import spiralData from '../../public/spiral.json';
import heartbeatData from '../../public/heartbeat.json';
import type { Slide } from '../types';
import { Avatar } from './Avatar';

interface SlideCompositionProps {
  slides: Slide[];
  /** Thời lượng mỗi slide (ms) — tính từ TTS durationMs */
  slideDurationsMs: number[];
}

/**
 * SlideCompositionProps.slides render từng slide tuần tự với animation:
 * - Background gradient tím-xanh
 * - Title fade-in + slide up
 * - Bullet points lần lượt xuất hiện (stagger 15 frames mỗi point)
 */
export const SlideScene: React.FC<{ slide: Slide; isActive: boolean }> = ({
  slide,
  isActive,
}) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();

  // Highlight logic calculations
  const titleReadingFrames = fps * 2; // Giả sử đọc xong title mất khoảng 2 giây
  const nPoints = slide.bulletPoints.length;
  const remainingFrames = Math.max(0, durationInFrames - titleReadingFrames);
  const framesPerPoint = nPoints > 0 ? remainingFrames / nPoints : remainingFrames;

  const titleOpacity = interpolate(frame, [0, 20], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  const titleY = spring({
    frame,
    fps,
    config: { damping: 14, stiffness: 100, mass: 1 },
    from: 40,
    to: 0,
  });

  // Background floating animations
  const floatY1 = Math.sin(frame / 30) * 20;
  const floatX1 = Math.cos(frame / 40) * 15;
  const floatY2 = Math.cos(frame / 25) * 25;
  const floatX2 = Math.sin(frame / 35) * 20;

  // Dynamic background positions using sine waves for smooth loops
  const bgX = interpolate(Math.sin(frame / 60), [-1, 1], [0, 100]);
  const bgY = interpolate(Math.cos(frame / 60), [-1, 1], [0, 100]);

  // Typewriter effect for title
  const charsToShow = Math.floor(
    interpolate(frame, [0, 45], [0, slide.title.length], {
      extrapolateRight: 'clamp',
    })
  );
  const titleToDisplay = slide.title.slice(0, charsToShow);

  return (
    <AbsoluteFill
      style={{
        background: 'linear-gradient(135deg, #1e1b4b, #312e81, #1e3a5f, #2e1065)',
        backgroundSize: '400% 400%',
        backgroundPosition: `${bgX}% ${bgY}%`,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        padding: '80px 120px',
        fontFamily: '"Inter", "Segoe UI", sans-serif',
      }}
    >
      {/* Audio thực tế từ TTS (nếu có) */}
      {slide.audioUrl && <Audio src={slide.audioUrl} />}

      {/* Background decorative circles */}
      <div
        style={{
          position: 'absolute',
          top: -100 + floatY1,
          right: -100 + floatX1,
          width: 400,
          height: 400,
          borderRadius: '50%',
          background: 'rgba(139, 92, 246, 0.15)',
          filter: 'blur(60px)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: -80 + floatY2,
          left: -80 + floatX2,
          width: 300,
          height: 300,
          borderRadius: '50%',
          background: 'rgba(59, 130, 246, 0.12)',
          filter: 'blur(50px)',
        }}
      />



      {/* Logo / Watermark cực kỳ xịn xò (Glassmorphism) */}
      <div
        style={{
          position: 'absolute',
          top: 50,
          right: 50,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '12px 24px',
          background: 'rgba(255, 255, 255, 0.1)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          borderRadius: 100,
          border: '1px solid rgba(255, 255, 255, 0.2)',
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
          color: 'white',
          zIndex: 50,
        }}
      >
        <div style={{
          width: 24,
          height: 24,
          background: 'linear-gradient(135deg, #a78bfa, #60a5fa)',
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 0 15px rgba(167, 139, 250, 0.5)'
        }}>
          <div style={{ width: 10, height: 10, backgroundColor: 'white', borderRadius: '50%' }} />
        </div>
        <span style={{
          fontSize: 18,
          fontWeight: 700,
          letterSpacing: 1.5,
          background: 'linear-gradient(90deg, #ffffff, #e2e8f0)',
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent',
        }}>
          AI LECTURE
        </span>
      </div>

      {/* Content Container (Split Layout if image exists) */}
      <div style={{ display: 'flex', flexDirection: 'row', gap: 60, flex: 1, zIndex: 10 }}>
        {/* Left Column (Text) */}
        <div style={{ 
          flex: slide.imagePrompt ? 0.6 : 1, 
          display: 'flex', 
          flexDirection: 'column', 
          justifyContent: 'center',
          paddingBottom: 120, // Chừa khoảng trống bên dưới để Avatar không đè lên chữ
        }}>
          {/* Title */}
          <div
            style={{
              opacity: titleOpacity,
              transform: `translateY(${titleY}px)`,
              marginBottom: 48,
            }}
          >
            <div
              style={{
                display: 'inline-block',
                background: 'linear-gradient(90deg, #a78bfa, #60a5fa)',
                borderRadius: 8,
                padding: '6px 20px',
                marginBottom: 20,
                fontSize: 16,
                color: 'white',
                fontWeight: 600,
                letterSpacing: 1,
              }}
            >
              BÀI GIẢNG
            </div>
              <h1
                style={{
                  fontSize: slide.imagePrompt ? 56 : 64, // Nhỏ lại chút nếu chia 2 cột
                  fontWeight: 800,
                  color: 'white',
                  margin: 0,
                  lineHeight: 1.15,
                  letterSpacing: -1,
                  // Giữ chiều cao cố định để không bị giật khi gõ chữ
                  minHeight: slide.imagePrompt ? 130 : 150, 
                }}
              >
                {titleToDisplay}
              </h1>
          </div>

          {/* Bullet Points */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
            {slide.bulletPoints.map((point, i) => {
              // Timing for reading highlight
              const pointStartFrame = titleReadingFrames + i * framesPerPoint;
              const pointEndFrame = pointStartFrame + framesPerPoint;
              const isActivePoint = frame >= pointStartFrame && frame < pointEndFrame;

              // Appearance stagger
              const bulletDelay = 15 + i * 15;
              const appearOpacity = interpolate(frame, [bulletDelay, bulletDelay + 15], [0, 1], {
                extrapolateLeft: 'clamp',
                extrapolateRight: 'clamp',
              });
              
              // Target opacity based on highlight
              const targetOpacity = isActivePoint ? 1 : 0.3;
              const finalOpacity = appearOpacity * targetOpacity;

              const bulletX = spring({
                frame: frame - bulletDelay,
                fps,
                config: { damping: 12, stiffness: 120 },
                from: -40,
                to: 0,
              });
              const bulletScale = spring({
                frame: frame - bulletDelay,
                fps,
                config: { damping: 12, stiffness: 120 },
                from: 0.8,
                to: isActivePoint ? 1.1 : 1, // To lên một chút khi đang đọc
              });

              return (
                <div
                  key={i}
                  style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 20,
                    opacity: finalOpacity,
                    transform: `translateX(${bulletX}px) scale(${bulletScale})`,
                    transition: 'opacity 0.3s ease',
                  }}
                >
                  <div
                    style={{
                      width: 40,
                      height: 40,
                      flexShrink: 0,
                      marginTop: -4,
                    }}
                  >
                    {/* Lottie không cho phép playbackRate = 0, nên dùng 0.0001 để gần như đóng băng */}
                    <Lottie 
                      animationData={spiralData} 
                      loop 
                      playbackRate={isActivePoint ? 1 : 0.0001} 
                    />
                  </div>
                  <p
                    style={{
                      fontSize: slide.imagePrompt ? 24 : 28,
                      color: 'rgba(255,255,255,0.9)',
                      margin: 0,
                      lineHeight: 1.5,
                      fontWeight: 400,
                    }}
                  >
                    {point}
                  </p>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right Column (Image) */}
        {slide.imagePrompt && (
          <div style={{ flex: 0.4, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
            <div
              style={{
                width: '100%',
                aspectRatio: '1/1',
                borderRadius: 24,
                overflow: 'hidden',
                boxShadow: '0 20px 50px rgba(0,0,0,0.5)',
                opacity: titleOpacity,
                transform: `translateY(${titleY}px)`, // Dùng chung animation với title
              }}
            >
              <Img
                src={`https://image.pollinations.ai/prompt/${encodeURIComponent(slide.imagePrompt)}?width=600&height=600&nologo=true`}
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  // Hiệu ứng Ken Burns (Zoom chậm liên tục)
                  transform: `scale(${interpolate(frame, [0, 500], [1, 1.1])})`,
                }}
              />
            </div>
          </div>
        )}
      </div>

      {/* Tích hợp AI Avatar và Heartbeat Lottie */}
      {slide.audioUrl && (
        <>
          <Avatar audioSrc={slide.audioUrl} style={{ position: 'absolute', bottom: 50, left: 60, width: 110, height: 110 }} />
          <div style={{ position: 'absolute', bottom: 85, left: 160, width: 60, height: 60 }}>
            <Lottie animationData={heartbeatData} loop />
          </div>
        </>
      )}
    </AbsoluteFill>
  );
};

export type { SlideCompositionProps };
