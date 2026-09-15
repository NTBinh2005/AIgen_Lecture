import React from 'react';
import { useCurrentFrame, useVideoConfig } from 'remotion';
import { useAudioData, visualizeAudio } from '@remotion/media-utils';

export const Avatar: React.FC<{ audioSrc: string; style?: React.CSSProperties }> = ({ audioSrc, style }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const audioData = useAudioData(audioSrc);

  if (!audioData) return null;

  const frequencies = visualizeAudio({
    fps,
    frame,
    audioData,
    numberOfSamples: 16,
  });

  const volume = frequencies.reduce((a, b) => a + b, 0) / frequencies.length;
  // Độ mở của miệng (0 đến 1)
  const mouthOpenness = Math.max(0, Math.min(1, volume * 6));
  
  // Chớp mắt mỗi 4 giây
  const isBlinking = frame % 120 < 6;
  // Hiệu ứng bay bổng
  const hoverY = Math.sin(frame / 20) * 8;
  // Hào quang phát sáng theo giọng nói
  const glowOpacity = 0.3 + volume * 0.7;

  return (
    <div style={{
      width: 130,
      height: 130,
      transform: `translateY(${hoverY}px)`,
      zIndex: 100,
      ...style,
    }}>
      {/* Vẽ Robot bằng SVG (Sắc nét vô hạn, siêu đẹp) */}
      <svg viewBox="0 0 200 200" width="100%" height="100%" style={{ filter: 'drop-shadow(0 15px 25px rgba(0,0,0,0.4))' }}>
        <defs>
          {/* Chế độ màu Gradient kim loại và Kính */}
          <linearGradient id="bodyGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#ffffff" />
            <stop offset="100%" stopColor="#94a3b8" />
          </linearGradient>
          <linearGradient id="visorGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#0f172a" />
            <stop offset="100%" stopColor="#1e293b" />
          </linearGradient>
          <radialGradient id="glowGrad" cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor="rgba(56, 189, 248, 1)" />
            <stop offset="100%" stopColor="rgba(56, 189, 248, 0)" />
          </radialGradient>
        </defs>

        {/* Ánh sáng Hào quang sau lưng robot (nhấp nháy theo giọng nói) */}
        <circle cx="100" cy="100" r="90" fill="url(#glowGrad)" opacity={glowOpacity * 0.6} />

        {/* Ăng-ten thu sóng (phát đỏ khi nói) */}
        <line x1="100" y1="30" x2="100" y2="10" stroke="#64748b" strokeWidth="6" strokeLinecap="round" />
        <circle cx="100" cy="10" r="7" fill={volume > 0.1 ? "#ef4444" : "#64748b"} style={{ transition: 'fill 0.1s' }} />

        {/* Ống nghe 2 bên */}
        <rect x="25" y="75" width="15" height="45" rx="5" fill="#64748b" />
        <rect x="160" y="75" width="15" height="45" rx="5" fill="#64748b" />

        {/* Đầu Robot */}
        <rect x="35" y="30" width="130" height="120" rx="45" fill="url(#bodyGrad)" stroke="#e2e8f0" strokeWidth="2" />
        
        {/* Màn hình LED (Visor - Kính chắn gió) */}
        <rect x="50" y="55" width="100" height="70" rx="25" fill="url(#visorGrad)" stroke="#334155" strokeWidth="3" />
        
        {/* Vết bóng kính lấp lánh (Glass reflection) */}
        <path d="M 60 65 Q 100 55 140 65 A 20 20 0 0 1 145 75 Q 100 65 55 75 A 20 20 0 0 1 60 65" fill="rgba(255,255,255,0.15)" />

        {/* Đôi mắt LED Xanh Neon */}
        <g style={{ transformOrigin: '100px 75px', transform: isBlinking ? 'scaleY(0.1)' : 'scaleY(1)', transition: 'transform 0.05s' }}>
          <circle cx="80" cy="75" r="9" fill="#38bdf8" filter="drop-shadow(0 0 6px #38bdf8)" />
          <circle cx="120" cy="75" r="9" fill="#38bdf8" filter="drop-shadow(0 0 6px #38bdf8)" />
        </g>

        {/* Miệng nhấp nháy theo Sóng âm (Đường cong mở rộng ra) */}
        <g style={{ transformOrigin: '100px 100px' }}>
          <path 
            d={`M 80 100 Q 100 ${100 + mouthOpenness * 20} 120 100`} 
            stroke="#38bdf8" 
            strokeWidth="5" 
            strokeLinecap="round" 
            fill="none" 
            filter="drop-shadow(0 0 4px #38bdf8)"
          />
        </g>

        {/* Cổ */}
        <path d="M 85 150 L 115 150 L 125 170 L 75 170 Z" fill="#64748b" />
        {/* Vai & Ngực */}
        <path d="M 50 170 Q 100 160 150 170 L 170 200 L 30 200 Z" fill="url(#bodyGrad)" />
        
        {/* Lõi năng lượng trước ngực (Đập theo nhịp nói) */}
        <circle cx="100" cy="185" r="12" fill="#38bdf8" filter={`drop-shadow(0 0 ${5 + volume * 15}px #38bdf8)`} />
      </svg>
    </div>
  );
};
